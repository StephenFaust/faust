package com.faust.agent.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.runtime.sandbox.manager.model.container.ContainerModel;
import io.agentscope.runtime.sandbox.manager.model.container.SandboxKey;
import io.agentscope.runtime.sandbox.manager.utils.SandboxMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 Redis 的沙箱注册表实现，替换框架默认的 {@code InMemorySandboxMap}。
 *
 * <p>内存版把映射放在进程内的两个 {@code ConcurrentHashMap} 里，多实例部署时每个实例
 * 只能看到自己创建的容器：A 实例建好的沙箱，B 实例既查不到也复用不了（
 * {@code SandboxService#createSandbox} 的 containSandbox 前置判断永远 miss），
 * 实例重启后注册关系还会整体丢失——容器仍在跑，但没有任何实例认识它。</p>
 *
 * <p>存储结构（两个 Hash，<b>不分片、不加 TTL</b>，生命周期完全跟随显式的 add/remove）：</p>
 * <ul>
 *   <li>{@code faust:sandbox:index}：sandboxKey -> containerId，对应内存版的 keyIdMap</li>
 *   <li>{@code faust:sandbox:container}：containerId -> 记录 JSON，对应内存版的 idContainerMap</li>
 * </ul>
 *
 * <p>containerId 对应的记录里回存了 sandboxKey，因此「按 containerId 删除」能直接拿到
 * 反向索引字段，不必像内存版那样遍历全部 value 做 {@code removeIf}。</p>
 *
 * <p>注意：这里不吞异常。Redis 不可用时直接抛出，让上层沙箱创建失败——
 * 静默降级只会让同一 (user, session, type) 被重复建出多个容器，问题更难查。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSandboxMap implements SandboxMap {

	/**
	 * sandboxKey -> containerId
	 */
	private static final String INDEX_KEY = "faust:sandbox:index";

	/**
	 * containerId -> 沙箱记录 JSON
	 */
	private static final String CONTAINER_KEY = "faust:sandbox:container";

	/**
	 * sandboxKey 三段拼接分隔符；各段内的 '%' 与 '|' 会被转义，避免不同 key 拼出同一个字符串。
	 */
	private static final char SEPARATOR = '|';

	private static final String NULL_PART = "%00";

	private static final ObjectMapper MAPPER = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private final RedissonClient redissonClient;

	@Override
	public void addSandbox(SandboxKey sandboxKey, ContainerModel containerModel) {
		if (sandboxKey == null || containerModel == null) {
			return;
		}
		String containerId = containerModel.getContainerId();
		if (isBlank(containerId)) {
			return;
		}
		String index = encode(sandboxKey);
		// 先写容器记录再写索引：索引一旦可查，指向的记录必定已存在
		containers().put(containerId, toJson(new SandboxRecord(index, containerModel)));
		indexes().put(index, containerId);
		LOGGER.debug("Redis sandbox added, sandboxKey={}, containerId={}", sandboxKey, containerId);
	}

	@Override
	public ContainerModel getSandbox(SandboxKey sandboxKey) {
		if (sandboxKey == null) {
			return null;
		}
		String containerId = indexes().get(encode(sandboxKey));
		if (isBlank(containerId)) {
			return null;
		}
		return getSandbox(containerId);
	}

	@Override
	public boolean removeSandbox(SandboxKey sandboxKey) {
		if (sandboxKey == null) {
			return false;
		}
		String containerId = indexes().remove(encode(sandboxKey));
		if (!isBlank(containerId)) {
			containers().remove(containerId);
		}
		LOGGER.debug("Redis sandbox removed by key, sandboxKey={}, containerId={}", sandboxKey, containerId);
		return true;
	}

	@Override
	public ContainerModel getSandbox(String containerId) {
		if (isBlank(containerId)) {
			return null;
		}
		SandboxRecord record = readRecord(containerId);
		return record == null ? null : record.container();
	}

	@Override
	public void removeSandbox(String containerId) {
		if (isBlank(containerId)) {
			return;
		}
		SandboxRecord record = readRecord(containerId);
		containers().remove(containerId);
		if (record != null && !isBlank(record.sandboxKey())) {
			indexes().remove(record.sandboxKey());
		}
		LOGGER.debug("Redis sandbox removed by containerId, containerId={}", containerId);
	}

	@Override
	public Map<String, ContainerModel> getAllSandboxes() {
		Map<String, String> raw = containers().readAllMap();
		if (raw.isEmpty()) {
			return Map.of();
		}
		Map<String, ContainerModel> result = new LinkedHashMap<>(raw.size());
		raw.forEach((containerId, json) -> {
			SandboxRecord record = fromJson(containerId, json);
			if (record != null && record.container() != null) {
				result.put(containerId, record.container());
			}
		});
		return result;
	}

	@Override
	public boolean containSandbox(SandboxKey sandboxKey) {
		if (sandboxKey == null) {
			return false;
		}
		return indexes().containsKey(encode(sandboxKey));
	}

	@Override
	public boolean containSandbox(String containerId) {
		if (isBlank(containerId)) {
			return false;
		}
		return containers().containsKey(containerId);
	}

	private RMap<String, String> indexes() {
		return redissonClient.getMap(INDEX_KEY, StringCodec.INSTANCE);
	}

	private RMap<String, String> containers() {
		return redissonClient.getMap(CONTAINER_KEY, StringCodec.INSTANCE);
	}

	private SandboxRecord readRecord(String containerId) {
		return fromJson(containerId, containers().get(containerId));
	}

	private String toJson(SandboxRecord record) {
		try {
			return MAPPER.writeValueAsString(record);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(
				"Serialize sandbox record failed, containerId=" + record.container().getContainerId(), e);
		}
	}

	private SandboxRecord fromJson(String containerId, String json) {
		if (isBlank(json)) {
			return null;
		}
		try {
			return MAPPER.readValue(json, SandboxRecord.class);
		} catch (JsonProcessingException e) {
			// 单条脏数据不应该让 getAllSandboxes 整体失败，跳过并留痕
			LOGGER.warn("Deserialize sandbox record failed, skip it. containerId={}", containerId, e);
			return null;
		}
	}

	/**
	 * sandboxKey 由 (userID, sessionID, sandboxType) 三段组成，任何一段为空都写成 {@link #NULL_PART}，
	 * 段内的 '%' 与 '|' 做百分号转义，保证「不同三元组」不会拼出「同一个字符串」。
	 */
	private static String encode(SandboxKey sandboxKey) {
		return escape(sandboxKey.userID()) + SEPARATOR
			+ escape(sandboxKey.sessionID()) + SEPARATOR
			+ escape(sandboxKey.sandboxType());
	}

	private static String escape(String part) {
		if (part == null) {
			return NULL_PART;
		}
		return part.replace("%", "%25").replace("|", "%7C");
	}

	private static boolean isBlank(String value) {
		return value == null || value.isEmpty();
	}

	/**
	 * 存储在 Redis 里的整条记录：索引串 + 容器信息。
	 */
	private record SandboxRecord(String sandboxKey, ContainerModel container) {
	}
}

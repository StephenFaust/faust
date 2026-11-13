package com.faust.support.lock;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 分布式锁 key 规范：{@code faust:lock:{type}:{id}}。
 *
 * <p>约定：</p>
 * <ol>
 *   <li>资源 id 全局唯一，key 里<b>不带 workspaceId</b>，否则同一资源会出现两把锁；</li>
 *   <li>一个业务操作涉及多个资源时，用 {@link #merge(List[])} 合成一组，
 *       由 {@link DistributedLockTemplate} 统一排序后加锁，避免 A/B 与 B/A 相互等待；</li>
 *   <li>id 为空时返回空列表，合成时被忽略（例如非 DB 工具没有数据源）。</li>
 * </ol>
 */
public final class LockKeys {

	private static final String PREFIX = "faust:lock:";
	private static final String AGENT = "agent:";
	private static final String TOOL = "tool:";
	private static final String SKILL = "skill:";
	private static final String DATA_SOURCE = "datasource:";
	private static final String SPACE = "space:";

	private LockKeys() {
	}

	public static List<String> agent(Long agentId) {
		return single(AGENT, agentId);
	}

	public static List<String> tool(Long toolId) {
		return single(TOOL, toolId);
	}

	public static List<String> tools(Collection<Long> toolIds) {
		return many(TOOL, toolIds);
	}

	public static List<String> skill(Long skillId) {
		return single(SKILL, skillId);
	}

	public static List<String> skills(Collection<Long> skillIds) {
		return many(SKILL, skillIds);
	}

	public static List<String> dataSource(Long dataSourceId) {
		return single(DATA_SOURCE, dataSourceId);
	}

	public static List<String> space(Long spaceId) {
		return single(SPACE, spaceId);
	}

	/**
	 * 合并多组 key，自动忽略 null / 空串 / 空集合。
	 */
	@SafeVarargs
	public static List<String> merge(List<String>... groups) {
		if (groups == null || groups.length == 0) {
			return Collections.emptyList();
		}
		List<String> keys = new ArrayList<>();
		for (List<String> group : groups) {
			if (CollectionUtils.isEmpty(group)) {
				continue;
			}
			group.stream().filter(StringUtils::hasText).forEach(keys::add);
		}
		return keys;
	}

	/**
	 * 加统一前缀，交给 Redisson 时使用
	 */
	public static String withPrefix(String key) {
		return PREFIX + key;
	}

	private static List<String> single(String type, Long id) {
		if (id == null) {
			return Collections.emptyList();
		}
		return List.of(type + id);
	}

	private static List<String> many(String type, Collection<Long> ids) {
		if (CollectionUtils.isEmpty(ids)) {
			return Collections.emptyList();
		}
		return ids.stream()
			.filter(Objects::nonNull)
			.map(id -> type + id)
			.toList();
	}
}

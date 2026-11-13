package com.faust.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.constant.DbConstants;
import com.faust.entity.AgentInfo;
import com.faust.entity.AgentModelConfig;
import com.faust.entity.AgentSystemPrompt;
import com.faust.mapper.AgentInfoMapper;
import com.faust.repository.AgentInfoRepository;
import com.faust.repository.AgentModelConfigRepository;
import com.faust.repository.AgentSystemPromptRepository;
import com.faust.enums.AgentStatusEnum;
import com.faust.enums.ErrorMessageEnum;
import com.faust.exception.AgentBusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentInfoRepositoryImpl extends ServiceImpl<AgentInfoMapper, AgentInfo> implements AgentInfoRepository {

	@Resource
	private AgentModelConfigRepository agentModelConfigService;

	@Resource
	private AgentSystemPromptRepository agentSystemPromptService;


	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long saveAgent(AgentInfo agentInfo, Long ucId) {
		if (lambdaQuery()
			.eq(AgentInfo::getAgentCode, agentInfo.getAgentCode())
			.eq(AgentInfo::getVersion, agentInfo.getVersion())
			.count() > 0) {
			throw new AgentBusinessException(ErrorMessageEnum.AGENT_VERSION_DUPLICATE.getErrorCode(),
				ErrorMessageEnum.AGENT_VERSION_DUPLICATE.getErrorMsg());
		}
		agentInfo.setStatus(AgentStatusEnum.UNPUBLISHED.getValue());
		agentInfo.setCreateBy(ucId);
		agentInfo.setUpdateBy(ucId);
		save(agentInfo);

		initModelConfig(agentInfo.getId(), ucId);
		initSystemPrompt(agentInfo.getId());
		return agentInfo.getId();
	}


	@Override
	public List<AgentInfo> getPublishedAgents() {
		LambdaQueryWrapper<AgentInfo> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(AgentInfo::getStatus, AgentStatusEnum.PUBLISHED.getValue());
		return list(wrapper);
	}

	@Override
	public AgentInfo getPublishedAgentByCodeAndVersion(String agentCode, String version) {
		LambdaQueryWrapper<AgentInfo> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(AgentInfo::getStatus, AgentStatusEnum.PUBLISHED.getValue());
		wrapper.eq(AgentInfo::getAgentCode, agentCode);
		wrapper.eq(AgentInfo::getVersion, version);
		wrapper.last(DbConstants.LIMIT_ONE);
		return getOne(wrapper);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateAgent(AgentInfo agentInfo, Long ucId, String expectedVersion) {
		if (agentInfo.getId() == null) {
			throw new AgentBusinessException(ErrorMessageEnum.PARAMETER_ERROR.getErrorCode(),
				"Agent ID不能为空");
		}
		agentInfo.setUpdateBy(ucId);
		// 版本 CAS 兜底：expected 为调用方在锁内读到的行版本。
		// 编辑不再要求升级版本，同版本改写会正常命中；命中 0 行说明行已被删除或版本被绕过锁改动。
		LambdaUpdateWrapper<AgentInfo> wrapper = Wrappers.lambdaUpdate();
		wrapper.eq(AgentInfo::getId, agentInfo.getId())
			.eq(StringUtils.isNotBlank(expectedVersion), AgentInfo::getVersion, expectedVersion);
		if (baseMapper.update(agentInfo, wrapper) == 0) {
			throw new AgentBusinessException(ErrorMessageEnum.CONCURRENT_MODIFIED.getErrorCode(),
				ErrorMessageEnum.CONCURRENT_MODIFIED.getErrorMsg());
		}
	}

	@Override
	public boolean updateStatusIfChanged(Long agentId, String targetStatus, Long ucId) {
		return lambdaUpdate()
			.eq(AgentInfo::getId, agentId)
			.ne(AgentInfo::getStatus, targetStatus)
			.set(AgentInfo::getStatus, targetStatus)
			.set(AgentInfo::getUpdateBy, ucId)
			.update();
	}

	@Override
	public boolean deleteIfNotPublished(Long agentId) {
		return remove(Wrappers.<AgentInfo>lambdaQuery()
			.eq(AgentInfo::getId, agentId)
			.ne(AgentInfo::getStatus, AgentStatusEnum.PUBLISHED.getValue()));
	}


	@Override
	public Page<AgentInfo> listAgents(Page<AgentInfo> page, Long workspaceId, String keyword, List<String> status,
									  String classification, List<String> tags) {
		return page(page, buildAgentQueryWrapper(workspaceId, keyword, status, classification, tags));
	}

	@Override
	public Page<AgentInfo> listPublished(Page<AgentInfo> page, Long workspaceId, String keyword, List<String> status,
										 String classification, List<String> tags) {
		return page(page, buildAgentQueryWrapper(workspaceId, keyword, status, classification, tags));
	}

	@Override
	public Page<AgentInfo> listPublishedSubAgent(Page<AgentInfo> page, Long workspaceId) {
		LambdaQueryWrapper<AgentInfo> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(AgentInfo::getWorkspaceId, workspaceId);
		wrapper.eq(AgentInfo::getStatus, AgentStatusEnum.PUBLISHED.getValue());
		wrapper.eq(AgentInfo::getCanAsSubAgent, true);
		wrapper.orderByDesc(AgentInfo::getCreateTime);
		return page(page, wrapper);
	}

	private LambdaQueryWrapper<AgentInfo> buildAgentQueryWrapper(Long workspaceId, String keyword, List<String> status,
																 String classification, List<String> tags) {
		LambdaQueryWrapper<AgentInfo> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(AgentInfo::getWorkspaceId, workspaceId);
		if (StringUtils.isNotBlank(keyword)) {
			wrapper.and(w -> w.like(AgentInfo::getAgentCode, keyword)
				.or()
				.like(AgentInfo::getName, keyword));
		}
		if (status != null && !status.isEmpty()) {
			wrapper.in(AgentInfo::getStatus, status);
		}
		if (StringUtils.isNotBlank(classification)) {
			wrapper.eq(AgentInfo::getClassification, classification);
		}
		if (tags != null && !tags.isEmpty()) {
			wrapper.and(w -> {
				boolean first = true;
				for (String tag : tags) {
					if (StringUtils.isBlank(tag)) {
						continue;
					}
					if (first) {
						w.like(AgentInfo::getTags, "\"" + tag + "\"");
						first = false;
					} else {
						w.or().like(AgentInfo::getTags, "\"" + tag + "\"");
					}
				}
			});
		}
		wrapper.orderByDesc(AgentInfo::getCreateTime);
		return wrapper;
	}

	@Override
	public AgentInfo getAgentByCode(String agentCode) {
		if (StringUtils.isBlank(agentCode)) {
			return null;
		}
		LambdaQueryWrapper<AgentInfo> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(AgentInfo::getAgentCode, agentCode);
		return getOne(wrapper);
	}

	private void initModelConfig(Long agentId, Long ucId) {
		AgentModelConfig config = new AgentModelConfig();
		config.setAgentId(agentId);
		config.setBaseUrl("");
		config.setApiKey("");
		config.setModelName("");
		config.setMaxTokens(4096);
		config.setTemperature(0.70);
		config.setTopP(1.00);
		config.setTimeout(60);
		config.setCreateBy(ucId);
		config.setUpdateBy(ucId);
		agentModelConfigService.save(config);
	}

	private void initSystemPrompt(Long agentId) {
		AgentSystemPrompt prompt = new AgentSystemPrompt();
		prompt.setAgentId(agentId);
		prompt.setContent("");
		agentSystemPromptService.save(prompt);
	}


	@Override
	public void increaseUseNum(String agentCode, String version) {
		this.baseMapper.increaseUseNum(agentCode, version);
	}
}

package com.faust.service;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.entity.AgentInfo;
import com.faust.entity.AgentTool;
import com.faust.entity.AgentToolRel;
import com.faust.entity.DataSourceConfig;
import com.faust.repository.AgentInfoRepository;
import com.faust.repository.AgentToolRelRepository;
import com.faust.repository.AgentToolRepository;
import com.faust.repository.DataSourceConfigRepository;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.dto.tool.req.ToolCreateReq;
import com.faust.dto.tool.req.ToolListQueryReq;
import com.faust.dto.tool.req.ToolPublishOrTaskOffReq;
import com.faust.dto.tool.req.ToolUpdateReq;
import com.faust.dto.tool.resp.*;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.ToolStatusEnum;
import com.faust.enums.ToolTypeEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.common.context.UserContext;
import com.faust.constant.SpaceConstants;
import com.faust.agent.cache.AgentCacheInvalidator;
import com.faust.support.SpaceGuard;
import com.faust.support.lock.DistributedLockTemplate;
import com.faust.support.lock.LockKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

	private final AgentToolRepository agentToolService;

	private final AgentToolRelRepository agentToolRelService;
	private final DataSourceConfigRepository dataSourceConfigDaoService;
	private final AgentInfoRepository agentInfoService;

	private final SpaceGuard spaceGuard;

	private final DistributedLockTemplate lockTemplate;

	private final AgentCacheInvalidator agentCacheInvalidator;

	/**
	 * 编辑类操作：等待时间长一点，技能 / 工具结构可能较大
	 */
	private static final long LOCK_WAIT_EDIT = 2000L;

	/**
	 * 发布 / 下架 / 删除：快速失败，不让用户干等
	 */
	private static final long LOCK_WAIT_QUICK = 800L;

	public ToolBaseInfoResp create(ToolCreateReq req) {
		Long spaceId = spaceGuard.currentSpaceId();
		// 空间锁：与「解散空间」互斥；数据源锁：与「数据源删除」互斥（D1）
		List<String> keys = LockKeys.merge(LockKeys.space(spaceId),
			LockKeys.dataSource(req.getDataSourceConfigId()));
		return lockTemplate.call(keys, LOCK_WAIT_EDIT, () -> doCreate(req, spaceId));
	}

	private ToolBaseInfoResp doCreate(ToolCreateReq req, Long spaceId) {
		Long ucId = UserContext.getUcId();
		// 锁内二次校验：排队期间空间可能已被解散
		spaceGuard.assertSpaceExists(spaceId);
		assertRefsInSpace(spaceId, req.getDataSourceConfigId(), req.getSubAgentId(), req.getType());
		AgentTool tool = new AgentTool();
		tool.setWorkspaceId(spaceId);
		tool.setToolCode(req.getToolCode());
		tool.setName(req.getName());
		tool.setType(req.getType());
		tool.setDescription(req.getDescription());
		tool.setToolSchema(StringUtils.isBlank(req.getSchemaJson()) ?
			null : req.getSchemaJson());
		tool.setPublishVersion(req.getVersion());
		tool.setDataSourceConfigId(req.getDataSourceConfigId());
		tool.setSubAgentId(req.getSubAgentId());
		tool.setTags(req.getTags());
		Long toolId = agentToolService.createTool(tool, ucId);
		ToolBaseInfoResp resp = new ToolBaseInfoResp();
		resp.setToolId(toolId);
		resp.setToolCode(tool.getToolCode());
		resp.setName(tool.getName());
		resp.setVersion(tool.getPublishVersion());
		return resp;
	}

	public boolean update(ToolUpdateReq req) {
		// 同时锁工具与它引用的数据源，避免「工具被改写」与「数据源被删」交错
		List<String> keys = LockKeys.merge(LockKeys.tool(req.getToolId()),
			LockKeys.dataSource(req.getDataSourceConfigId()));
		return lockTemplate.call(keys, LOCK_WAIT_EDIT, () -> doUpdate(req));
	}

	private boolean doUpdate(ToolUpdateReq req) {
		Long ucId = UserContext.getUcId();
		AgentTool tool = agentToolService.getById(req.getToolId());
		if (tool == null) {
			throw new AgentBusinessException(ErrorMessageEnum.TOOL_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.TOOL_NOT_FOUND.getErrorMsg());
		}
		spaceGuard.assertMember(tool.getWorkspaceId());
		ThrowExceptionFunction.isTrue(ToolTypeEnum.NATIVE.name().equals(tool.getType()))
			.throwMessage(ErrorMessageEnum.TOOL_NATIVE_NOT_OPERABLE);
		assertRefsInSpace(tool.getWorkspaceId(), req.getDataSourceConfigId(), req.getSubAgentId(), tool.getType());
		tool.setToolCode(req.getToolCode());
		tool.setName(req.getName());
		tool.setDescription(req.getDescription());
		tool.setToolSchema(StringUtils.isBlank(req.getSchemaJson()) ?
			null : req.getSchemaJson());
		tool.setPublishVersion(req.getVersion());
		tool.setTags(req.getTags());
		tool.setDataSourceConfigId(req.getDataSourceConfigId());
		tool.setUpdateBy(ucId);
		tool.setSubAgentId(req.getSubAgentId());
		agentToolService.updateById(tool);
		// 工具定义已被内嵌进 Agent 运行时缓存，改完必须让引用它的 Agent 重建
		evictAgentCachesByTool(req.getToolId());
		return true;
	}

	public boolean publish(ToolPublishOrTaskOffReq req) {
		return lockTemplate.call(LockKeys.tool(req.getToolId()), LOCK_WAIT_QUICK, () -> doPublish(req));
	}

	private boolean doPublish(ToolPublishOrTaskOffReq req) {
		Long ucId = UserContext.getUcId();
		AgentTool tool = agentToolService.getById(req.getToolId());
		if (tool == null) {
			throw new AgentBusinessException(ErrorMessageEnum.TOOL_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.TOOL_NOT_FOUND.getErrorMsg());
		}
		spaceGuard.assertMember(tool.getWorkspaceId());
		ThrowExceptionFunction.isTrue(ToolTypeEnum.NATIVE.name().equals(tool.getType()))
			.throwMessage(ErrorMessageEnum.TOOL_NATIVE_NOT_OPERABLE);
		if (ToolStatusEnum.PUBLISHED.getValue().equals(tool.getStatus())) {
			throw new AgentBusinessException(ErrorMessageEnum.TOOL_ALREADY_PUBLISHED.getErrorCode(),
				ErrorMessageEnum.TOOL_ALREADY_PUBLISHED.getErrorMsg());
		}
		// 条件更新兜底：锁失效时也不会出现「两个请求都以为自己是发布者」
		boolean updated = agentToolService.lambdaUpdate()
			.eq(AgentTool::getId, req.getToolId())
			.ne(AgentTool::getStatus, ToolStatusEnum.PUBLISHED.getValue())
			.set(AgentTool::getStatus, ToolStatusEnum.PUBLISHED.getValue())
			.set(AgentTool::getUpdateBy, ucId)
			.update();
		ThrowExceptionFunction.isFalse(updated).throwMessage(ErrorMessageEnum.STATUS_CHANGED);
		evictAgentCachesByTool(req.getToolId());
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean takeOff(ToolPublishOrTaskOffReq req) {
		return lockTemplate.call(LockKeys.tool(req.getToolId()), LOCK_WAIT_QUICK, () -> doTakeOff(req));
	}

	private boolean doTakeOff(ToolPublishOrTaskOffReq req) {
		Long ucId = UserContext.getUcId();
		AgentTool tool = agentToolService.getById(req.getToolId());
		ThrowExceptionFunction.isNull(tool)
			.throwMessage(ErrorMessageEnum.TOOL_NOT_FOUND);
		spaceGuard.assertMember(tool.getWorkspaceId());
		ThrowExceptionFunction.isTrue(ToolTypeEnum.NATIVE.name().equals(tool.getType()))
			.throwMessage(ErrorMessageEnum.TOOL_NATIVE_NOT_OPERABLE);
		// 先 CAS 再查引用：CAS 失败说明状态已被别人改过，直接快速失败
		boolean updated = agentToolService.lambdaUpdate()
			.eq(AgentTool::getId, req.getToolId())
			.ne(AgentTool::getStatus, ToolStatusEnum.UNPUBLISHED.getValue())
			.set(AgentTool::getStatus, ToolStatusEnum.UNPUBLISHED.getValue())
			.set(AgentTool::getUpdateBy, ucId)
			.update();
		ThrowExceptionFunction.isFalse(updated).throwMessage(ErrorMessageEnum.RESOURCE_ALREADY_TAKEOFF);
		// 仍有 Agent 引用 → 抛异常，事务回滚把状态改回 PUBLISHED
		List<AgentToolRel> agentToolRelList = agentToolRelService.listByToolId(tool.getId());
		ThrowExceptionFunction.isNotEmpty(agentToolRelList)
			.throwMessage(ErrorMessageEnum.REL_TOOL_AGENT_NOT_OPERATE);
		evictAgentCachesByTool(req.getToolId());
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean delete(ToolPublishOrTaskOffReq req) {
		return lockTemplate.call(LockKeys.tool(req.getToolId()), LOCK_WAIT_QUICK, () -> doDelete(req));
	}

	private boolean doDelete(ToolPublishOrTaskOffReq req) {
		AgentTool tool = agentToolService.getById(req.getToolId());
		ThrowExceptionFunction.isNull(tool)
			.throwMessage(ErrorMessageEnum.TOOL_NOT_FOUND);
		spaceGuard.assertMember(tool.getWorkspaceId());
		ThrowExceptionFunction.isTrue(Objects.equals(tool.getStatus(), ToolStatusEnum.PUBLISHED.getValue()))
			.throwMessage(ErrorMessageEnum.TOOL_IS_PUBLISHED_NOT_DELETED);
		ThrowExceptionFunction.isTrue(ToolTypeEnum.NATIVE.name().equals(tool.getType()))
			.throwMessage(ErrorMessageEnum.TOOL_NATIVE_NOT_OPERABLE);
		List<AgentToolRel> agentToolRelList = agentToolRelService.listByToolId(tool.getId());
		ThrowExceptionFunction.isNotEmpty(agentToolRelList)
			.throwMessage(ErrorMessageEnum.REL_TOOL_AGENT_NOT_OPERATE);
		// 删除语句自带 status <> 'PUBLISHED'，作为并发下的第二道闸
		boolean removed = agentToolService.lambdaUpdate()
			.eq(AgentTool::getId, req.getToolId())
			.ne(AgentTool::getStatus, ToolStatusEnum.PUBLISHED.getValue())
			.remove();
		ThrowExceptionFunction.isFalse(removed).throwMessage(ErrorMessageEnum.TOOL_IS_PUBLISHED_NOT_DELETED);
		// 级联解除与 Agent 的关联
		List<AgentToolRel> toolRelList = agentToolRelService.lambdaQuery()
			.eq(AgentToolRel::getToolId, req.getToolId())
			.list();
		if (!CollectionUtils.isEmpty(toolRelList)) {
			List<Long> toolRelIds = toolRelList.stream().map(AgentToolRel::getId).toList();
			agentToolRelService.removeBatchByIds(toolRelIds);
		}
		return true;
	}

	/**
	 * 工具定义（编码 / 描述 / schema / 数据源 / 子 Agent）会被内嵌进 Agent 运行时缓存，
	 * 改完必须让引用它的 Agent 重建，否则旧实例仍按旧定义调用。
	 */
	private void evictAgentCachesByTool(Long toolId) {
		List<AgentToolRel> relList = agentToolRelService.listByToolId(toolId);
		if (CollectionUtils.isEmpty(relList)) {
			return;
		}
		evictAgentCachesByAgentIds(relList.stream().map(AgentToolRel::getAgentId).toList());
	}

	private void evictAgentCachesByAgentIds(List<Long> agentIds) {
		if (CollectionUtils.isEmpty(agentIds)) {
			return;
		}
		List<String> agentCodes = agentInfoService.listByIds(agentIds.stream().distinct().toList()).stream()
			.map(AgentInfo::getAgentCode)
			.toList();
		agentCacheInvalidator.invalidateAll(agentCodes);
	}

	public Page<ToolPageInfoResp> pageList(ToolListQueryReq req) {
		long current = req.getCurrent() != null ? req.getCurrent() : 1L;
		long size = req.getSize() != null ? req.getSize() : 20L;
		Page<AgentTool> page = new Page<>(current, size);
		LambdaQueryWrapper<AgentTool> wrapper = new LambdaQueryWrapper<>();
		// 空间隔离：本空间工具 + 系统内置工具（workspace_id = 0）
		wrapper.in(AgentTool::getWorkspaceId, Arrays.asList(SpaceConstants.SYSTEM_SPACE_ID, spaceGuard.currentSpaceId()));
		if (StringUtils.isNotBlank(req.getKeyword())) {
			wrapper.and(w -> w.like(AgentTool::getToolCode, req.getKeyword())
				.or()
				.like(AgentTool::getName, req.getKeyword()));
		}
		wrapper.eq(StringUtils.isNotBlank(req.getType()), AgentTool::getType, req.getType());
		wrapper.notIn(req.isExcludeAgentTool(), AgentTool::getType, ToolTypeEnum.SUB_AGENT.name());
		wrapper.in(!CollectionUtils.isEmpty(req.getStatus()), AgentTool::getStatus, req.getStatus());
		wrapper.orderByDesc(AgentTool::getCreateTime);
		Page<AgentTool> toolPage = agentToolService.page(page, wrapper);
		Page<ToolPageInfoResp> result = new Page<>(toolPage.getCurrent(), toolPage.getSize(), toolPage.getTotal());
		result.setRecords(toolPage.getRecords().stream()
			.map(this::toToolPageInfoResp)
			.toList());
		return result;
	}

	public ToolDetailResp detail(Long toolId) {
		AgentTool tool = agentToolService.getById(toolId);
		if (tool == null) {
			throw new AgentBusinessException(ErrorMessageEnum.TOOL_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.TOOL_NOT_FOUND.getErrorMsg());
		}
		spaceGuard.assertMember(tool.getWorkspaceId());
		ToolDetailResp resp = new ToolDetailResp();
		resp.setToolId(tool.getId());
		resp.setToolCode(tool.getToolCode());
		resp.setName(tool.getName());
		resp.setType(tool.getType());
		resp.setStatus(tool.getStatus());
		resp.setDescription(tool.getDescription());
		resp.setSchemaJson(tool.getToolSchema());
		resp.setCreateTime(tool.getCreateTime());
		resp.setTags(tool.getTags());
		resp.setVersion(tool.getPublishVersion());
		if (Objects.equals(ToolTypeEnum.DB.name(), tool.getType()) &&
			Objects.nonNull(tool.getDataSourceConfigId())) {
			DataSourceConfig dbConfig = dataSourceConfigDaoService.getById(tool.getDataSourceConfigId());
			if (Objects.nonNull(dbConfig)) {
				resp.setDataSourceConfig(DataSourceConfigInfoResp.builder()
					.configId(dbConfig.getId())
					.dbName(dbConfig.getDbName())
					.dbType(dbConfig.getDbType())
					.url(dbConfig.getUrl())
					.build());
			}
		} else if (Objects.equals(ToolTypeEnum.SUB_AGENT.name(), tool.getType()) &&
			Objects.nonNull(tool.getSubAgentId())) {
			AgentInfo subAgent = agentInfoService.getById(tool.getSubAgentId());
			if (Objects.nonNull(subAgent)) {
				resp.setSubAgentInfoResp(SubAgentInfoResp.builder()
					.agentId(subAgent.getId())
					.name(subAgent.getName())
					.agentCode(subAgent.getAgentCode())
					.version(subAgent.getVersion())
					.desc(subAgent.getDescription()).build());
			}
		} else if (Objects.equals(ToolTypeEnum.NATIVE.name(), tool.getType())) {
			resp.setNativeToolsListResp(getNativeToolsList(tool));
		}
		return resp;
	}


	/**
	 * 校验工具引用的数据源 / 子智能体与工具本身属于同一空间
	 */
	private void assertRefsInSpace(Long spaceId, Long dataSourceConfigId, Long subAgentId, String type) {
		if (Objects.equals(ToolTypeEnum.DB.name(), type) && Objects.nonNull(dataSourceConfigId)) {
			DataSourceConfig config = dataSourceConfigDaoService.getById(dataSourceConfigId);
			ThrowExceptionFunction.isNull(config)
				.throwMessage(ErrorMessageEnum.DATA_SOURCE_CONFIG_NOT_FOUND);
			spaceGuard.assertSameSpace(spaceId, config.getWorkspaceId());
		}
		if (Objects.equals(ToolTypeEnum.SUB_AGENT.name(), type) && Objects.nonNull(subAgentId)) {
			AgentInfo subAgent = agentInfoService.getById(subAgentId);
			ThrowExceptionFunction.isNull(subAgent)
				.throwMessage(ErrorMessageEnum.AGENT_NOT_FOUND);
			spaceGuard.assertSameSpace(spaceId, subAgent.getWorkspaceId());
		}
	}


	private List<NativeToolsListResp> getNativeToolsList(AgentTool tool) {
		List<NativeToolsListResp> nativeToolsListRespList = new ArrayList<>();
		try {
			Object toolBean = SpringUtil.getBean(tool.getBeanName());
			Class<?> clazz = toolBean.getClass();
			Method[] methods = clazz.getMethods();
			for (Method method : methods) {
				Tool annotation = method.getAnnotation(Tool.class);
				if (annotation != null) {
					nativeToolsListRespList.add(new NativeToolsListResp(annotation.name(), annotation.description()));
				}
			}
		} catch (Exception e) {
			LOGGER.error("Get native tool function description error,msg:{}", e.getMessage(), e);
		}
		return nativeToolsListRespList;
	}


	private ToolPageInfoResp toToolPageInfoResp(AgentTool tool) {
		ToolPageInfoResp resp = new ToolPageInfoResp();
		resp.setToolId(tool.getId());
		resp.setToolCode(tool.getToolCode());
		resp.setStatus(tool.getStatus());
		resp.setName(tool.getName());
		resp.setDescription(tool.getDescription());
		resp.setTags(tool.getTags());
		resp.setType(tool.getType());
		resp.setVersion(tool.getPublishVersion());
		resp.setCreateTime(tool.getCreateTime());
		return resp;
	}

}

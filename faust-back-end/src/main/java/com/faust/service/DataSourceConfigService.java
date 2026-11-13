package com.faust.service;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
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
import com.faust.dto.datasource.req.*;
import com.faust.dto.datasource.resp.ConnectionTestResp;
import com.faust.dto.datasource.resp.DataSourceConfigBaseInfoResp;
import com.faust.dto.datasource.resp.DataSourceConfigDetailResp;
import com.faust.dto.datasource.resp.DataSourceConfigPageInfoResp;
import com.faust.enums.ErrorMessageEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.agent.factory.DataSourceFactory;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.common.security.PasswordEncryptor;
import com.faust.common.context.UserContext;
import com.faust.support.SpaceGuard;
import com.faust.agent.cache.AgentCacheInvalidator;
import com.faust.support.lock.DistributedLockTemplate;
import com.faust.support.lock.LockKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataSourceConfigService {

	private final DataSourceConfigRepository dataSourceConfigDao;

	private final AgentToolRepository agentToolService;

	private final AgentToolRelRepository agentToolRelService;

	private final AgentInfoRepository agentInfoService;

	private final AgentCacheInvalidator agentCacheInvalidator;

	private final DataSourceFactory dataSourceFactory;

	private final PasswordEncryptor passwordEncryptor;

	private final SpaceGuard spaceGuard;

	private final DistributedLockTemplate lockTemplate;

	/**
	 * 编辑类操作
	 */
	private static final long LOCK_WAIT_EDIT = 2000L;

	/**
	 * 删除：快速失败
	 */
	private static final long LOCK_WAIT_QUICK = 800L;

	public DataSourceConfigBaseInfoResp create(DataSourceConfigCreateReq req) {
		Long spaceId = spaceGuard.currentSpaceId();
		// 空间锁：与「解散空间」互斥
		return lockTemplate.call(LockKeys.space(spaceId), LOCK_WAIT_EDIT, () -> doCreate(req, spaceId));
	}

	private DataSourceConfigBaseInfoResp doCreate(DataSourceConfigCreateReq req, Long spaceId) {
		Long ucId = UserContext.getUcId();
		// 锁内二次校验：排队期间空间可能已被解散
		spaceGuard.assertSpaceExists(spaceId);
		DataSourceConfig config = new DataSourceConfig();
		config.setWorkspaceId(spaceId);
		config.setDbType(req.getDbType());
		config.setDbName(req.getDbName());
		config.setUrl(req.getUrl());
		config.setUser(req.getUser());
		config.setPwd(passwordEncryptor.encrypt(req.getPwd()));
		config.setCreateBy(ucId);
		config.setUpdateBy(ucId);
		dataSourceConfigDao.save(config);
		DataSourceConfigBaseInfoResp resp = new DataSourceConfigBaseInfoResp();
		resp.setId(config.getId());
		return resp;
	}

	public boolean update(DataSourceConfigUpdateReq req) {
		return lockTemplate.call(LockKeys.dataSource(req.getId()), LOCK_WAIT_EDIT, () -> doUpdate(req));
	}

	private boolean doUpdate(DataSourceConfigUpdateReq req) {
		Long ucId = UserContext.getUcId();
		DataSourceConfig config = dataSourceConfigDao.getById(req.getId());
		if (config == null) {
			throw new AgentBusinessException(ErrorMessageEnum.DATA_SOURCE_CONFIG_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.DATA_SOURCE_CONFIG_NOT_FOUND.getErrorMsg());
		}
		spaceGuard.assertMember(config.getWorkspaceId());
		config.setDbName(req.getDbName());
		config.setDbType(req.getDbType());
		config.setUrl(req.getUrl());
		config.setUser(req.getUser());
		// 密码留空则保留原值；填写则加密后落库
		if (StringUtils.isNotBlank(req.getPwd())) {
			config.setPwd(passwordEncryptor.encrypt(req.getPwd()));
		}
		config.setUpdateBy(ucId);
		dataSourceConfigDao.updateById(config);
		// 数据源连接信息被复制进 Agent 的工具元数据，改完要让引用它的 Agent 重建
		evictAgentCachesByDataSource(req.getId());
		return true;
	}

	/**
	 * 数据源变更后，按「数据源 → 工具 → Agent」逐级找出受影响的 Agent 并失效其缓存
	 */
	private void evictAgentCachesByDataSource(Long dataSourceId) {
		List<AgentTool> tools = agentToolService.getByDbConfigId(dataSourceId);
		if (CollectionUtils.isEmpty(tools)) {
			return;
		}
		Set<Long> agentIds = new HashSet<>();
		tools.forEach(tool -> agentToolRelService.listByToolId(tool.getId())
			.forEach(rel -> agentIds.add(rel.getAgentId())));
		if (agentIds.isEmpty()) {
			return;
		}
		List<String> agentCodes = agentInfoService.listByIds(agentIds).stream()
			.map(AgentInfo::getAgentCode)
			.toList();
		agentCacheInvalidator.invalidateAll(agentCodes);
	}

	public boolean delete(DataSourceConfigDeleteReq req) {
		return lockTemplate.call(LockKeys.dataSource(req.getId()), LOCK_WAIT_QUICK, () -> doDelete(req));
	}

	private boolean doDelete(DataSourceConfigDeleteReq req) {
		DataSourceConfig config = dataSourceConfigDao.getById(req.getId());
		ThrowExceptionFunction.isNull(config)
			.throwMessage(ErrorMessageEnum.DATA_SOURCE_CONFIG_NOT_FOUND);
		spaceGuard.assertMember(config.getWorkspaceId());
		// 与「工具创建 / 编辑」共用同一把数据源锁，避免工具指向刚被物理删除的数据源（D1）
		List<AgentTool> relToolList = agentToolService.getByDbConfigId(req.getId());
		if (CollectionUtils.isNotEmpty(relToolList)) {
			String toolCodes = relToolList.stream().map(AgentTool::getToolCode).collect(Collectors.joining());
			throw new AgentBusinessException(ErrorMessageEnum.REL_TOOL_DATA_SOURCE_CONFIG_NOT_DELETED.getErrorCode(),
				ErrorMessageEnum.REL_TOOL_DATA_SOURCE_CONFIG_NOT_DELETED.getErrorMsg() + ",Tool:" + toolCodes);
		}
		// 逻辑删除（@TableLogic 自动生效）
		dataSourceConfigDao.removeById(req.getId());
		return true;
	}

	public Page<DataSourceConfigPageInfoResp> pageList(DataSourceConfigListQueryReq req) {
		long current = req.getCurrent() != null ? req.getCurrent() : 1L;
		long size = req.getSize() != null ? req.getSize() : 20L;
		Page<DataSourceConfig> page = new Page<>(current, size);

		LambdaQueryWrapper<DataSourceConfig> wrapper = new LambdaQueryWrapper<>();
		// 空间隔离：数据源只在所属空间可见（含连接串，属于敏感信息）
		wrapper.eq(DataSourceConfig::getWorkspaceId, spaceGuard.currentSpaceId());
		if (StringUtils.isNotBlank(req.getKeyword())) {
			wrapper.and(w -> w.like(DataSourceConfig::getDbType, req.getKeyword())
				.or()
				.like(DataSourceConfig::getUrl, req.getKeyword())
				.or()
				.like(DataSourceConfig::getUser, req.getKeyword()));
		}
		wrapper.orderByDesc(DataSourceConfig::getCreateTime);

		Page<DataSourceConfig> configPage = dataSourceConfigDao.page(page, wrapper);
		Page<DataSourceConfigPageInfoResp> result = new Page<>(configPage.getCurrent(), configPage.getSize(), configPage.getTotal());
		result.setRecords(configPage.getRecords().stream()
			.map(this::toPageInfoResp)
			.toList());
		return result;
	}

	public DataSourceConfigDetailResp detail(Long id) {
		DataSourceConfig config = dataSourceConfigDao.getById(id);
		if (config == null) {
			throw new AgentBusinessException(ErrorMessageEnum.DATA_SOURCE_CONFIG_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.DATA_SOURCE_CONFIG_NOT_FOUND.getErrorMsg());
		}
		spaceGuard.assertMember(config.getWorkspaceId());
		DataSourceConfigDetailResp resp = new DataSourceConfigDetailResp();
		resp.setId(config.getId());
		resp.setDbName(config.getDbName());
		resp.setDbType(config.getDbType());
		resp.setUrl(config.getUrl());
		resp.setUser(config.getUser());
		resp.setCreateTime(config.getCreateTime());
		resp.setUpdateTime(config.getUpdateTime());
		resp.setCreateBy(config.getCreateBy());
		resp.setUpdateBy(config.getUpdateBy());
		return resp;
	}

	private DataSourceConfigPageInfoResp toPageInfoResp(DataSourceConfig config) {
		DataSourceConfigPageInfoResp resp = new DataSourceConfigPageInfoResp();
		resp.setId(config.getId());
		resp.setDbName(config.getDbName());
		resp.setDbType(config.getDbType());
		resp.setUrl(config.getUrl());
		resp.setUser(config.getUser());
		resp.setCreateTime(config.getCreateTime());
		return resp;
	}


	public ConnectionTestResp connectionTest(ConnectionTestReq req) {
		try (DruidDataSource dateSource = dataSourceFactory
			.getDateSource(req.getDbType(), req.getUrl(), req.getUser(), req.getPwd());
			 Connection connection = dateSource.getConnection()) {
			return ConnectionTestResp.builder()
				.result(connection.isValid(5))
				.build();
		} catch (SQLException e) {
			return ConnectionTestResp.builder()
				.result(false)
				.errMsg(e.getMessage())
				.build();
		} catch (Exception e) {
			return ConnectionTestResp.builder()
				.result(false)
				.errMsg("连接失败")
				.build();
		}
	}
}


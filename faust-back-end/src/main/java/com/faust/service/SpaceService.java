package com.faust.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.faust.common.context.UserContext;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.constant.SpaceConstants;
import com.faust.dto.space.req.SpaceCreateReq;
import com.faust.dto.space.req.SpaceMemberAddReq;
import com.faust.dto.space.req.SpaceRenameReq;
import com.faust.dto.space.resp.SpaceInfoResp;
import com.faust.dto.space.resp.SpaceMemberResp;
import com.faust.entity.AgentInfo;
import com.faust.entity.AgentSkill;
import com.faust.entity.AgentTool;
import com.faust.entity.DataSourceConfig;
import com.faust.entity.WorkSpace;
import com.faust.entity.WorkSpaceUser;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.SpaceRoleEnum;
import com.faust.enums.SpaceTypeEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.repository.AgentInfoRepository;
import com.faust.repository.AgentSkillRepository;
import com.faust.repository.AgentToolRepository;
import com.faust.repository.DataSourceConfigRepository;
import com.faust.repository.WorkSpaceRepository;
import com.faust.repository.WorkSpaceUserRepository;
import com.faust.support.lock.DistributedLockTemplate;
import com.faust.support.lock.LockKeys;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工作空间服务：个人空间自举、团队空间 CRUD、成员管理。
 *
 * <p>说明：本服务的接口不走 {@code SpaceInterceptor} 的当前空间校验，
 * 因此所有权限判断都基于「传入的 spaceId + 当前登录人」显式校验。</p>
 */
@Service
@RequiredArgsConstructor
public class SpaceService {

	private final WorkSpaceRepository workSpaceRepository;
	private final WorkSpaceUserRepository workSpaceUserRepository;
	private final AgentInfoRepository agentInfoRepository;
	private final AgentToolRepository agentToolRepository;
	private final AgentSkillRepository agentSkillRepository;
	private final DataSourceConfigRepository dataSourceConfigRepository;

	private final DistributedLockTemplate lockTemplate;

	/**
	 * 空间解散 / 资源创建是低频操作，等待时间给宽一点
	 */
	private static final long LOCK_WAIT_SPACE = 2000L;

	// ---------------------------------------------------------------
	// 查询
	// ---------------------------------------------------------------

	/**
	 * 我的空间列表：个人空间在最前，其次我创建的团队空间，最后我加入的团队空间
	 */
	public List<SpaceInfoResp> mySpaces() {
		Long ucId = UserContext.getUcId();
		ensurePersonalSpace(ucId);

		List<WorkSpaceUser> memberships = workSpaceUserRepository.listByUcId(ucId);
		if (memberships.isEmpty()) {
			return List.of();
		}
		Map<Long, SpaceRoleEnum> roleMap = memberships.stream()
			.filter(member -> member.getSpaceId() != null)
			.collect(Collectors.toMap(WorkSpaceUser::getSpaceId,
				member -> SpaceRoleEnum.fromValue(member.getRole()), (a, b) -> a));

		List<WorkSpace> spaces = workSpaceRepository.listByIds(roleMap.keySet());
		if (spaces.isEmpty()) {
			return List.of();
		}

		List<Long> spaceIds = spaces.stream().map(WorkSpace::getId).toList();
		Map<Long, Long> memberCounts = workSpaceUserRepository.countBySpaceIds(spaceIds);
		Map<Long, Long> agentCounts = countAgents(spaceIds);
		Map<Long, Long> toolCounts = countTools(spaceIds);
		Map<Long, Long> skillCounts = countSkills(spaceIds);
		Map<Long, Long> dataSourceCounts = countDataSources(spaceIds);

		return spaces.stream()
			.filter(space -> roleMap.containsKey(space.getId()))
			.sorted(spaceComparator(roleMap, ucId))
			.map(space -> toResp(space, roleMap.get(space.getId()), memberCounts, agentCounts,
				toolCounts, skillCounts, dataSourceCounts))
			.toList();
	}

	/**
	 * 空间详情：成员数 + 四类资源数，用于解散前提示
	 */
	public SpaceInfoResp detail(Long spaceId) {
		WorkSpace space = requireSpace(spaceId);
		assertMember(spaceId);
		List<Long> ids = List.of(spaceId);
		return toResp(space, currentRole(spaceId), workSpaceUserRepository.countBySpaceIds(ids),
			countAgents(ids), countTools(ids), countSkills(ids), countDataSources(ids));
	}

	/**
	 * 空间成员列表
	 */
	public List<SpaceMemberResp> members(Long spaceId) {
		requireSpace(spaceId);
		assertMember(spaceId);
		return workSpaceUserRepository.listBySpaceId(spaceId).stream()
			.map(this::toMemberResp)
			.toList();
	}

	/**
	 * 查询用户在某空间的角色，非成员返回 null
	 */
	public SpaceRoleEnum memberRole(Long spaceId, Long ucId) {
		return workSpaceUserRepository.memberRole(spaceId, ucId);
	}

	// ---------------------------------------------------------------
	// 个人空间
	// ---------------------------------------------------------------

	/**
	 * 获取个人空间 ID，不存在则创建（幂等：并发由 uk_personal_owner 兜底）
	 */
	@Transactional(rollbackFor = Exception.class)
	public Long ensurePersonalSpace(Long ucId) {
		WorkSpace exist = workSpaceRepository.getPersonalSpace(ucId);
		if (exist != null) {
			// 自愈：极端情况下（历史写入中断）空间存在但成员行缺失，会导致用户看不到自己的个人空间
			ensureMemberRow(exist.getId(), ucId);
			return exist.getId();
		}
		WorkSpace space = new WorkSpace();
		space.setName(SpaceConstants.PERSONAL_SPACE_NAME);
		space.setSpaceType(SpaceTypeEnum.PERSONAL.getValue());
		space.setOwnerUcId(ucId);
		space.setCreateBy(ucId);
		space.setUpdateBy(ucId);
		try {
			workSpaceRepository.save(space);
		} catch (DuplicateKeyException e) {
			// 并发下另一个请求已经建好，直接复用
			WorkSpace again = workSpaceRepository.getPersonalSpace(ucId);
			if (again != null) {
				ensureMemberRow(again.getId(), ucId);
				return again.getId();
			}
			throw e;
		}
		saveMember(space.getId(), ucId, null, SpaceRoleEnum.OWNER, ucId);
		return space.getId();
	}

	/**
	 * 确保成员行存在（幂等）
	 */
	private void ensureMemberRow(Long spaceId, Long ucId) {
		if (workSpaceUserRepository.memberRole(spaceId, ucId) != null) {
			return;
		}
		try {
			saveMember(spaceId, ucId, null, SpaceRoleEnum.OWNER, ucId);
		} catch (DuplicateKeyException ignored) {
			// 并发插入，忽略
		}
	}

	// ---------------------------------------------------------------
	// 团队空间
	// ---------------------------------------------------------------

	@Transactional(rollbackFor = Exception.class)
	public SpaceInfoResp create(SpaceCreateReq req) {
		Long ucId = UserContext.getUcId();
		WorkSpace space = new WorkSpace();
		space.setName(req.getName().trim());
		space.setSpaceType(SpaceTypeEnum.TEAM.getValue());
		space.setOwnerUcId(ucId);
		space.setDescription(StringUtils.trimToNull(req.getDescription()));
		space.setCreateBy(ucId);
		space.setUpdateBy(ucId);
		workSpaceRepository.save(space);
		saveMember(space.getId(), ucId, null, SpaceRoleEnum.OWNER, ucId);
		return toResp(space, SpaceRoleEnum.OWNER, Map.of(space.getId(), 1L), Map.of(), Map.of(), Map.of(), Map.of());
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean rename(SpaceRenameReq req) {
		WorkSpace space = requireTeamSpace(req.getSpaceId());
		assertOwner(space.getId());
		space.setName(req.getName().trim());
		space.setUpdateBy(UserContext.getUcId());
		return workSpaceRepository.updateById(space);
	}

	/**
	 * 解散空间：为安全起见，空间内仍有资源时拒绝解散（工程无逻辑删除，避免级联物理删除）
	 *
	 * <p>这里与「创建资源」共用 {@code space:{id}} 锁：不带锁时 {@code countResources}
	 * 是快照读，看不到并发创建并已提交的资源，会把刚有资源的空间删掉（W1）。</p>
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean dissolve(Long spaceId) {
		return lockTemplate.call(LockKeys.space(spaceId), LOCK_WAIT_SPACE, () -> doDissolve(spaceId));
	}

	private boolean doDissolve(Long spaceId) {
		requireTeamSpace(spaceId);
		assertOwner(spaceId);
		ThrowExceptionFunction.isTrue(countResources(spaceId) > 0)
			.throwMessage(ErrorMessageEnum.SPACE_NOT_EMPTY);
		workSpaceUserRepository.removeBySpaceId(spaceId);
		return workSpaceRepository.removeById(spaceId);
	}

	// ---------------------------------------------------------------
	// 成员
	// ---------------------------------------------------------------

	@Transactional(rollbackFor = Exception.class)
	public boolean addMember(SpaceMemberAddReq req) {
		WorkSpace space = requireTeamSpace(req.getSpaceId());
		assertMember(space.getId());
		ThrowExceptionFunction.isTrue(Objects.equals(req.getUcId(), space.getOwnerUcId())
				|| workSpaceUserRepository.memberRole(space.getId(), req.getUcId()) != null)
			.throwMessage(ErrorMessageEnum.SPACE_MEMBER_EXISTS);
		saveMember(space.getId(), req.getUcId(), StringUtils.trimToNull(req.getUcName()),
			SpaceRoleEnum.MEMBER, UserContext.getUcId());
		return true;
	}

	/**
	 * 移除成员：仅创建者可操作，且不能移除自己（自己走退出）
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean removeMember(Long spaceId, Long ucId) {
		WorkSpace space = requireTeamSpace(spaceId);
		assertOwner(spaceId);
		ThrowExceptionFunction.isTrue(Objects.equals(ucId, space.getOwnerUcId()))
			.throwMessage(ErrorMessageEnum.SPACE_OWNER_CANNOT_QUIT);
		WorkSpaceUser member = findMember(spaceId, ucId);
		ThrowExceptionFunction.isNull(member).throwMessage(ErrorMessageEnum.SPACE_MEMBER_NOT_FOUND);
		return workSpaceUserRepository.removeById(member.getId());
	}

	/**
	 * 退出空间：创建者不可退出
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean leave(Long spaceId) {
		requireTeamSpace(spaceId);
		Long ucId = UserContext.getUcId();
		WorkSpaceUser member = findMember(spaceId, ucId);
		ThrowExceptionFunction.isNull(member).throwMessage(ErrorMessageEnum.SPACE_NO_PERMISSION);
		ThrowExceptionFunction.isTrue(SpaceRoleEnum.OWNER == SpaceRoleEnum.fromValue(member.getRole()))
			.throwMessage(ErrorMessageEnum.SPACE_OWNER_CANNOT_QUIT);
		return workSpaceUserRepository.removeById(member.getId());
	}

	// ---------------------------------------------------------------
	// 内部方法
	// ---------------------------------------------------------------

	private WorkSpace requireSpace(Long spaceId) {
		WorkSpace space = spaceId == null ? null : workSpaceRepository.getById(spaceId);
		ThrowExceptionFunction.isNull(space).throwMessage(ErrorMessageEnum.SPACE_NOT_FOUND);
		return space;
	}

	private WorkSpace requireTeamSpace(Long spaceId) {
		WorkSpace space = requireSpace(spaceId);
		ThrowExceptionFunction.isFalse(SpaceTypeEnum.TEAM.getValue().equals(space.getSpaceType()))
			.throwMessage(ErrorMessageEnum.SPACE_PERSONAL_NOT_ALLOWED);
		return space;
	}

	private void assertMember(Long spaceId) {
		ThrowExceptionFunction.isNull(memberRole(spaceId, UserContext.getUcId()))
			.throwMessage(ErrorMessageEnum.SPACE_NO_PERMISSION);
	}

	private void assertOwner(Long spaceId) {
		ThrowExceptionFunction.isTrue(memberRole(spaceId, UserContext.getUcId()) != SpaceRoleEnum.OWNER)
			.throwMessage(ErrorMessageEnum.SPACE_NO_PERMISSION);
	}

	private SpaceRoleEnum currentRole(Long spaceId) {
		return memberRole(spaceId, UserContext.getUcId());
	}

	private WorkSpaceUser findMember(Long spaceId, Long ucId) {
		LambdaQueryWrapper<WorkSpaceUser> wrapper = Wrappers.lambdaQuery();
		wrapper.eq(WorkSpaceUser::getSpaceId, spaceId);
		wrapper.eq(WorkSpaceUser::getUcId, ucId);
		wrapper.last("LIMIT 1");
		return workSpaceUserRepository.getOne(wrapper);
	}

	private void saveMember(Long spaceId, Long ucId, String ucName, SpaceRoleEnum role, Long operator) {
		WorkSpaceUser member = new WorkSpaceUser();
		member.setSpaceId(spaceId);
		member.setUcId(ucId);
		member.setUcName(ucName);
		member.setRole(role.getValue());
		member.setCreateBy(operator);
		workSpaceUserRepository.save(member);
	}

	/**
	 * 空间内资源总数：Agent + 工具 + 技能 + 数据源
	 */
	private long countResources(Long spaceId) {
		return agentInfoRepository.count(Wrappers.<AgentInfo>lambdaQuery().eq(AgentInfo::getWorkspaceId, spaceId))
			+ agentToolRepository.count(Wrappers.<AgentTool>lambdaQuery().eq(AgentTool::getWorkspaceId, spaceId))
			+ agentSkillRepository.count(Wrappers.<AgentSkill>lambdaQuery().eq(AgentSkill::getWorkspaceId, spaceId))
			+ dataSourceConfigRepository.count(Wrappers.<DataSourceConfig>lambdaQuery()
			.eq(DataSourceConfig::getWorkspaceId, spaceId));
	}

	private Comparator<WorkSpace> spaceComparator(Map<Long, SpaceRoleEnum> roleMap, Long ucId) {
		return Comparator
			.comparingInt((WorkSpace space) -> sortWeight(space, roleMap, ucId))
			.thenComparing(WorkSpace::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
	}

	private int sortWeight(WorkSpace space, Map<Long, SpaceRoleEnum> roleMap, Long ucId) {
		if (SpaceTypeEnum.PERSONAL.getValue().equals(space.getSpaceType())) {
			return 0;
		}
		boolean owned = SpaceRoleEnum.OWNER == roleMap.get(space.getId())
			&& Objects.equals(space.getOwnerUcId(), ucId);
		return owned ? 1 : 2;
	}

	private SpaceInfoResp toResp(WorkSpace space, SpaceRoleEnum role,
								 Map<Long, Long> memberCounts, Map<Long, Long> agentCounts,
								 Map<Long, Long> toolCounts, Map<Long, Long> skillCounts,
								 Map<Long, Long> dataSourceCounts) {
		SpaceInfoResp resp = new SpaceInfoResp();
		resp.setSpaceId(space.getId());
		resp.setName(space.getName());
		resp.setSpaceType(space.getSpaceType());
		SpaceTypeEnum type = SpaceTypeEnum.fromValue(space.getSpaceType());
		resp.setSpaceTypeDesc(type == null ? space.getSpaceType() : type.getLabel());
		resp.setOwnerUcId(space.getOwnerUcId());
		resp.setOwnerName(space.getOwnerName());
		resp.setDescription(space.getDescription());
		resp.setRole(role == null ? null : role.getValue());
		resp.setOwner(role == SpaceRoleEnum.OWNER);
		resp.setPersonal(SpaceTypeEnum.PERSONAL.getValue().equals(space.getSpaceType()));
		resp.setMemberCount(memberCounts.getOrDefault(space.getId(), 0L));
		resp.setAgentCount(agentCounts.getOrDefault(space.getId(), 0L));
		resp.setToolCount(toolCounts.getOrDefault(space.getId(), 0L));
		resp.setSkillCount(skillCounts.getOrDefault(space.getId(), 0L));
		resp.setDataSourceCount(dataSourceCounts.getOrDefault(space.getId(), 0L));
		resp.setCreateTime(space.getCreateTime());
		return resp;
	}

	private SpaceMemberResp toMemberResp(WorkSpaceUser member) {
		SpaceMemberResp resp = new SpaceMemberResp();
		resp.setId(member.getId());
		resp.setSpaceId(member.getSpaceId());
		resp.setUcId(member.getUcId());
		resp.setUcName(member.getUcName());
		resp.setRole(member.getRole());
		SpaceRoleEnum role = SpaceRoleEnum.fromValue(member.getRole());
		resp.setRoleDesc(role == null ? member.getRole() : role.getLabel());
		resp.setOwner(role == SpaceRoleEnum.OWNER);
		resp.setCreateTime(member.getCreateTime());
		return resp;
	}

	// ---------------------------------------------------------------
	// 批量计数（避免逐空间查询）
	// ---------------------------------------------------------------

	private Map<Long, Long> countAgents(Collection<Long> spaceIds) {
		return toCountMap(agentInfoRepository.listMaps(groupCount(spaceIds)));
	}

	private Map<Long, Long> countTools(Collection<Long> spaceIds) {
		return toCountMap(agentToolRepository.listMaps(groupCount(spaceIds)));
	}

	private Map<Long, Long> countSkills(Collection<Long> spaceIds) {
		return toCountMap(agentSkillRepository.listMaps(groupCount(spaceIds)));
	}

	private Map<Long, Long> countDataSources(Collection<Long> spaceIds) {
		return toCountMap(dataSourceConfigRepository.listMaps(groupCount(spaceIds)));
	}

	private <T> QueryWrapper<T> groupCount(Collection<Long> spaceIds) {
		QueryWrapper<T> wrapper = new QueryWrapper<>();
		wrapper.select("workspace_id", "COUNT(*) AS cnt")
			.in("workspace_id", spaceIds)
			.groupBy("workspace_id");
		return wrapper;
	}

	private Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
		Map<Long, Long> result = new HashMap<>();
		if (rows == null) {
			return result;
		}
		for (Map<String, Object> row : rows) {
			Object spaceId = row.get("workspace_id") != null ? row.get("workspace_id") : row.get("workspaceId");
			Object cnt = row.get("cnt");
			if (spaceId == null || cnt == null) {
				continue;
			}
			result.put(Long.valueOf(String.valueOf(spaceId)), Long.valueOf(String.valueOf(cnt)));
		}
		return result;
	}
}

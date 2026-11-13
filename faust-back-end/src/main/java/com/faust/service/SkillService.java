package com.faust.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.common.context.UserContext;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.agent.cache.AgentCacheInvalidator;
import com.faust.dto.skill.req.SkillCreateReq;
import com.faust.dto.skill.req.SkillIdReq;
import com.faust.dto.skill.req.SkillItemReq;
import com.faust.dto.skill.req.SkillListQueryReq;
import com.faust.dto.skill.req.SkillUpdateReq;
import com.faust.dto.skill.resp.SkillBaseInfoResp;
import com.faust.dto.skill.resp.SkillDetailResp;
import com.faust.dto.skill.resp.SkillItemResp;
import com.faust.dto.skill.resp.SkillPageInfoResp;
import com.faust.entity.AgentInfo;
import com.faust.entity.AgentSkill;
import com.faust.entity.AgentSkillItem;
import com.faust.entity.AgentSkillRel;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.SkillStatusEnum;
import com.faust.exception.AgentBusinessException;
import com.faust.repository.AgentInfoRepository;
import com.faust.repository.AgentSkillItemRepository;
import com.faust.repository.AgentSkillRelRepository;
import com.faust.repository.AgentSkillRepository;
import com.faust.support.SpaceGuard;
import com.faust.support.lock.DistributedLockTemplate;
import com.faust.support.lock.LockKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Skill 管理：技能的独立增删改查与发布/下架。
 * <p>技能与 Agent 为多对多关系，通过 agent_skill_rel 关联；技能无版本概念。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

	private final AgentSkillRepository agentSkillService;

	private final AgentSkillItemRepository agentSkillItemService;

	private final AgentSkillRelRepository agentSkillRelService;

	private final SpaceGuard spaceGuard;

	private final DistributedLockTemplate lockTemplate;

	private final AgentInfoRepository agentInfoService;

	private final AgentCacheInvalidator agentCacheInvalidator;

	/**
	 * 编辑类操作：技能条目可能批量插入，等待时间给宽一点
	 */
	private static final long LOCK_WAIT_EDIT = 2000L;

	/**
	 * 发布 / 下架 / 删除：快速失败
	 */
	private static final long LOCK_WAIT_QUICK = 800L;

	public SkillBaseInfoResp create(SkillCreateReq req) {
		Long spaceId = spaceGuard.currentSpaceId();
		// 空间锁：与「解散空间」互斥
		return lockTemplate.call(LockKeys.space(spaceId), LOCK_WAIT_EDIT, () -> doCreate(req, spaceId));
	}

	private SkillBaseInfoResp doCreate(SkillCreateReq req, Long spaceId) {
		Long ucId = UserContext.getUcId();
		// 锁内二次校验：排队期间空间可能已被解散
		spaceGuard.assertSpaceExists(spaceId);
		AgentSkill skill = new AgentSkill();
		BeanUtils.copyProperties(req, skill);
		skill.setWorkspaceId(spaceId);
		Long skillId = agentSkillService.saveSkillWithItems(skill, toSkillItems(req.getSkillItemList()), ucId);
		return SkillBaseInfoResp.builder()
			.skillId(skillId)
			.skillCode(skill.getSkillCode())
			.name(skill.getName())
			.build();
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean update(SkillUpdateReq req) {
		return lockTemplate.call(LockKeys.skill(req.getSkillId()), LOCK_WAIT_EDIT, () -> doUpdate(req));
	}

	private boolean doUpdate(SkillUpdateReq req) {
		Long ucId = UserContext.getUcId();
		AgentSkill skill = agentSkillService.getById(req.getSkillId());
		ThrowExceptionFunction.isNull(skill)
			.throwMessage(ErrorMessageEnum.SKILL_NOT_FOUND);
		spaceGuard.assertMember(skill.getWorkspaceId());
		// 已发布的技能不允许编辑：与发布/下架共用 skill:{id} 锁，锁内读到状态即无并发窗口
		ThrowExceptionFunction.isTrue(Objects.equals(skill.getStatus(), SkillStatusEnum.PUBLISHED.getValue()))
			.throwMessage(ErrorMessageEnum.SKILL_IS_PUBLISHED_NOT_EDITED);
		AgentSkill update = new AgentSkill();
		BeanUtils.copyProperties(req, update);
		update.setId(req.getSkillId());
		agentSkillService.updateSkill(update, toSkillItems(req.getSkillItemList()), ucId);
		// 技能条目与描述在构建 Agent 时被复制进缓存实例，改完要让引用它的 Agent 重建
		evictAgentCachesBySkill(req.getSkillId());
		return true;
	}

	/**
	 * 技能内容变更后，失效所有引用它的 Agent 运行时缓存
	 */
	private void evictAgentCachesBySkill(Long skillId) {
		List<AgentSkillRel> relList = agentSkillRelService.listBySkillId(skillId);
		if (CollectionUtils.isEmpty(relList)) {
			return;
		}
		List<Long> agentIds = relList.stream().map(AgentSkillRel::getAgentId).distinct().toList();
		List<String> agentCodes = agentInfoService.listByIds(agentIds).stream()
			.map(AgentInfo::getAgentCode)
			.toList();
		agentCacheInvalidator.invalidateAll(agentCodes);
	}

	public boolean publish(SkillIdReq req) {
		return lockTemplate.call(LockKeys.skill(req.getSkillId()), LOCK_WAIT_QUICK, () -> doPublish(req));
	}

	private boolean doPublish(SkillIdReq req) {
		Long ucId = UserContext.getUcId();
		AgentSkill skill = agentSkillService.getById(req.getSkillId());
		ThrowExceptionFunction.isNull(skill)
			.throwMessage(ErrorMessageEnum.SKILL_NOT_FOUND);
		spaceGuard.assertMember(skill.getWorkspaceId());
		ThrowExceptionFunction.isTrue(SkillStatusEnum.PUBLISHED.getValue().equals(skill.getStatus()))
			.throwMessage(ErrorMessageEnum.SKILL_ALREADY_PUBLISHED);
		// 条件更新兜底
		boolean updated = agentSkillService.lambdaUpdate()
			.eq(AgentSkill::getId, req.getSkillId())
			.ne(AgentSkill::getStatus, SkillStatusEnum.PUBLISHED.getValue())
			.set(AgentSkill::getStatus, SkillStatusEnum.PUBLISHED.getValue())
			.set(AgentSkill::getUpdateBy, ucId)
			.update();
		ThrowExceptionFunction.isFalse(updated).throwMessage(ErrorMessageEnum.STATUS_CHANGED);
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean takeOff(SkillIdReq req) {
		return lockTemplate.call(LockKeys.skill(req.getSkillId()), LOCK_WAIT_QUICK, () -> doTakeOff(req));
	}

	private boolean doTakeOff(SkillIdReq req) {
		Long ucId = UserContext.getUcId();
		AgentSkill skill = agentSkillService.getById(req.getSkillId());
		ThrowExceptionFunction.isNull(skill)
			.throwMessage(ErrorMessageEnum.SKILL_NOT_FOUND);
		spaceGuard.assertMember(skill.getWorkspaceId());
		// 先 CAS 再查引用，失败即说明状态已被别人改过
		boolean updated = agentSkillService.lambdaUpdate()
			.eq(AgentSkill::getId, req.getSkillId())
			.ne(AgentSkill::getStatus, SkillStatusEnum.UNPUBLISHED.getValue())
			.set(AgentSkill::getStatus, SkillStatusEnum.UNPUBLISHED.getValue())
			.set(AgentSkill::getUpdateBy, ucId)
			.update();
		ThrowExceptionFunction.isFalse(updated).throwMessage(ErrorMessageEnum.RESOURCE_ALREADY_TAKEOFF);
		// 仍有 Agent 引用 → 抛异常，事务回滚把状态改回 PUBLISHED
		List<AgentSkillRel> relList = agentSkillRelService.listBySkillId(skill.getId());
		ThrowExceptionFunction.isNotEmpty(relList)
			.throwMessage(ErrorMessageEnum.REL_SKILL_AGENT_NOT_OPERATE);
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean delete(SkillIdReq req) {
		return lockTemplate.call(LockKeys.skill(req.getSkillId()), LOCK_WAIT_QUICK, () -> doDelete(req));
	}

	private boolean doDelete(SkillIdReq req) {
		AgentSkill skill = agentSkillService.getById(req.getSkillId());
		ThrowExceptionFunction.isNull(skill)
			.throwMessage(ErrorMessageEnum.SKILL_NOT_FOUND);
		spaceGuard.assertMember(skill.getWorkspaceId());
		ThrowExceptionFunction.isTrue(Objects.equals(skill.getStatus(), SkillStatusEnum.PUBLISHED.getValue()))
			.throwMessage(ErrorMessageEnum.SKILL_IS_PUBLISHED_NOT_DELETED);
		List<AgentSkillRel> relList = agentSkillRelService.listBySkillId(skill.getId());
		ThrowExceptionFunction.isNotEmpty(relList)
			.throwMessage(ErrorMessageEnum.REL_SKILL_AGENT_NOT_OPERATE);
		// 删除语句自带 status <> 'PUBLISHED'，作为并发下的第二道闸
		boolean removed = agentSkillService.lambdaUpdate()
			.eq(AgentSkill::getId, req.getSkillId())
			.ne(AgentSkill::getStatus, SkillStatusEnum.PUBLISHED.getValue())
			.remove();
		ThrowExceptionFunction.isFalse(removed).throwMessage(ErrorMessageEnum.SKILL_IS_PUBLISHED_NOT_DELETED);
		// 级联删除技能条目
		deleteSkillItems(req.getSkillId());
		return true;
	}

	private void deleteSkillItems(Long skillId) {
		List<AgentSkillItem> items = agentSkillItemService.listBySkillId(skillId);
		if (CollectionUtils.isEmpty(items)) {
			return;
		}
		List<Long> itemIds = items.stream().map(AgentSkillItem::getId).toList();
		agentSkillItemService.removeBatchByIds(itemIds);
	}

	public Page<SkillPageInfoResp> pageList(SkillListQueryReq req) {
		long current = req.getCurrent() != null ? req.getCurrent() : 1L;
		long size = req.getSize() != null ? req.getSize() : 20L;
		Page<AgentSkill> page = new Page<>(current, size);
		LambdaQueryWrapper<AgentSkill> wrapper = new LambdaQueryWrapper<>();
		// 空间隔离：技能只在所属空间可见
		wrapper.eq(AgentSkill::getWorkspaceId, spaceGuard.currentSpaceId());
		if (StringUtils.isNotBlank(req.getKeyword())) {
			wrapper.and(w -> w.like(AgentSkill::getSkillCode, req.getKeyword())
				.or()
				.like(AgentSkill::getName, req.getKeyword()));
		}
		wrapper.in(!CollectionUtils.isEmpty(req.getStatus()), AgentSkill::getStatus, req.getStatus());
		wrapper.orderByDesc(AgentSkill::getCreateTime);
		Page<AgentSkill> skillPage = agentSkillService.page(page, wrapper);
		Page<SkillPageInfoResp> result = new Page<>(skillPage.getCurrent(), skillPage.getSize(), skillPage.getTotal());
		if (CollectionUtils.isEmpty(skillPage.getRecords())) {
			result.setRecords(Collections.emptyList());
			return result;
		}
		// 一次性统计条目数量，避免逐条查询
		List<Long> skillIds = skillPage.getRecords().stream().map(AgentSkill::getId).toList();
		Map<Long, Long> itemCountMap = agentSkillItemService.listBySkillIdList(skillIds)
			.stream()
			.collect(Collectors.groupingBy(AgentSkillItem::getSkillId, Collectors.counting()));
		result.setRecords(skillPage.getRecords().stream()
			.map(skill -> toSkillPageInfoResp(skill, itemCountMap.getOrDefault(skill.getId(), 0L)))
			.toList());
		return result;
	}

	public SkillDetailResp detail(Long skillId) {
		AgentSkill skill = agentSkillService.getById(skillId);
		if (skill == null) {
			throw new AgentBusinessException(ErrorMessageEnum.SKILL_NOT_FOUND.getErrorCode(),
				ErrorMessageEnum.SKILL_NOT_FOUND.getErrorMsg());
		}
		spaceGuard.assertMember(skill.getWorkspaceId());
		SkillDetailResp resp = new SkillDetailResp();
		BeanUtils.copyProperties(skill, resp);
		resp.setSkillId(skill.getId());
		List<AgentSkillItem> items = agentSkillItemService.listBySkillId(skillId);
		resp.setSkillItemList(items.stream().map(this::toSkillItemResp).toList());
		return resp;
	}

	private List<AgentSkillItem> toSkillItems(List<SkillItemReq> itemReqList) {
		if (CollectionUtils.isEmpty(itemReqList)) {
			return new ArrayList<>();
		}
		return itemReqList.stream().map(itemReq -> {
			AgentSkillItem item = new AgentSkillItem();
			BeanUtils.copyProperties(itemReq, item);
			return item;
		}).toList();
	}

	private SkillPageInfoResp toSkillPageInfoResp(AgentSkill skill, Long itemCount) {
		SkillPageInfoResp resp = new SkillPageInfoResp();
		BeanUtils.copyProperties(skill, resp);
		resp.setSkillId(skill.getId());
		resp.setItemCount(itemCount.intValue());
		return resp;
	}

	private SkillItemResp toSkillItemResp(AgentSkillItem item) {
		SkillItemResp resp = new SkillItemResp();
		BeanUtils.copyProperties(item, resp);
		return resp;
	}

}

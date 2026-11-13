package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentSkillItem;

import java.util.List;

public interface AgentSkillItemRepository extends IService<AgentSkillItem> {

    List<AgentSkillItem> listBySkillId(Long skillId);

    List<AgentSkillItem> listBySkillIdList(List<Long>  skillIdList);
}

package com.faust.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentSkills;
import com.faust.mapper.AgentSkillsMapper;
import com.faust.repository.AgentSkillsRepository;
import org.springframework.stereotype.Service;

@Service
public class AgentSkillsRepositoryImpl extends ServiceImpl<AgentSkillsMapper, AgentSkills> implements AgentSkillsRepository {
}

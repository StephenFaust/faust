package com.faust.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.AgentDict;
import com.faust.mapper.AgentDictMapper;
import com.faust.repository.AgentDictRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentDictRepositoryImpl extends ServiceImpl<AgentDictMapper, AgentDict> implements AgentDictRepository {

    @Override
    public List<AgentDict> listDictByType(String dictType) {
        return lambdaQuery()
                .eq(AgentDict::getDictType, dictType)
                .orderByAsc(AgentDict::getSortOrder)
                .list();
    }
}

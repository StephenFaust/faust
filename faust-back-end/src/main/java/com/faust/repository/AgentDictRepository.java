package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.AgentDict;

import java.util.List;

public interface AgentDictRepository extends IService<AgentDict> {

    List<AgentDict> listDictByType(String dictType);
}

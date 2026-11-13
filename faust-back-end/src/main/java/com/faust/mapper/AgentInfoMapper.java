package com.faust.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.faust.entity.AgentInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentInfoMapper extends BaseMapper<AgentInfo> {

	// 插入用户
	@Update("UPDATE agent_info SET use_num = use_num + 1 WHERE agent_code=#{agentCode} AND version =#{version}")
	void increaseUseNum(@Param("agentCode") String agentCode, @Param("version") String version);

}

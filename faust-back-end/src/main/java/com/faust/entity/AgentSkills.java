package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("agent_skills")
public class AgentSkills {

	@TableId(type = IdType.AUTO)
	private Long id;

	private String name;

	private String description;

	private String author;

	private String tags;

	private String version;

	private String content;


}

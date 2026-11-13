package com.faust.controller;

import com.faust.entity.AgentDict;
import com.faust.repository.AgentDictRepository;
import com.faust.common.api.ApiResponse;
import com.faust.dto.dict.DictVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dict")
@Tag(name = "数据字典", description = "字典数据查询，如 Agent 状态、工具状态、技能条目类型等")
public class DictController {

	@Resource
	private AgentDictRepository agentDictService;

	@GetMapping("/{dictType}")
	@Operation(summary = "按字典类型查询", description = "根据字典类型编码获取字典列表，如 agent_status、tool_status、skill_item_type")
	public ApiResponse<List<DictVO>> listByType(@PathVariable("dictType") String dictType) {
		List<AgentDict> dicts = agentDictService.listDictByType(dictType);
		List<DictVO> result = dicts.stream().map(dict -> {
			DictVO vo = new DictVO();
			BeanUtils.copyProperties(dict, vo);
			return vo;
		}).toList();
		return ApiResponse.success(result);
	}
}

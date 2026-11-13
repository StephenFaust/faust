package com.faust.agent.tools.local;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.faust.agent.tools.local.annotation.NativeTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


@NativeTool(name = "时间日期工具集", code = "date_time_tools",
	desc = "时间日期相关工具集，包含获取当前时间", tags = {"常用工具"}, version = "1.0.1")
public class DateTimeTools {


	private static final Logger log = LoggerFactory.getLogger(DateTimeTools.class);

	@Tool(name = "current_date_time", description = "获取当前时间，案例：2026-09-10 23:00:00")
	public String getCurrentTime() {
		return LocalDateTimeUtil.formatNormal(LocalDateTime.now());
	}


	@Tool(name = "convert_timestamp", description = "转换时间戳，案例：输入：2026-09-10 23:00:00，输出：1789052400000")
	public static String convertTimestamp(String dateTimeStr) {
		if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
			return "时间字符串不能为空";
		}
		try {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN);
			LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr.trim(), formatter);
			long millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			return String.valueOf(millis);
		} catch (Exception e) {
			log.error("convert timestamp failed! errMsg:{}", e.getMessage(), e);
			return "转换时间戳失败，发生异常 ";
		}
	}
}

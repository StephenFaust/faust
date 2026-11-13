package com.faust.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;
import com.faust.dto.file.FileInfoDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Data
@TableName(value = "message_record_item", autoResultMap = true)
public class MessageRecordItem {

	/**
	 * id
	 */
	@TableId(type = IdType.AUTO)
	private Long id;
	/**
	 * 会话id
	 */
	private String chatId;
	/**
	 * 消息类型
	 */
	private String type;
	/**
	 * 消息内容
	 */
	private String content;

	/**
	 * 文件列表
	 */
	@TableField(typeHandler = FastjsonTypeHandler.class)
	private List<FileInfoDTO> fileList;

	/**
	 * 时间
	 */
	private LocalDateTime dateTime;
}

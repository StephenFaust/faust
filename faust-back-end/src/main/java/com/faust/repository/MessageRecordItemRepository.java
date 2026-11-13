package com.faust.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.faust.entity.MessageRecordItem;
import com.faust.dto.file.FileInfoDTO;
import com.faust.enums.MessageRecordType;

import java.util.List;

public interface MessageRecordItemRepository extends IService<MessageRecordItem> {


	void add(String message, MessageRecordType type,
			 String chatId, List<FileInfoDTO> fileInfoDTOList);


	/**
	 * 分页查询会话消息，按时间倒序（id 倒序）取最近的批次。
	 * 返回的 Page.records 已反转回时间正序（旧 -> 新），便于前端向上追加历史。
	 *
	 * @param chatId        会话id
	 * @param pageSize      每页条数
	 * @param lastMessageId 上一页最后一条消息id
	 */
	List<MessageRecordItem> pageHistory(String chatId, Integer pageSize, Long lastMessageId);
}

package com.faust.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.MessageRecordItem;
import com.faust.mapper.MessageRecordItemMapper;
import com.faust.repository.MessageRecordItemRepository;
import com.faust.dto.file.FileInfoDTO;
import com.faust.enums.MessageRecordType;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class MessageRecordItemRepositoryImpl extends ServiceImpl<MessageRecordItemMapper, MessageRecordItem>
	implements MessageRecordItemRepository {
	@Override
	public void add(String message, MessageRecordType type,
					String chatId, List<FileInfoDTO> fileInfoDTOList) {

		MessageRecordItem messageRecordItem = new MessageRecordItem();
		messageRecordItem.setType(type.name());
		messageRecordItem.setChatId(chatId);
		messageRecordItem.setContent(message);
		messageRecordItem.setFileList(fileInfoDTOList);
		save(messageRecordItem);

	}

	@Override
	public List<MessageRecordItem> pageHistory(String chatId, Integer pageSize, Long lastMessageId) {
		LambdaQueryWrapper<MessageRecordItem> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(MessageRecordItem::getChatId, chatId)
			.orderByDesc(MessageRecordItem::getId);
		wrapper.lt(lastMessageId > 0, MessageRecordItem::getId, lastMessageId);
		wrapper.last("limit " + pageSize);
		List<MessageRecordItem> result = list(wrapper);
		// 反转回时间正序（旧 -> 新），便于前端向上追加历史时直接拼接
		Collections.reverse(result);
		return result;
	}
}

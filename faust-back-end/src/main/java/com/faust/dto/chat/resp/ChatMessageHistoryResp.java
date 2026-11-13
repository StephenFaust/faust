package com.faust.dto.chat.resp;

import com.faust.dto.file.resp.FileInfoResp;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatMessageHistoryResp {
	private Long id;
	private String role;
	private String message;
	private List<FileInfoResp> fileInfoRespList;
}

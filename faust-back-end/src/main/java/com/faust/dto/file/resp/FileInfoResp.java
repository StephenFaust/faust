package com.faust.dto.file.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileInfoResp {
	private String fileName;
	private String fileType;
	private String url;
}

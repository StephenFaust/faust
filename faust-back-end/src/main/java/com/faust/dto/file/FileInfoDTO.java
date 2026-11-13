package com.faust.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileInfoDTO {
	private String fileName;
	private String fileType;
	private String url;
}

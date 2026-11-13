package com.faust.dto.file.req;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileInfoReq implements Serializable {

	private String url;
	private String fileName;
	private String fileType;


}

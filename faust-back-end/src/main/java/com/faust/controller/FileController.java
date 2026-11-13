package com.faust.controller;

import com.faust.dto.file.resp.FileInfoResp;
import com.faust.common.api.ApiResponse;
import com.faust.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {



	private final FileService fileService;

	/**
	 * 上传文件（传入fileKey）
	 *
	 * @param file     文件
	 * @return {@code ResponseResult<String>}
	 */
	@PostMapping("/upload")
	public ApiResponse<FileInfoResp> upload(@RequestPart("file") MultipartFile file) {
		return ApiResponse.success(fileService.uploadAndGetFileUrl(file));
	}



}

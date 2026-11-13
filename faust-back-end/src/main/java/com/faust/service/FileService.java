package com.faust.service;

import com.faust.support.AwsS3Client;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.dto.file.resp.FileInfoResp;
import com.faust.enums.ErrorMessageEnum;
import com.faust.exception.AgentBusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

	private final AwsS3Client awsS3Client;


	public FileInfoResp uploadAndGetFileUrl(MultipartFile file) {
		ThrowExceptionFunction.isTrue(file.getSize() > MAX_FILE_SIZE)
			.throwMessage(ErrorMessageEnum.FILE_SIZE_EXCEED);
		try {
			String fileName = file.getOriginalFilename();
			String fileKey = generateUploadFileKey(file.getOriginalFilename());
			// 流式上传：直接透传输入流，避免把整个文件读入 JVM 堆（大文件防 OOM）
			try (BufferedInputStream bufferedStream = new BufferedInputStream(file.getInputStream())) {
				awsS3Client.upload(fileKey, file.getSize(), bufferedStream, file.getContentType());
			}
			String url = awsS3Client.generateUrl(fileKey);
			return FileInfoResp.builder()
				.fileName(fileName)
				.url(url).build();
		} catch (Exception e) {
			throw new AgentBusinessException(ErrorMessageEnum.FILE_UPLOAD_FAILED.getErrorCode(),
				ErrorMessageEnum.FILE_UPLOAD_FAILED.getErrorMsg());
		}
	}

	/**
	 * 生成用于上传文件的文件键（FileKey）。
	 * 该文件键由当前日期（格式为 yyyyMMdd）和一个唯一标识符（通过UUID生成）组成，
	 * 如果传入了文件后缀，会将后缀添加到文件键末尾，以形成类似文件名的格式。
	 *
	 * @param fileName 客户端上传的文件名
	 * @return 生成的文件键，格式为 "yyyyMMdd/UUID/{fileName}"
	 */
	private String generateUploadFileKey(String fileName) {
		if (fileName == null || fileName.isEmpty()) {
			throw new IllegalArgumentException("文件名不能为空");
		}
		//取文件后缀
		int lastDotIndex = fileName.lastIndexOf(".");
		if (lastDotIndex < 0) {
			throw new IllegalArgumentException("文件名格式不正确");
		}
		String fileSuffix = fileName.substring(lastDotIndex);
		// 获取当前的年月日，格式化为"yyyyMMdd"
		LocalDateTime now = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		String dateStr = now.format(formatter);
		// 使用UUID生成一个唯一的标识符
		String uniqueId = UUID.randomUUID().toString().replace("-", "");
		return dateStr + "/" + uniqueId + fileSuffix;
	}

	/**
	 * 单个文件大小上限：200MB
	 */
	private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
}

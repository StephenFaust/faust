package com.faust.support;

import com.faust.config.property.FileProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * 基于 AWS SDK for Java 2.x 的 S3 客户端封装（兼容 S3 协议的对象存储，
 * 如 MinIO、阿里云 OSS、腾讯云 COS 等）。
 *
 * <p>配置复用 {@link FileProperties}（与 v1 的 {@code AwsS3Client} 共用同一份配置）。
 * 与 v1 的区别：使用 {@code software.amazon.awssdk}、强制 path-style、预签名 URL 走 {@code S3 Presigner}。
 */
@Data
@Slf4j
@Component
@RequiredArgsConstructor
public class AwsS3Client {

	private final FileProperties fileProperties;
	private S3Client s3Client;
	private S3Presigner s3Presigner;

	@PostConstruct
	public void init() {
		String endpointUrl = StringUtils.isBlank(fileProperties.getOutUrl())
			? fileProperties.getUrl()
			: fileProperties.getOutUrl();
		StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
			AwsBasicCredentials.create(fileProperties.getAk(), fileProperties.getSk()));
		Region region = Region.of(fileProperties.getSigningRegion());
		URI endpoint = URI.create(endpointUrl);

		S3Configuration serviceConfig = S3Configuration.builder()
			.chunkedEncodingEnabled(false) // 关键：强制禁用分块编码
			.build();

		this.s3Client = S3Client.builder()
			.endpointOverride(endpoint)
			.region(region)
			.credentialsProvider(credentialsProvider)
			.forcePathStyle(true)
			.serviceConfiguration(serviceConfig)
			.build();

		// 自定义 endpoint（非 AWS 官方域名）时，S3Presigner 会自动采用 path-style 地址。
		this.s3Presigner = S3Presigner.builder()
			.endpointOverride(endpoint)
			.region(region)
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(true)
				.build())
			.credentialsProvider(credentialsProvider)
			.build();

		LOGGER.info("S3 client initialized, endpoint={}, region={}", endpointUrl, fileProperties.getSigningRegion());
	}

	@PreDestroy
	public void destroy() {
		if (s3Client != null) {
			try {
				s3Client.close();
				LOGGER.info("S3 client closed.");
			} catch (Exception e) {
				LOGGER.error("S3 client failed to close.", e);
			}
		}
		if (s3Presigner != null) {
			try {
				s3Presigner.close();
				LOGGER.info("S3 presigner closed.");
			} catch (Exception e) {
				LOGGER.error("S3 presigner failed to close.", e);
			}
		}
	}

	public S3Client getS3Client() {
		if (s3Client == null) {
			throw new IllegalStateException("S3 client not initialized.");
		}
		return this.s3Client;
	}

	public String getBucket() {
		if (fileProperties.getBucket() == null) {
			throw new IllegalStateException("S3 bucket not configured.");
		}
		return fileProperties.getBucket();
	}

	/**
	 * 上传本地文件
	 *
	 * @param fileKey S3 存储的文件唯一标识
	 * @param file    待上传的本地文件
	 * @return PutObjectResponse
	 */
	public PutObjectResponse upload(String fileKey, File file) {
		try {
			fileKey = convertFileKey(getBucket(), fileKey);
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(getBucket())
				.key(fileKey)
				.build();
			return getS3Client().putObject(request, RequestBody.fromFile(file));
		} catch (Exception e) {
			LOGGER.error("S3 upload failed, fileKey={}", fileKey, e);
			throw e;
		}
	}

	/**
	 * 上传输入流（已知内容长度，推荐用于大文件流式上传）
	 *
	 * @param fileKey       S3 存储的文件唯一标识
	 * @param contentLength 内容长度（字节），必须 >= 0
	 * @param inputStream   输入流
	 * @return PutObjectResponse
	 */
	public PutObjectResponse upload(String fileKey, long contentLength, InputStream inputStream) {
		try {
			validateUploadParams(fileKey, contentLength, inputStream);
			fileKey = convertFileKey(getBucket(), fileKey);
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(getBucket())
				.key(fileKey)
				.build();
			return getS3Client().putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
		} catch (Exception e) {
			LOGGER.error("S3 upload failed, fileKey={}", fileKey, e);
			throw e;
		}
	}

	/**
	 * 上传输入流（已知内容长度 + 指定 Content-Type）
	 *
	 * @param fileKey       S3 存储的文件唯一标识
	 * @param contentLength 内容长度（字节）
	 * @param inputStream   输入流
	 * @param contentType   Content-Type（可为 null）
	 * @return PutObjectResponse
	 */
	public PutObjectResponse upload(String fileKey, long contentLength, InputStream inputStream, String contentType) {
		try {
			validateUploadParams(fileKey, contentLength, inputStream);
			fileKey = convertFileKey(getBucket(), fileKey);
			PutObjectRequest.Builder builder = PutObjectRequest.builder()
				.bucket(getBucket())
				.key(fileKey);
			if (StringUtils.isNotBlank(contentType)) {
				builder.contentType(contentType);
			}
			return getS3Client().putObject(builder.build(), RequestBody.fromInputStream(inputStream, contentLength));
		} catch (Exception e) {
			LOGGER.error("S3 upload failed, fileKey={}", fileKey, e);
			throw e;
		}
	}

	/**
	 * 上传字节数组
	 *
	 * @param fileKey S3 存储的文件唯一标识
	 * @param content 文件内容字节数组
	 * @return PutObjectResponse
	 */
	public PutObjectResponse upload(String fileKey, byte[] content) {
		try {
			if (content == null) {
				throw new IllegalArgumentException("Content can not be null");
			}
			fileKey = convertFileKey(getBucket(), fileKey);
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(getBucket())
				.key(fileKey)
				.contentLength((long) content.length)
				.contentEncoding("identity") // ★ 关键：明确指定不压缩（"identity" 表示无编码）
				.build();
			RequestBody body = RequestBody.fromContentProvider(ContentStreamProvider.fromByteArray(content), content.length,
				MediaType.APPLICATION_OCTET_STREAM_VALUE);
			return getS3Client().putObject(request, body);
		} catch (Exception e) {
			LOGGER.error("S3 upload failed, fileKey={}", fileKey, e);
			throw e;
		}
	}

	/**
	 * 生成预签名 GET URL
	 *
	 * @param fileKey S3 存储的文件唯一标识
	 * @return 预签名 URL
	 */
	public URL generatePresignedUrl(String fileKey) {
		if (StringUtils.isBlank(fileKey)) {
			throw new IllegalArgumentException("File key can not be null");
		}
		final String finalFileKey = convertFileKey(getBucket(), fileKey);
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(fileProperties.getExpireMin()))
			.getObjectRequest(e -> e.bucket(getBucket()).key(finalFileKey).build())
			.build();
		PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
		return presigned.url();
	}


	/**
	 * 生成预签名 URL 字符串
	 *
	 * @param fileKey S3 存储的文件唯一标识
	 * @return 预签名 URL 字符串
	 */
	public String generateUrl(String fileKey) {
		if (StringUtils.isBlank(fileKey)) {
			return null;
		}
		return generatePresignedUrl(fileKey).toString();
	}

	/**
	 * 删除文件
	 *
	 * @param fileKey S3 存储的文件唯一标识
	 */
	public void deleteFile(String fileKey) {
		try {
			fileKey = convertFileKey(getBucket(), fileKey);
			DeleteObjectRequest request = DeleteObjectRequest.builder()
				.bucket(getBucket())
				.key(fileKey)
				.build();
			getS3Client().deleteObject(request);
			LOGGER.info("S3 file deletion successful, fileKey={}", fileKey);
		} catch (Exception e) {
			LOGGER.error("Failed to delete S3 file, fileKey={}", fileKey, e);
		}
	}


	private void validateUploadParams(String fileId, long contentLength, InputStream inputStream) {
		if (StringUtils.isBlank(fileId)) {
			throw new IllegalArgumentException("File key can not be null");
		}
		if (contentLength < 0) {
			throw new IllegalArgumentException("Content length must be a non-negative integer.");
		}
		if (inputStream == null) {
			throw new IllegalArgumentException("Input stream can not be null");
		}
	}

	private static void closeQuietly(InputStream inputStream) {
		if (inputStream == null) {
			return;
		}
		try {
			inputStream.close();
		} catch (Exception e) {
			// ignore
		}
	}

	private static String convertFileKey(String bucket, String fileKey) {
		// 去除 fileKey 中重复包含的 bucket 前缀
		if (fileKey.startsWith(bucket)) {
			return fileKey.substring(bucket.length() + 1);
		}
		return fileKey;
	}
}

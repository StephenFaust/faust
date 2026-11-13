package com.faust.common.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 数据源连接密码加解密工具。
 *
 * <p>设计原则：
 * <ul>
 *     <li>存储层（数据库 {@code data_source_config.pwd} 列）只保存 <b>密文</b>；</li>
 *     <li>使用侧（构建 Druid 连接）在内存中解密为明文，明文不落库；</li>
 *     <li>采用 AES-256-CBC，随机 IV 前缀后 Base64 编码，每次加密结果不同；</li>
 *     <li>兼容历史明文：解密失败（旧数据或密钥不匹配）时原样返回，保证平滑迁移、不破坏存量连接。</li>
 * </ul>
 *
 * <p>密钥来源：配置项 {@code agent.center.encrypt.secret}，建议通过环境变量 {@code AGENT_PASSWORD_SECRET} 注入。
 * 生产环境务必通过密钥管理系统 / 环境变量注入，<b>不要</b>将真实密钥提交进代码仓库。
 */
@Component
public class PasswordEncryptor {

	private static final String TRANSFORMATION = "AES/CTR/NoPadding";
	private static final String ALGORITHM = "AES";
	private static final int IV_LENGTH = 16;

	private final SecretKeySpec secretKey;

	public PasswordEncryptor(
			@Value("${agent.center.encrypt.secret}") String secret) {
		if (StringUtils.isBlank(secret)) {
			throw new IllegalStateException("数据源密码加密密钥未配置（agent.center.encrypt.secret）");
		}
		try {
			// 任意长度密钥统一派生为 256-bit，避免密钥长度不足导致 AES 初始化失败
			byte[] keyBytes = MessageDigest.getInstance("SHA-256")
					.digest(secret.getBytes(StandardCharsets.UTF_8));
			this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
		} catch (Exception e) {
			throw new IllegalStateException("初始化密码加密密钥失败", e);
		}
	}

	/**
	 * 加密明文密码。
	 *
	 * @param plainText 明文密码（可为空）
	 * @return Base64(IV + 密文)，空字符串原样返回
	 */
	public String encrypt(String plainText) {
		if (StringUtils.isBlank(plainText)) {
			return plainText;
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			SecureRandom random = new SecureRandom();
			random.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

			byte[] combined = new byte[IV_LENGTH + encrypted.length];
			System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
			System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
			return Base64.getEncoder().encodeToString(combined);
		} catch (Exception e) {
			throw new IllegalStateException("密码加密失败", e);
		}
	}

	/**
	 * 解密密文为明文。解密失败时（如历史明文数据、密钥不匹配）原样返回，保证可用性。
	 *
	 * @param cipherText 密文（Base64）
	 * @return 明文密码
	 */
	public String decrypt(String cipherText) {
		if (StringUtils.isBlank(cipherText)) {
			return cipherText;
		}
		try {
			byte[] combined = Base64.getDecoder().decode(cipherText);
			if (combined.length <= IV_LENGTH) {
				// 长度不合法，视为历史明文
				return cipherText;
			}
			byte[] iv = new byte[IV_LENGTH];
			byte[] encrypted = new byte[combined.length - IV_LENGTH];
			System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
			System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
			byte[] decrypted = cipher.doFinal(encrypted);
			return new String(decrypted, StandardCharsets.UTF_8);
		} catch (Exception e) {
			// 解密失败：保持存量连接可用，原样返回（历史明文）
			return cipherText;
		}
	}
}

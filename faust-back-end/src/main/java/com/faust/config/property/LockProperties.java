package com.faust.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 分布式锁开关。
 *
 * <p>本地调试 / 单测没有 Redis 时可关闭（写操作退化为原有行为），
 * 生产环境必须保持开启：关闭等于放弃并发保护。</p>
 */
@ConfigurationProperties(prefix = "agent.center.lock")
@Configuration
@Data
public class LockProperties {

	/**
	 * 是否启用写路径分布式锁，默认开启
	 */
	private boolean enabled = true;
}

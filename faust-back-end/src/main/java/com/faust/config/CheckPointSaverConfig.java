package com.faust.config;

import com.faust.agent.checkpoint.saver.NewRedisSaver;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class CheckPointSaverConfig {


	@Bean
	public NewRedisSaver redisSaver(RedissonClient redissonClient) {
		// 配置 Redis checkPointer
		return NewRedisSaver.builder()
			.redisson(redissonClient)
			.ttl(15, TimeUnit.DAYS)
			.build();
	}
}

package com.faust.config;

import com.faust.agent.sandbox.RedisSandboxMap;
import io.agentscope.runtime.sandbox.manager.ManagerConfig;
import io.agentscope.runtime.sandbox.manager.SandboxService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SandboxServiceConfig {

	/**
	 * 沙箱注册表改为 Redis（{@link RedisSandboxMap}），多实例共享同一份 sandboxKey/containerId 映射，
	 * 任何一个实例建好的沙箱，其它实例都能查到并复用。
	 *
	 */
	@Bean(initMethod = "start")
	public SandboxService sandboxService(RedisSandboxMap sandboxMap) {
		return new SandboxService(ManagerConfig.builder().sandboxMap(sandboxMap).build());
	}

}

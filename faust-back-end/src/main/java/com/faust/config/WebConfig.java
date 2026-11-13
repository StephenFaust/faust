package com.faust.config;

import com.faust.interceptor.LoginInterceptor;
import com.faust.interceptor.SpaceInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {


	private final LoginInterceptor loginInterceptor;

	private final SpaceInterceptor spaceInterceptor;

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 身份解析必须在前：SpaceInterceptor 依赖 UserContext 中的 UCID
		registry.addInterceptor(loginInterceptor)
			.addPathPatterns("/**")
			.order(1);

		// 空间上下文：空间自身的接口（/api/space/**）自行做权限判断，不参与「当前空间」强校验
		registry.addInterceptor(spaceInterceptor)
			.addPathPatterns("/**")
			.excludePathPatterns("/api/space/**")
			.order(2);
	}
}

package com.faust.interceptor;

import com.faust.common.context.SpaceContext;
import com.faust.common.context.User;
import com.faust.common.context.UserContext;
import com.faust.common.function.ThrowExceptionFunction;
import com.faust.constant.SpaceConstants;
import com.faust.enums.ErrorMessageEnum;
import com.faust.enums.SpaceRoleEnum;
import com.faust.service.SpaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 解析当前工作空间：请求头 {@code x-space-id} 有值时校验成员身份；
 * 无值时回落到个人空间（保证 curl / 旧客户端可用）。
 *
 * <p>注意：请求头有值但不是成员时直接报错，<b>不做静默回落</b>，
 * 避免前端拿着已失效的空间写入到自己的个人空间。</p>
 */
@Component
@RequiredArgsConstructor
public class SpaceInterceptor implements HandlerInterceptor {

	private final SpaceService spaceService;

	@Override
	public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
							 @NotNull Object handler) {
		if (!(handler instanceof HandlerMethod)) {
			return true;
		}
		Long ucId = UserContext.getUserOptional().map(User::getUcId).orElse(null);
		// 未携带身份的请求（如健康检查、字典）交给下游逻辑判断，不在这里拦截
		if (ucId == null) {
			return true;
		}

		Long spaceId = parseSpaceId(request.getHeader(SpaceConstants.SPACE_ID_HEADER));
		if (spaceId == null) {
			SpaceContext.set(spaceService.ensurePersonalSpace(ucId), SpaceRoleEnum.OWNER);
			return true;
		}

		SpaceRoleEnum role = spaceService.memberRole(spaceId, ucId);
		ThrowExceptionFunction.isNull(role).throwMessage(ErrorMessageEnum.SPACE_NO_PERMISSION);
		SpaceContext.set(spaceId, role);
		return true;
	}

	@Override
	public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
								@NotNull Object handler, Exception ex) {
		SpaceContext.remove();
	}

	private Long parseSpaceId(String raw) {
		if (StringUtils.isBlank(raw)) {
			return null;
		}
		try {
			long value = Long.parseLong(raw.trim());
			return value > 0 ? value : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}

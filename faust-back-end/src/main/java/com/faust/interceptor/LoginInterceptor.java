package com.faust.interceptor;


import com.faust.common.context.User;
import com.faust.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;


import java.util.Optional;

@Component
public class LoginInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
		if (!(handler instanceof HandlerMethod)) {
			return true;
		}
		User user = new User();
		setUserHeaders(request, user);
		UserContext.setUser(user);
		return true;
	}

	private void setUserHeaders(HttpServletRequest request, User user) {
		String ucid = request.getHeader(RequestConst.UCID_HEADER);
		String supplier = request.getHeader(RequestConst.SUPPLIER_HEADER);
		String source = request.getHeader(RequestConst.SOURCE_HEADER);

		Optional.ofNullable(ucid).map(Long::valueOf).ifPresent(user::setUcId);
		Optional.ofNullable(supplier).ifPresent(user::setSupplierCode);
		Optional.ofNullable(source).map(Integer::valueOf).ifPresent(user::setSource);
	}

	@Override
	public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
								@NotNull Object handler, Exception ex) {
		UserContext.remove();
	}


	static class RequestConst {

		private RequestConst() {
		}

		public static final String UCID_HEADER = "user-id";
		public static final String SUPPLIER_HEADER = "supplierCode";
		public static final String SOURCE_HEADER = "channelSource";
	}

}

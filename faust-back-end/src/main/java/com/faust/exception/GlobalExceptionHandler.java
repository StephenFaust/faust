package com.faust.exception;

import com.faust.controller.*;
import com.faust.exception.AgentBusinessException;
import com.faust.common.api.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@Slf4j
@ControllerAdvice(assignableTypes = {
		AgentController.class,
		ToolController.class,
		SkillController.class,
		DictController.class,
		ChatController.class,
		SpaceController.class,
		DataSourceConfigController.class,
		SkillController.class
})
public class GlobalExceptionHandler {

	@ExceptionHandler(AgentBusinessException.class)
	@ResponseBody
	public ApiResponse<Void> handleBusinessException(AgentBusinessException e) {
		LOGGER.warn("Business exception: {}", e.getMessage());
		return ApiResponse.error(e.getCode(), e.getMessage());
	}

	@ExceptionHandler(AsyncRequestNotUsableException.class)
	@ResponseBody
	public ApiResponse<Void> handleAsyncRequestNotUsableException(AsyncRequestNotUsableException e) {
		LOGGER.warn("IO exception: Agent cancel Broken pipe", e);
		return null;
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
	@ResponseBody
	public ApiResponse<Void> handleValidationException(Exception e) {
		String message = "参数校验失败";
		if (e instanceof MethodArgumentNotValidException ex) {
			message = ex.getBindingResult().getFieldErrors().stream()
					.findFirst()
					.map(error -> error.getField() + ": " + error.getDefaultMessage())
					.orElse(message);
		} else if (e instanceof BindException ex) {
			message = ex.getBindingResult().getFieldErrors().stream()
					.findFirst()
					.map(error -> error.getField() + ": " + error.getDefaultMessage())
					.orElse(message);
		}
		return ApiResponse.error(2, message);
	}

	/**
	 * 处理类级别 @Validated 触发的 query / path 参数约束违反异常
	 * （如 @Valid AgentWhitelistListQueryReq query 上的 @NotBlank 未填时抛出）
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	@ResponseBody
	public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException e) {
		String message = e.getConstraintViolations().stream()
				.findFirst()
				.map(ConstraintViolation::getMessage)
				.orElse("参数校验失败");
		return ApiResponse.error(2, message);
	}

	@ExceptionHandler(Exception.class)
	@ResponseBody
	public ApiResponse<Void> handleException(Exception e) {
		LOGGER.error("Internal error", e);
		return ApiResponse.error(1, "系统内部异常");
	}
}

package com.faust.exception;


import lombok.Getter;

@Getter
public class AgentBusinessException extends RuntimeException {
	private final int code;

	public AgentBusinessException(int code, String message) {
		super(message);
		this.code = code;
	}

	public AgentBusinessException(int code, String message, Throwable throwable) {
		super(message, throwable);
		this.code = code;
	}
}

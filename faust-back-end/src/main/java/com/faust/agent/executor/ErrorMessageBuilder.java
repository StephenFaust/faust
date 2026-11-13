package com.faust.agent.executor;

public class ErrorMessageBuilder {


	private static final String DEFAULT_ERROR_MSG = """
		{
		"error":true,
		"errorMessage":"Internal error occurred"
		}
		""";

	private ErrorMessageBuilder() {
	}

	public static String errorMsg(String errorMessage, String errorType) {
		return String.format("""
			{
			"error":true,
			"errorMessage":"%s",
			"errorType":"%s"
			}
			""", errorMessage, errorType);
	}

	public static String errorMsg() {
		return DEFAULT_ERROR_MSG;
	}


	public static String errorMsg(String errorMessage) {
		return String.format("""
			{
			"error":true,
			"errorMessage":"%s"
			}
			""", errorMessage);
	}
}

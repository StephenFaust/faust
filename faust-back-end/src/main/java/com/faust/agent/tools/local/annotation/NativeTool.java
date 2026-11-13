package com.faust.agent.tools.local.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Component
@Documented
public @interface NativeTool {

	String name();

	String code();

	String version();

	String desc();

	String[] tags();
}

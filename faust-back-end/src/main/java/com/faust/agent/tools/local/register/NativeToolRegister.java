package com.faust.agent.tools.local.register;


import com.faust.entity.AgentTool;
import com.faust.repository.AgentToolRepository;
import com.faust.enums.ToolStatusEnum;
import com.faust.enums.ToolTypeEnum;
import com.faust.agent.tools.local.annotation.NativeTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class NativeToolRegister implements ApplicationListener<ContextRefreshedEvent> {


	private final AgentToolRepository agentToolService;


	private void register(String beanName, Object bean) {
		try {
			Class<?> clazz = bean.getClass();
			NativeTool annotation = clazz.getAnnotation(NativeTool.class);
			if (Objects.isNull(annotation)) {
				return;
			}
			String toolCode = annotation.code();
			AgentTool tool = agentToolService.getToolByCode(toolCode);
			if (Objects.isNull(tool)) {
				AgentTool newTool = new AgentTool();
				newTool.setToolCode(annotation.code());
				newTool.setName(annotation.name());
				newTool.setBeanName(beanName);
				newTool.setType(ToolTypeEnum.NATIVE.name());
				newTool.setStatus(ToolStatusEnum.PUBLISHED.getValue());
				newTool.setDescription(annotation.desc());
				newTool.setTags(Arrays.asList(annotation.tags()));
				newTool.setPublishVersion(annotation.version());
				agentToolService.save(newTool);
			} else if (ToolTypeEnum.NATIVE.name().equals(tool.getType())) {
				tool.setName(annotation.name());
				tool.setBeanName(beanName);
				tool.setDescription(annotation.desc());
				tool.setTags(Arrays.asList(annotation.tags()));
				tool.setPublishVersion(annotation.version());
				agentToolService.updateById(tool);
			} else {
				LOGGER.warn("Duplicate tool code with non-native tool,toolCode:{}", toolCode);
			}
		} catch (Exception e) {
			LOGGER.error("Register native tool failed,errMsg:{}", e.getMessage(), e);
		}

	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		ApplicationContext applicationContext = event.getApplicationContext();
		Map<String, Object> nativeToolBeanMap = applicationContext.getBeansWithAnnotation(NativeTool.class);
		if (MapUtils.isEmpty(nativeToolBeanMap)) {
			return;
		}
		nativeToolBeanMap.forEach(this::register);
	}
}

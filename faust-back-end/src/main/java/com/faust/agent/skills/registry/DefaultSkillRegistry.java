package com.faust.agent.skills.registry;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.AbstractSkillRegistry;
import com.alibaba.fastjson.JSON;
import com.faust.dto.agent.SkillItemMateInfo;
import com.faust.dto.agent.SkillMateInfo;
import com.faust.enums.SkillItemTypeEnum;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MySQL 版 SkillRegistry。
 * <p>框架只提供一条读取链路：read_skill(skill_name) -> SkillRegistry.readSkillContent(name)，
 * 且 name 会被 ReadSkillTool 原样透传，因此在这里做「虚拟路径」解析即可让 Agent 同时读到
 * SKILL.md（CONTENT 条目）与附属文件（REFERENCE / SCRIPT 条目）。</p>
 * <p>虚拟路径协议：</p>
 * <ul>
 *   <li>{@code skill://{skillName}} —— 技能正文，末尾附带附属文件清单</li>
 *   <li>{@code skill://{skillName}/references/{fileName}} —— REFERENCE 条目内容</li>
 *   <li>{@code skill://{skillName}/scripts/{fileName}} —— SCRIPT 条目内容</li>
 * </ul>
 */
public class DefaultSkillRegistry extends AbstractSkillRegistry {
	private static final Logger logger = LoggerFactory.getLogger(DefaultSkillRegistry.class);

	/**
	 * Skill 虚拟路径前缀，同时写入 SkillMetadata.skillPath 供 Prompt 展示
	 */
	private static final String SKILL_PATH_SCHEME = "skill://";

	private static final String PATH_SEPARATOR = "/";

	private static final String SCHEME_SEPARATOR = "://";

	private static final String REFERENCES_DIR = "references";

	private static final String SCRIPTS_DIR = "scripts";

	private static final String SECTION_SEPARATOR = "\n\n";

	private static final String MANIFEST_HEADER = "## Supporting Files";

	private static final String TITLE_PREFIX = "## ";

	private static final String DEFAULT_SOURCE = "mysql";

	/**
	 * 标题/内容里会破坏虚拟路径解析的字符：路径分隔符与引号
	 */
	private static final String PATH_UNSAFE_CHARS = "[/\\\\\"]";

	private static final Pattern BREAK_LINE_PATTERN = Pattern.compile("[\\r\\n]+");

	private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);

	private final List<SkillMateInfo> skillMateInfos;

	private final SystemPromptTemplate systemPromptTemplate;

	/**
	 * skillName -> (附属文件相对路径 -> 文件内容)，如 "references/退款规则" -> "..."
	 */
	private Map<String, Map<String, String>> attachments = Collections.emptyMap();

	public DefaultSkillRegistry(List<SkillMateInfo> skillMateInfos, SystemPromptTemplate systemPromptTemplate, boolean autoLoad) {
		this.skillMateInfos = skillMateInfos;
		this.systemPromptTemplate = systemPromptTemplate;
		if (autoLoad) {
			loadSkillsToRegistry();
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	protected void loadSkillsToRegistry() {
		Map<String, SkillMetadata> loadedSkills = new HashMap<>();
		Map<String, Map<String, String>> loadedAttachments = new HashMap<>();
		try {
			if (!CollectionUtils.isEmpty(skillMateInfos)) {
				skillMateInfos.forEach(skillMateInfo -> {
					String skillName = skillMateInfo.getName();
					if (StringUtils.isBlank(skillName)) {
						logger.warn("Skipping skill with empty name: {}", JSON.toJSONString(skillMateInfo));
						return;
					}
					List<SkillItemMateInfo> skillItemMateInfoList = skillMateInfo.getSkillItemMateInfoList();
					if (CollectionUtils.isEmpty(skillItemMateInfoList)) {
						logger.error("Skipping skill with empty item: {}", skillName);
						return;
					}
					String fullContent = mergeContentItems(skillItemMateInfoList);
					if (StringUtils.isBlank(fullContent)) {
						logger.error("Skipping skill with empty content: {}", skillName);
						return;
					}
					Map<String, String> skillAttachments = collectAttachments(skillItemMateInfoList);
					String skillPath = SKILL_PATH_SCHEME + skillName;
					SkillMetadata metadata = SkillMetadata.builder()
						.name(skillName)
						.description(skillMateInfo.getDescription())
						.fullContent(fullContent + buildManifest(skillPath, skillAttachments))
						.source(DEFAULT_SOURCE)
						.skillPath(skillPath)
						.build();
					loadedSkills.put(metadata.getName(), metadata);
					loadedAttachments.put(skillName, skillAttachments);
					logger.debug("Loaded skill from MySQL: {} with {} supporting file(s)",
						skillName, skillAttachments.size());
				});
				logger.info("Loaded {} skills from MySQL", loadedSkills.size());
			}
		} catch (Exception e) {
			logger.error("Failed to load skills from MySQL: {}", e.getMessage(), e);
		}
		this.skills = loadedSkills;
		this.attachments = loadedAttachments;
	}

	/**
	 * 合并 CONTENT 条目：单条保持原样，多条按 sortOrder 顺序拼接并用 fileName 作为小节标题。
	 */
	private static String mergeContentItems(List<SkillItemMateInfo> skillItemMateInfoList) {
		List<SkillItemMateInfo> contentItems = skillItemMateInfoList.stream()
			.filter(item -> Objects.equals(item.getItemType(), SkillItemTypeEnum.CONTENT.name()))
			.filter(item -> StringUtils.isNotBlank(item.getContent()))
			.toList();
		if (contentItems.isEmpty()) {
			return null;
		}
		if (contentItems.size() == 1) {
			return contentItems.get(0).getContent();
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < contentItems.size(); i++) {
			SkillItemMateInfo item = contentItems.get(i);
			if (i > 0) {
				builder.append(SECTION_SEPARATOR);
			}
			if (StringUtils.isNotBlank(item.getFileName())) {
				builder.append(TITLE_PREFIX).append(item.getFileName()).append(SECTION_SEPARATOR);
			}
			builder.append(item.getContent());
		}
		return builder.toString();
	}

	/**
	 * 收集 REFERENCE / SCRIPT 条目，生成「相对路径 -> 内容」的映射。
	 */
	private static Map<String, String> collectAttachments(List<SkillItemMateInfo> skillItemMateInfoList) {
		Map<String, String> skillAttachments = new LinkedHashMap<>();
		Map<String, Integer> indexByDir = new HashMap<>(2);
		skillItemMateInfoList.stream()
			.filter(item -> isAttachmentType(item.getItemType()))
			.filter(item -> StringUtils.isNotBlank(item.getContent()))
			.forEach(item -> {
				String dir = Objects.equals(item.getItemType(), SkillItemTypeEnum.SCRIPT.name())
					? SCRIPTS_DIR : REFERENCES_DIR;
				int index = indexByDir.merge(dir, 1, Integer::sum);
				String fileName = resolveAttachmentName(item, index);
				String path = dir + PATH_SEPARATOR + fileName;
				// 同名文件在扩展名前插入序号，保证每个附属文件都能被唯一寻址
				String uniquePath = path;
				for (int seq = 2; skillAttachments.containsKey(uniquePath); seq++) {
					uniquePath = dir + PATH_SEPARATOR + appendSequence(fileName, seq);
				}
				if (!Objects.equals(uniquePath, path)) {
					logger.warn("Duplicated supporting file name in skill, renamed: {} -> {}", path, uniquePath);
				}
				skillAttachments.put(uniquePath, item.getContent());
			});
		return skillAttachments;
	}

	private static boolean isAttachmentType(String itemType) {
		return Objects.equals(itemType, SkillItemTypeEnum.REFERENCE.name())
			|| Objects.equals(itemType, SkillItemTypeEnum.SCRIPT.name());
	}

	/**
	 * 计算附属文件名：fileName 优先，其次从内容里推导（首个 markdown 标题 / URL 末段），最后退回序号。
	 */
	private static String resolveAttachmentName(SkillItemMateInfo item, int index) {
		String name = sanitizePathSegment(item.getFileName());
		if (StringUtils.isNotBlank(name)) {
			return name;
		}
		name = resolveNameFromContent(item.getContent());
		if (StringUtils.isNotBlank(name)) {
			return name;
		}
		Integer sortOrder = item.getSortOrder();
		return "file-" + (Objects.nonNull(sortOrder) ? sortOrder : index);
	}

	/**
	 * 在扩展名之前插入序号：{@code a.md -> a-2.md}；
	 * 无扩展名或纯点开头的名字（{@code .env}）直接追加：{@code README -> README-2}。
	 */
	private static String appendSequence(String fileName, int seq) {
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex <= 0) {
			return fileName + "-" + seq;
		}
		return fileName.substring(0, dotIndex) + "-" + seq + fileName.substring(dotIndex);
	}

	private static String resolveNameFromContent(String content) {
		try {
			String candidate = null;
			Matcher matcher = HEADING_PATTERN.matcher(content);
			if (matcher.find()) {
				candidate = matcher.group(1);
			} else if (StringUtils.startsWithAny(content.trim(), "http://", "https://")) {
				String path = URI.create(content.trim()).getPath();
				candidate = path.substring(path.lastIndexOf(PATH_SEPARATOR) + 1);
			}
			return sanitizePathSegment(candidate);
		} catch (Exception e) {
			logger.debug("Failed to derive supporting file name from content: {}", e.getMessage());
			return null;
		}
	}

	private static String sanitizePathSegment(String raw) {
		if (StringUtils.isBlank(raw)) {
			return null;
		}
		String sanitized = BREAK_LINE_PATTERN.matcher(raw.trim()).replaceAll(" ")
			.replaceAll(PATH_UNSAFE_CHARS, "-")
			.trim();
		return StringUtils.isBlank(sanitized) ? null : sanitized;
	}

	/**
	 * 把附属文件清单追加到技能正文末尾。
	 * <p>清单只在 read_skill 时随正文返回，不占用常驻 Prompt；无附属文件时返回空串，行为与旧版一致。</p>
	 */
	private static String buildManifest(String skillPath, Map<String, String> skillAttachments) {
		if (CollectionUtils.isEmpty(skillAttachments)) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		builder.append(SECTION_SEPARATOR)
			.append(MANIFEST_HEADER).append(SECTION_SEPARATOR)
			.append("This skill bundles ").append(skillAttachments.size())
			.append(" supporting file(s). Read one only when the task actually needs it, ")
			.append("using the exact call listed below:\n");
		skillAttachments.keySet().forEach(path -> builder.append("- read_skill(\"")
			.append(skillPath).append(PATH_SEPARATOR).append(path)
			.append("\")\n"));
		return builder.toString();
	}

	@Override
	public synchronized void reload() {
		logger.info("Reloading skills from MySQL...");
		loadSkillsToRegistry();
	}

	@Override
	public String readSkillContent(String name) {
		if (StringUtils.isBlank(name)) {
			throw new IllegalArgumentException("Skill name cannot be null or empty");
		}
		String normalized = normalizeSkillPath(name);
		// 1. 精确命中 Skill 名 -> 返回技能正文（末尾附带附属文件清单）
		Optional<SkillMetadata> skillOpt = get(normalized);
		if (skillOpt.isPresent()) {
			String fullContent = skillOpt.get().getFullContent();
			return fullContent != null ? fullContent : "";
		}
		// 2. 命中附属文件虚拟路径 {skillName}/{references|scripts}/{fileName}
		int separatorIndex = normalized.indexOf(PATH_SEPARATOR);
		if (separatorIndex <= 0) {
			throw new IllegalStateException("Skill not found: " + name);
		}
		String skillName = normalized.substring(0, separatorIndex);
		if (get(skillName).isEmpty()) {
			throw new IllegalStateException("Skill not found: " + name);
		}
		String attachmentPath = normalized.substring(separatorIndex + 1);
		Map<String, String> skillAttachments = attachments.getOrDefault(skillName, Collections.emptyMap());
		if (!skillAttachments.containsKey(attachmentPath)) {
			throw new IllegalStateException("Supporting file not found: " + attachmentPath + " in skill " + skillName
				+ ". Available supporting files: " + skillAttachments.keySet());
		}
		return skillAttachments.get(attachmentPath);
	}

	/**
	 * 归一化虚拟路径：去掉协议前缀与本就不该出现的首尾斜杠。
	 */
	private static String normalizeSkillPath(String name) {
		String normalized = name.trim();
		int schemeIndex = normalized.indexOf(SCHEME_SEPARATOR);
		if (schemeIndex >= 0) {
			normalized = normalized.substring(schemeIndex + SCHEME_SEPARATOR.length());
		}
		while (normalized.startsWith(PATH_SEPARATOR)) {
			normalized = normalized.substring(1);
		}
		while (normalized.endsWith(PATH_SEPARATOR)) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	@Override
	public String getSkillLoadInstructions() {
		return """
			**Skill Path Format:**
			  - Each skill is addressed by its name shown in the skill list above, i.e. `skill://{skillName}`.
			    Call `read_skill("skill://{skillName}")` to read the skill instruction.
			  - Some skills bundle extra supporting files (reference documents, scripts). Their list is appended
			    at the end of the skill content under `## Supporting Files`.
			  - Read a supporting file only when the task actually needs it, using the exact path from that list:
			    `read_skill("skill://{skillName}/references/{fileName}")` or `read_skill("skill://{skillName}/scripts/{fileName}")`

			**Important:**
			  - Never guess or invent a supporting file path. Only use the paths listed under `## Supporting Files`.
			  - Supporting files are read on demand; do not read them all up front.
			""";
	}

	@Override
	public String getRegistryType() {
		return "MySQL";
	}

	@Override
	public SystemPromptTemplate getSystemPromptTemplate() {
		return systemPromptTemplate;
	}

	public static class Builder {
		private SystemPromptTemplate systemPromptTemplate;
		private List<SkillMateInfo> skillMateInfos;

		public Builder agentSkillsService(List<SkillMateInfo> skillMateInfos) {
			this.skillMateInfos = skillMateInfos;
			return this;
		}


		public DefaultSkillRegistry build() {
			if (systemPromptTemplate == null) {
				systemPromptTemplate = SystemPromptTemplate.builder()
					.template(DEFAULT_SYSTEM_PROMPT_TEMPLATE)
					.build();
			}

			return new DefaultSkillRegistry(skillMateInfos, systemPromptTemplate, true);
		}

		private static final String DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
			## Skills System

			You have access to a skills library that provides specialized capabilities and domain knowledge.
			All skills are stored in a Skill Registry with MySQL database storage.

			### Available Skills

			{skills_list}

			### How to Use Skills (Progressive Disclosure) \s

			Skills follow a **progressive disclosure** pattern - you know they exist (name + description above),
			but you only read the full instructions when needed:
			    1. **Recognize when a skill applies**: Check if the user's task matches any skill's description
			    2. **Read the skill's full instructions**: The skill list above shows the exact skill id to use with `read_skill`
			    3. **Follow the skill's instructions**: The skill contains step-by-step workflows, best practices, and examples
			    4. **Access supporting files**: When the skill content lists `## Supporting Files`, read the needed one
			       with `read_skill` using its exact path, instead of reading them all

			#### How to Read The Full Skill Instruction

			You are currently using the MySQL based Skill Registry. Please follow the skill loading guidelines below:
			{skills_load_instructions}

			**Important:**

			- Use `read_skill` to read skill instructions. Do not attempt to access skills through other methods.

			 #### When to Use Skills

			- When the user's request matches a skill's domain
			- When you need specialized knowledge or structured workflows
			- When a skill provides proven patterns for complex tasks

			#### Skills are Self-Documenting

			- Each skill tells you exactly what the skill does and how to use it
			- The skill list above shows the skill id for each skill
			 Remember: Skills are tools to make you more capable and consistent.\s
			 When in doubt, check if a skill exists for the task!
			""";
	}
}

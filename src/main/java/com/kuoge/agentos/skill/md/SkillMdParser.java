package com.kuoge.agentos.skill.md;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;

/**
 * SKILL.md 解析器：YAML frontmatter + markdown body 两段式。
 * 格式与 AgentSkills 规范一致：
 * <pre>
 * ---
 * name: ad-review-draft
 * description: ...
 * ---
 * （markdown 正文）
 * </pre>
 */
public final class SkillMdParser {

    private SkillMdParser() {}

    /** 解析结果：元数据 + 正文 */
    public record Parsed(SkillMetadata metadata, String body) {}

    public static Parsed parse(String raw, String sourceName) {
        if (raw == null || !raw.startsWith("---")) {
            throw new IllegalArgumentException("SKILL.md missing YAML frontmatter: " + sourceName);
        }
        int end = raw.indexOf("\n---", 3);
        if (end < 0) {
            throw new IllegalArgumentException("SKILL.md frontmatter not closed: " + sourceName);
        }
        String frontmatter = raw.substring(3, end).trim();
        String body = raw.substring(end + 4).trim();

        Map<String, Object> yaml = new Yaml().load(frontmatter);
        if (yaml == null) {
            throw new IllegalArgumentException("SKILL.md empty frontmatter: " + sourceName);
        }
        SkillMetadata metadata = new SkillMetadata(
                str(yaml, "name", sourceName, true),
                str(yaml, "description", sourceName, true),
                str(yaml, "version", sourceName, false),
                str(yaml, "form", sourceName, false),
                str(yaml, "risk", sourceName, false),
                str(yaml, "plans", sourceName, false));
        return new Parsed(metadata, body);
    }

    private static String str(Map<String, Object> yaml, String key, String source, boolean required) {
        Object value = yaml.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(
                        "SKILL.md frontmatter missing required key '" + key + "': " + source);
            }
            return "";
        }
        return String.valueOf(value);
    }
}

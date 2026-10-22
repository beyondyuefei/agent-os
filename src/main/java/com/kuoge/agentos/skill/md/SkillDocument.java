package com.kuoge.agentos.skill.md;

import java.util.List;

/**
 * Skill 文档（AgentSkills 规范第二级：body 按需加载后的完整内容）。
 *
 * @param metadata   frontmatter 元数据
 * @param body       markdown 正文（提示词模板，可含 {{variable}} 占位符）
 * @param references references/ 目录下可用的引用文件名（第三级，按需单独加载）
 */
public record SkillDocument(
        SkillMetadata metadata,
        String body,
        List<String> references
) {
    public SkillDocument {
        references = references == null ? List.of() : List.copyOf(references);
    }
}

package com.kuoge.agentos.skill.md;

import java.util.Map;

/**
 * Skill 契约（contract.yaml 的完整映射）：
 * 元数据 + 全部模板的变量声明与默认内容。契约变更 = 工程评审（PR）；
 * 内容迭代 = 运营端评审流（草稿 → 发布 → 快照）。
 */
public record SkillContract(
        SkillMetadata metadata,
        /** 模板键 → 契约（BODY 表示主提示词，其余为话术 / 格式规则） */
        Map<String, TemplateContract> templates
) {
    public SkillContract {
        templates = templates == null ? Map.of() : Map.copyOf(templates);
    }

    public TemplateContract requireTemplate(String templateKey) {
        TemplateContract contract = templates.get(templateKey);
        if (contract == null) {
            throw new IllegalArgumentException(
                    "Template not declared in contract: " + metadata.name() + "/" + templateKey);
        }
        return contract;
    }
}

package com.kuoge.agentos.skill;

/**
 * Skill 定义（方案 3.1 统一模板 / 3.6 AgentSkills 规范 metadata 映射）。
 */
public record SkillDefinition(
        String code,
        String version,
        /** domain-service | ai-skill */
        String form,
        RiskLevel risk,
        /** 适用套餐档位，all 表示全档位同一版本线 */
        String plans
) {}

package com.kuoge.agentos.skill.md;

/**
 * Skill 元数据（contract.yaml 头部字段的映射，对应 AgentSkills 规范第一级：
 * metadata 常驻，框架随时可见，用于触发判断）。
 */
public record SkillMetadata(
        String name,
        String description,
        /** 版本线：1.x / 2.x / all，与 plan_capability_binding.skill_version 对齐 */
        String version,
        /** domain-service | ai-skill */
        String form,
        /** READ | WRITE_LOW_RISK | WRITE_HIGH_RISK */
        String risk,
        /** 适用套餐档位，all 表示全档位 */
        String plans
) {}

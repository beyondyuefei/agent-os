package com.kuoge.agentos.skill;

import com.kuoge.agentos.capability.PolicyProfile;

/**
 * Skill 执行上下文：租户、操作者与网关注入的策略快照。
 */
public record SkillContext(
        String tenantId,
        String operatorId,
        /** 网关判定后注入的策略档位（ALLOW / DEGRADED 时存在） */
        PolicyProfile policy
) {

    public static SkillContext of(String tenantId) {
        return new SkillContext(tenantId, null, null);
    }

    public SkillContext withPolicy(PolicyProfile policy) {
        return new SkillContext(tenantId, operatorId, policy);
    }
}

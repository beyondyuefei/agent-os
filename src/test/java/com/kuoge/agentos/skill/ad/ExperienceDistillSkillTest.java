package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 经验沉淀审核门禁测试（验收规则 9）：
 * 未审核规则不可被任何 Skill 读为生效配置；KA 线候选自动入库但仍需审核才生效。
 */
class ExperienceDistillSkillTest {

    private AdFactStore factStore;

    @BeforeEach
    void setUp() {
        factStore = new InMemoryAdFactStore();
    }

    @Test
    void kaCandidateSavedButNotEffectiveUntilApproved() {
        ExperienceDistillSkill kaSkill = new ExperienceDistillSkill(true, factStore);
        SkillContext ctx = SkillContext.of(CapabilityTestFixtures.TENANT_KA);

        SkillResult<AdFactStore.Experience> result = kaSkill.run(ctx, "高毛利品前置后 CTR 提升 30%");

        assertFalse(result.output().approved(), "候选默认未审核");
        assertEquals(1, factStore.allExperiences().size(), "KA 线候选自动入库");
        assertTrue(factStore.effectiveExperiences().isEmpty(), "未审核规则不可被读为生效配置");

        // 人工审核通过 → 生效可见
        kaSkill.approve(result.output());
        assertEquals(1, factStore.effectiveExperiences().size());
        assertTrue(factStore.effectiveExperiences().get(0).rule().contains("高毛利品前置"));
    }

    @Test
    void proCandidateNotPersisted() {
        ExperienceDistillSkill proSkill = new ExperienceDistillSkill(false, factStore);
        SkillContext ctx = SkillContext.of(CapabilityTestFixtures.TENANT_PRO);

        SkillResult<AdFactStore.Experience> result = proSkill.run(ctx, "复盘结论");

        assertFalse(result.output().approved());
        assertTrue(factStore.allExperiences().isEmpty(), "PRO 线仅生成候选，人工标注前不入库");
        assertTrue(factStore.effectiveExperiences().isEmpty());
    }
}

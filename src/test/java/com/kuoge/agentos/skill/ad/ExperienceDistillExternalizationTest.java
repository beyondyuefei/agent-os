package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;
import com.kuoge.agentos.skill.md.SkillLibrary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExperienceDistillSkill 文案外部化测试：
 * 措辞来自 md 模板；审核门禁逻辑（approved=false / approve 生效 / PRO 不入库）不变。
 */
class ExperienceDistillExternalizationTest {

    private AdFactStore factStore;
    private SkillLibrary library;

    @BeforeEach
    void setUp() {
        factStore = new AdFactStore();
        library = new SkillLibrary();
    }

    @Test
    void candidateWordingComesFromMdTemplate() {
        ExperienceDistillSkill skill = new ExperienceDistillSkill(true, factStore, library);

        SkillResult<AdFactStore.Experience> result = skill.run(
                SkillContext.of(CapabilityTestFixtures.TENANT_KA), "高毛利品前置后 CTR 提升 30%");

        assertEquals("从复盘提炼的经验规则候选：高毛利品前置后 CTR 提升 30%",
                result.output().rule(), "候选规则应由 RULE_CANDIDATE 模板渲染并注入 {{review}}");
        assertEquals("适用条件待人工标注", result.output().applicableCondition());
        assertFalse(result.output().rule().contains("{{"), "不得残留未渲染占位符");
    }

    @Test
    void approvalGateUnchangedWithExternalizedWording() {
        ExperienceDistillSkill skill = new ExperienceDistillSkill(true, factStore, library);
        SkillResult<AdFactStore.Experience> result = skill.run(
                SkillContext.of(CapabilityTestFixtures.TENANT_KA), "复盘结论");

        assertFalse(result.output().approved(), "候选默认未审核（文案来源不影响门禁）");
        assertTrue(factStore.effectiveExperiences().isEmpty(), "未审核规则不可被读为生效配置");
        skill.approve(result.output());
        assertEquals(1, factStore.effectiveExperiences().size());
    }

    @Test
    void fallsBackToBuiltinWordingWithoutLibrary() {
        ExperienceDistillSkill skill = new ExperienceDistillSkill(true, factStore);   // 无库兼容构造

        SkillResult<AdFactStore.Experience> result = skill.run(
                SkillContext.of(CapabilityTestFixtures.TENANT_KA), "复盘结论");

        assertTrue(result.output().rule().contains("从复盘提炼的经验规则候选"),
                "库缺失时回退内置文案");
        assertEquals("适用条件待人工标注", result.output().applicableCondition());
    }

    @Test
    void proLineStillNotPersisted() {
        ExperienceDistillSkill skill = new ExperienceDistillSkill(false, factStore, library);
        skill.run(SkillContext.of(CapabilityTestFixtures.TENANT_PRO), "复盘结论");
        assertTrue(factStore.allExperiences().isEmpty(), "PRO 档仅生成候选不入库（逻辑不变）");
    }
}

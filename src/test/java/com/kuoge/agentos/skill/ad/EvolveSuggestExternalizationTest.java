package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.AutonomyLevel;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.PolicyProfile;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;
import com.kuoge.agentos.skill.md.SkillLibrary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvolveSuggestSkill 话术外部化测试：
 * 诊断逻辑（阈值比较、规则命中）不变；事实 / 建议 / 实验设计文案来自 md 模板；
 * 库缺失时回退内置文案（降级不失效），与 ReviewDraftSkill 同一模式。
 */
class EvolveSuggestExternalizationTest {

    private CapabilityStore store;
    private SkillLibrary library;

    @BeforeEach
    void setUp() {
        store = CapabilityTestFixtures.storeWithProAndKa();
        library = new SkillLibrary();
    }

    private AttributionMapService.Observation observation(long impressions, long clicks,
                                                          int crossProductOrders) {
        return new AttributionMapService.Observation("di_1", 50000, impressions, clicks,
                0, crossProductOrders, 0, List.of("ad_fact_platform:ad_1"), List.of());
    }

    private SkillContext kaContext() {
        PolicyProfile policy = store.findPolicy("EVOLVE_KA", 5).orElseThrow();
        return SkillContext.of(CapabilityTestFixtures.TENANT_KA).withPolicy(policy);
    }

    @Test
    void wordingComesFromMdTemplates() {
        EvolveSuggestSkill skill = new EvolveSuggestSkill("2.x", library);

        SkillResult<EvolveSuggestSkill.Suggestion> result =
                skill.run(kaContext(), observation(10000, 100, 2));   // ctr=0.01 < 0.02

        // ctr < 阈值 → RULE_CTR_LOW 话术来自模板（含渲染后的阈值变量）
        assertTrue(result.output().suggestions().stream()
                .anyMatch(s -> s.contains("0.02")), "RULE_CTR_LOW 模板变量 {{ctrLow}} 应被渲染");
        // 跨品事实来自 FACT_CROSS_PRODUCT 模板
        assertTrue(result.output().facts().stream()
                .anyMatch(f -> f.contains("跨品成交 2 单")));
        // 实验设计来自 EXPERIMENT_DESIGN 模板
        assertTrue(result.output().experimentDesign().contains("观察窗口 7 天"));
        // 事实段 CTR 描述来自 FACT_CTR 模板
        assertTrue(result.output().facts().stream()
                .anyMatch(f -> f.startsWith("CTR=0.0100（阈值")));
        // 任何输出不得残留未渲染占位符
        assertTrue(result.output().facts().stream().noneMatch(f -> f.contains("{{")));
        assertTrue(result.output().suggestions().stream().noneMatch(s -> s.contains("{{")));
    }

    @Test
    void fallsBackToBuiltinWordingWithoutLibrary() {
        EvolveSuggestSkill skill = new EvolveSuggestSkill("2.x");   // 无库兼容构造

        SkillResult<EvolveSuggestSkill.Suggestion> result =
                skill.run(kaContext(), observation(10000, 100, 2));

        assertTrue(result.output().suggestions().stream()
                .anyMatch(s -> s.contains("建议更换素材")), "库缺失时回退内置话术");
        assertNotNull(result.output().experimentDesign());
        assertTrue(result.output().ruleRefs().contains("RULE_CTR_LOW"),
                "话术来源不影响规则命中逻辑");
    }

    @Test
    void ruleLogicUnchangedRegardlessOfWordingSource() {
        // 同一 Observation，两个实例（有库 / 无库）命中的规则集必须一致
        EvolveSuggestSkill withLib = new EvolveSuggestSkill("1.x", library);
        EvolveSuggestSkill withoutLib = new EvolveSuggestSkill("1.x");
        PolicyProfile policy = store.findPolicy("EVOLVE_PRO", 3).orElseThrow();
        SkillContext ctx = SkillContext.of(CapabilityTestFixtures.TENANT_PRO).withPolicy(policy);
        AttributionMapService.Observation obs = observation(10000, 100, 0);

        assertEquals(withLib.run(ctx, obs).output().ruleRefs(),
                withoutLib.run(ctx, obs).output().ruleRefs(),
                "逻辑留代码、话术进 md：规则命中结果与话术来源无关");
    }
}

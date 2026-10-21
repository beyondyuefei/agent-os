package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.PolicyProfile;
import com.kuoge.agentos.skill.RiskLevel;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillDefinition;
import com.kuoge.agentos.skill.SkillRegistry;
import com.kuoge.agentos.skill.SkillResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 效果进化建议双版本线路由测试（验收规则 9 / 10）：
 * 同一能力 ad.effect.evolve，按套餐绑定解析到不同 skill 版本线；
 * 差异全部落在 policy_profile 上 —— PRO=SUGGEST_ONLY 只出建议卡片，
 * KA=AUTO_LOW_RISK 出实验设计且动作路由到 OPS_CONTROL。
 */
class EvolveSuggestRoutingTest {

    private CapabilityStore store;
    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        store = CapabilityTestFixtures.storeWithProAndKa();
        registry = new SkillRegistry();
        registry.register(new SkillDefinition("ad-evolve-suggest", "1.x",
                "ai-skill", RiskLevel.WRITE_LOW_RISK, "PLAN_PRO"), new EvolveSuggestSkill("1.x"));
        registry.register(new SkillDefinition("ad-evolve-suggest", "2.x",
                "ai-skill", RiskLevel.WRITE_HIGH_RISK, "PLAN_KA"), new EvolveSuggestSkill("2.x"));
    }

    private AttributionMapService.Observation observation(long impressions, long clicks,
                                                          int crossProductOrders) {
        return new AttributionMapService.Observation("di_1", 50000, impressions, clicks,
                0, crossProductOrders, 0, List.of("ad_fact_platform:mock_ad_1"), List.of());
    }

    @SuppressWarnings("unchecked")
    private EvolveSuggestSkill resolveByBinding(String planSkillVersion) {
        return (EvolveSuggestSkill) registry.resolve("ad-evolve-suggest", planSkillVersion)
                .orElseThrow();
    }

    @Test
    void proLineSuggestOnlyCardNoExperiment() {
        // PRO 套餐绑定 1.x 版本线 + EVOLVE_PRO 策略
        EvolveSuggestSkill skill = resolveByBinding("1.x");
        PolicyProfile policy = store.findPolicy("EVOLVE_PRO", 3).orElseThrow();
        SkillContext ctx = SkillContext.of(CapabilityTestFixtures.TENANT_PRO).withPolicy(policy);

        SkillResult<EvolveSuggestSkill.Suggestion> result = skill.run(ctx, observation(10000, 500, 0));

        assertEquals("NONE", result.output().actionRoute());
        assertNull(result.output().experimentDesign(), "PRO 档不输出实验设计");
        assertFalse(result.output().suggestions().isEmpty());
        assertFalse(result.evidenceRefs().isEmpty(), "结论必须挂证据指针");
    }

    @Test
    void kaLineAutoLowRiskRoutesToOpsControlWithExperiment() {
        // KA 套餐绑定 2.x 版本线 + EVOLVE_KA 策略
        EvolveSuggestSkill skill = resolveByBinding("2.x");
        PolicyProfile policy = store.findPolicy("EVOLVE_KA", 5).orElseThrow();
        SkillContext ctx = SkillContext.of(CapabilityTestFixtures.TENANT_KA).withPolicy(policy);

        SkillResult<EvolveSuggestSkill.Suggestion> result = skill.run(ctx, observation(10000, 500, 3));

        assertEquals("OPS_CONTROL", result.output().actionRoute());
        assertNotNull(result.output().experimentDesign(), "KA 档输出实验设计");
        assertTrue(result.output().facts().stream().anyMatch(f -> f.contains("跨品")),
                "KA 档多维归因应包含跨品事实");
        assertTrue(result.output().ruleRefs().contains("RULE_CROSS_PRODUCT"));
    }

    @Test
    void ctrBelowThresholdEmitsRuleRef() {
        EvolveSuggestSkill skill = resolveByBinding("1.x");
        PolicyProfile policy = store.findPolicy("EVOLVE_PRO", 3).orElseThrow();
        SkillContext ctx = SkillContext.of(CapabilityTestFixtures.TENANT_PRO).withPolicy(policy);

        // ctr = 100/10000 = 0.01 < 阈值 0.02
        SkillResult<EvolveSuggestSkill.Suggestion> result = skill.run(ctx, observation(10000, 100, 0));

        assertTrue(result.output().ruleRefs().contains("RULE_CTR_LOW"));
        assertTrue(result.output().suggestions().stream().anyMatch(s -> s.contains("CTR")));
    }

    @Test
    void missingPolicyRejected() {
        EvolveSuggestSkill skill = resolveByBinding("2.x");
        assertThrows(IllegalStateException.class,
                () -> skill.run(SkillContext.of(CapabilityTestFixtures.TENANT_KA), observation(1, 1, 0)),
                "未经网关注入策略直接调用 Skill 必须被拒绝");
    }
}

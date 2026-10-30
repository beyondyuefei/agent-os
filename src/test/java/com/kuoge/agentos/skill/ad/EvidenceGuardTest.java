package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.infrastructure.MockLlmClient;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvidenceGuard 证据指针校验测试（方案「幻觉校验」，验收口径：无指针结论比例为零）：
 * 有证据放行、无证据打回重生成 / 降级、空证据集全违规、假设段不校验。
 */
class EvidenceGuardTest {

    private final EvidenceGuard guard = new EvidenceGuard();

    private AttributionMapService.Observation observation(List<String> evidenceRefs) {
        return new AttributionMapService.Observation("di_1", 50000, 10000, 500,
                3, 1, 1, evidenceRefs, List.of());
    }

    @Test
    void factLinesWithEvidencePass() {
        String draft = """
                【事实】周期内花费 50000 分（证据: ad_fact_platform:ad_1@2026W43）。
                【事实】曝光 10000，点击 500（证据: ad_fact_platform:ad_1@2026W43）。
                【限制】数据范围以快照为准。
                【假设】转化表现需更长窗口确认。
                【建议】维持当前投放结构。
                """;

        EvidenceGuard.Check check = guard.check(draft, List.of("ad_fact_platform:ad_1@2026W43"));

        assertTrue(check.pass());
        assertTrue(check.violations().isEmpty());
    }

    @Test
    void factLineWithoutEvidenceFlagged() {
        String draft = """
                【事实】周期内花费 50000 分（证据: ad_fact_platform:ad_1@2026W43）。
                本期转化率表现优异，远超行业均值。
                【限制】数据范围以快照为准。
                """;

        EvidenceGuard.Check check = guard.check(draft, List.of("ad_fact_platform:ad_1@2026W43"));

        assertFalse(check.pass());
        assertEquals(List.of("本期转化率表现优异，远超行业均值。"), check.violations(),
                "violations 必须列出无证据的事实行原文");
    }

    @Test
    void emptyEvidenceRefsFlagEveryFactLine() {
        String draft = """
                【事实】周期内花费 50000 分。
                曝光 10000。
                【限制】无。
                """;

        EvidenceGuard.Check check = guard.check(draft, List.of());

        assertFalse(check.pass());
        assertEquals(2, check.violations().size(), "空证据集时事实段任何非空行都算违规");
    }

    @Test
    void assumptionSectionAndTaggedLinesNotChecked() {
        String draft = """
                【事实】周期内花费 50000 分（证据: ad_fact_platform:ad_1@2026W43）。
                【限制】数据范围以快照为准，无毛利口径。
                【假设】排序结构可能导致跨品购买，待 A/B 验证。
                另一个无证据推断，仍在假设段。
                【建议】先做小流量实验。
                """;
        // 事实段内含"假设"标注的行同样跳过校验
        String taggedFact = """
                【事实】假设：转化率可能受季节性影响（无证据，待验证）。
                """;

        assertTrue(guard.check(draft, List.of("ad_fact_platform:ad_1@2026W43")).pass(),
                "假设 / 限制 / 建议段不校验");
        assertTrue(guard.check(taggedFact, List.of()).pass(),
                "事实段内含\"假设\"标注的行不算违规");
    }

    @Test
    void llmDraftWithoutEvidenceRetriedWithConstraint() {
        AtomicReference<String> retryPrompt = new AtomicReference<>();
        LlmClient llm = new MockLlmClient(messages -> {
            String prompt = messages.get(messages.size() - 1).extractText();
            if (prompt.contains(ReviewDraftSkill.EVIDENCE_CONSTRAINT_MARKER)) {
                retryPrompt.set(prompt);
                return LlmResponse.text("""
                        【事实】周期内花费 50000 分（证据: ad_fact_platform:ad_1@2026W43）。
                        【限制】数据范围以快照为准。
                        【假设】转化表现需更长窗口确认。
                        【建议】维持当前投放结构。
                        """);
            }
            return LlmResponse.text("""
                    【事实】本期表现很好，花费合理。
                    【限制】无。
                    【假设】无。
                    【建议】继续投放。
                    """);
        });
        ReviewDraftSkill skill = new ReviewDraftSkill(llm);
        AttributionMapService.Observation obs = observation(List.of("ad_fact_platform:ad_1@2026W43"));

        SkillResult<String> result = skill.run(SkillContext.of("t1"), obs);

        assertNotNull(retryPrompt.get(), "无证据结论必须打回重生成一次");
        assertTrue(retryPrompt.get().contains("ad_fact_platform:ad_1@2026W43"),
                "重生成 prompt 必须附证据清单强约束");
        assertFalse(result.assumption());
        assertTrue(guard.check(result.output(), obs.evidenceRefs()).pass());
        assertEquals(obs.evidenceRefs(), result.evidenceRefs());
    }

    @Test
    void retryStillFailsDegradesToLimitationDraft() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient llm = new MockLlmClient(messages -> {
            calls.incrementAndGet();
            return LlmResponse.text("""
                    【事实】本期转化率远超行业均值。
                    【限制】无。
                    【假设】无。
                    【建议】加大预算。
                    """);
        });
        ReviewDraftSkill skill = new ReviewDraftSkill(llm);
        AttributionMapService.Observation obs = observation(List.of("ad_fact_platform:ad_1@2026W43"));

        SkillResult<String> result = skill.run(SkillContext.of("t1"), obs);

        assertEquals(2, calls.get(), "最多打回重生成一次");
        assertTrue(result.assumption(), "重生成仍不过 → 降级输出限制说明");
        assertTrue(result.output().contains("证据不足"));
        assertTrue(result.output().contains("【限制】"), "降级草稿是限制说明而非编造结论");
        assertFalse(result.output().contains("远超行业均值"), "降级草稿不得保留被驳回的编造结论");
        assertTrue(guard.check(result.output(), obs.evidenceRefs()).pass(),
                "降级草稿自身必须过 guard（无指针结论比例为零）");
    }

    @Test
    void templateDraftPassesGuardWithEvidence() {
        ReviewDraftSkill skill = new ReviewDraftSkill(null);
        AttributionMapService.Observation obs = observation(List.of("ad_fact_platform:ad_1@2026W43"));

        SkillResult<String> result = skill.run(SkillContext.of("t1"), obs);

        assertFalse(result.assumption());
        assertTrue(guard.check(result.output(), obs.evidenceRefs()).pass(),
                "内置模板草稿事实段附证据标注，必须与 LLM 路径同口径");
    }

    @Test
    void templateDraftWithEmptyEvidenceDegrades() {
        ReviewDraftSkill skill = new ReviewDraftSkill(null);

        SkillResult<String> result = skill.run(SkillContext.of("t1"), observation(List.of()));

        assertTrue(result.assumption(), "空证据快照 → 模板路径同样降级为限制说明");
        assertTrue(result.output().contains("证据不足"));
        assertTrue(guard.check(result.output(), List.of()).pass());
    }

    @Test
    void markdownSectionHeadersAreChecked() {
        // 真实 K3 冒烟输出形态：markdown 标题而非【】标记，不得绕过校验
        String draft = """
                # 四段式复盘草稿

                ## 一、事实

                1. 本周期投放花费为 0 分。［证据：spend = 0］

                ## 二、限制

                1. 快照未提供预算设置。
                """;

        EvidenceGuard.Check check = guard.check(draft, List.of("ad_fact_platform:ad_1@2026W43"));

        assertFalse(check.pass(), "markdown 标题的事实段同样受证据指针校验");
        assertEquals(1, check.violations().size());
    }

    @Test
    void markdownSectionWithProperEvidencePasses() {
        String draft = """
                ## 事实
                - 周期内花费 50000 分（证据: ad_fact_platform:ad_1@2026W43）
                ## 假设
                - 转化表现需更长窗口确认
                """;

        assertTrue(guard.check(draft, List.of("ad_fact_platform:ad_1@2026W43")).pass());
    }

    @Test
    void unstructuredDraftTreatedAsFactSection() {
        // 识别不出任何段结构 → 最严口径：全部按事实段校验，防止换格式绕过
        String draft = "本期转化表现优异，建议加大投放。";

        assertFalse(guard.check(draft, List.of("ad_fact_platform:ad_1@2026W43")).pass());
    }
}

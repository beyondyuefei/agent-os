package com.kuoge.agentos.skill.ad.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.capability.PolicyProfile;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.infrastructure.MockLlmClient;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;
import com.kuoge.agentos.skill.ad.AttributionMapService;
import com.kuoge.agentos.skill.ad.EvidenceGuard;
import com.kuoge.agentos.skill.ad.EvolveSuggestSkill;
import com.kuoge.agentos.skill.ad.ReviewDraftSkill;
import com.kuoge.agentos.skill.md.SkillLibrary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 最小评测集（方案 3.5 首批：常规 / 回归 / BadCase）——只断言规则层硬指标。
 * 数据集：src/test/resources/eval/ad-review-draft/*.jsonl、eval/ad-evolve-suggest/*.jsonl，
 * 逐条用 MockLlmClient 跑 ReviewDraftSkill / EvolveSuggestSkill，保证 CI 确定性。
 * 硬指标：
 * <ul>
 *   <li>复盘草稿四段式结构齐备（【事实】【限制】【假设】【建议】）</li>
 *   <li>事实段过 EvidenceGuard（验收口径：无指针结论比例为零）</li>
 *   <li>无证据场景输出降级限制说明，而非编造结论</li>
 *   <li>evolve 建议可追溯到规则编号（suggestions 非空则 ruleRefs 非空）</li>
 *   <li>PRO / KA 版本线输出差异（KA 有 experimentDesign，PRO 没有）</li>
 * </ul>
 * 模型层 Judge 体系（位置随机化 / 稳定性选择 / Golden Set）不在本期。
 */
class AdSkillEvalTest {

    /** Jackson 由 spring-boot-starter-web 传递引入，测试直接使用，不加新依赖 */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EvidenceGuard GUARD = new EvidenceGuard();
    /** Mock LLM 从 prompt 中回收证据指针，保证产出草稿可过 guard（CI 确定性） */
    private static final Pattern EVIDENCE_TOKEN = Pattern.compile("ad_fact_platform:[A-Za-z0-9_.@-]+");

    private List<JsonNode> load(String resource) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, "评测集缺失: " + resource);
            List<JsonNode> rows = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!line.isBlank()) {
                    rows.add(MAPPER.readTree(line));
                }
            }
            assertFalse(rows.isEmpty(), "评测集不能为空: " + resource);
            return rows;
        }
    }

    private AttributionMapService.Observation toObservation(JsonNode row) {
        List<String> refs = new ArrayList<>();
        row.get("evidenceRefs").forEach(n -> refs.add(n.asText()));
        List<String> gaps = new ArrayList<>();
        if (row.has("gaps")) {
            row.get("gaps").forEach(n -> gaps.add(n.asText()));
        }
        return new AttributionMapService.Observation("eval_" + row.get("name").asText(),
                row.get("spendCents").asLong(), row.get("impressions").asLong(),
                row.get("clicks").asLong(), row.get("attributedOrders").asInt(),
                row.get("crossProductOrders").asInt(), row.get("unknownOrders").asInt(),
                refs, gaps);
    }

    // ---- ad.review.draft ----

    /** Mock LLM：从 prompt 中提取证据指针嵌入四段式草稿（模拟合规模型） */
    private LlmClient compliantDraftLlm() {
        return new MockLlmClient(messages -> {
            String prompt = messages.get(messages.size() - 1).extractText();
            List<String> refs = new ArrayList<>();
            Matcher matcher = EVIDENCE_TOKEN.matcher(prompt);
            while (matcher.find()) {
                if (!refs.contains(matcher.group())) {
                    refs.add(matcher.group());
                }
            }
            String tag = refs.isEmpty() ? "" : "（证据: " + String.join(", ", refs) + "）";
            return LlmResponse.text("""
                    【事实】周期数据来自平台回流快照%s。
                    【限制】数据范围以 Observation 快照为准，缺毛利 / 退款口径。
                    【假设】转化表现需结合更长观察窗口确认。
                    【建议】维持当前投放结构，待样本充足后再调整。
                    """.formatted(tag));
        });
    }

    private void assertReviewDraftCase(JsonNode row) {
        AttributionMapService.Observation obs = toObservation(row);
        boolean expectDegraded = row.get("expect").get("degraded").asBoolean();
        String mode = row.has("mode") ? row.get("mode").asText() : "llm";
        String name = row.get("name").asText();
        ReviewDraftSkill skill = switch (mode) {
            // 回归集：模板 / Prompt 变更兜底路径
            case "template" -> new ReviewDraftSkill(null);                       // 无 LLM → 内置模板草稿
            case "fallbackPrompt" -> new ReviewDraftSkill(compliantDraftLlm());  // 无 SkillLibrary → 内置提示词
            default -> new ReviewDraftSkill(compliantDraftLlm(), new SkillLibrary());
        };

        SkillResult<String> result = skill.run(SkillContext.of("eval_tenant"), obs);
        String draft = result.output();

        for (String marker : List.of("【事实】", "【限制】", "【假设】", "【建议】")) {
            assertTrue(draft.contains(marker), name + "：四段式结构缺段落 " + marker);
        }
        assertTrue(GUARD.check(draft, obs.evidenceRefs()).pass(),
                name + "：事实段必须过 EvidenceGuard（无指针结论比例为零）");
        assertEquals(expectDegraded, result.assumption(), name + "：降级标记与预期不符");
        if (expectDegraded) {
            assertTrue(draft.contains("证据不足"),
                    name + "：无证据场景必须输出降级限制说明而非编造结论");
        }
    }

    @Test
    void reviewDraftRegular() throws IOException {
        for (JsonNode row : load("/eval/ad-review-draft/regular.jsonl")) {
            assertReviewDraftCase(row);
        }
    }

    @Test
    void reviewDraftRegression() throws IOException {
        for (JsonNode row : load("/eval/ad-review-draft/regression.jsonl")) {
            assertReviewDraftCase(row);
        }
    }

    @Test
    void reviewDraftBadcase() throws IOException {
        for (JsonNode row : load("/eval/ad-review-draft/badcase.jsonl")) {
            assertReviewDraftCase(row);
        }
    }

    // ---- ad.evolve.suggest ----

    private void assertEvolveCase(JsonNode row) {
        String name = row.get("name").asText();
        AttributionMapService.Observation obs = toObservation(row);
        CapabilityStore store = CapabilityTestFixtures.storeWithProAndKa();
        PolicyProfile policy = store
                .findPolicy(row.get("policyCode").asText(), row.get("policyVersion").asInt())
                .orElseThrow();
        String versionLine = row.get("versionLine").asText();
        String tenant = versionLine.startsWith("2")
                ? CapabilityTestFixtures.TENANT_KA : CapabilityTestFixtures.TENANT_PRO;
        SkillContext ctx = SkillContext.of(tenant).withPolicy(policy);

        EvolveSuggestSkill.Suggestion out =
                new EvolveSuggestSkill(versionLine, new SkillLibrary()).run(ctx, obs).output();

        JsonNode expect = row.get("expect");
        List<String> expectedRules = new ArrayList<>();
        expect.get("ruleRefs").forEach(n -> expectedRules.add(n.asText()));
        assertEquals(expectedRules, out.ruleRefs(), name + "：规则命中集与预期不符");
        // 建议可追溯到规则编号
        if (!out.suggestions().isEmpty()) {
            assertFalse(out.ruleRefs().isEmpty(), name + "：suggestions 非空则 ruleRefs 必须非空");
        }
        if (expect.get("suggestionsEmpty").asBoolean()) {
            assertTrue(out.suggestions().isEmpty(), name + "：无异常场景不应产出建议");
        }
        // PRO / KA 版本线输出差异
        assertEquals(expect.get("experimentDesign").asBoolean(), out.experimentDesign() != null,
                name + "：KA 有 experimentDesign，PRO 没有");
        // facts 证据指针映射（PostReasoning 校验）
        if (expect.has("factsEmpty") && expect.get("factsEmpty").asBoolean()) {
            assertTrue(out.facts().isEmpty(), name + "：空证据时事实行必须移入 limits");
            assertTrue(out.limits().stream().anyMatch(l -> l.contains("CTR")),
                    name + "：被移入的事实行应出现在 limits");
        } else {
            for (String fact : out.facts()) {
                assertTrue(obs.evidenceRefs().stream().anyMatch(fact::contains),
                        name + "：facts 行必须能映射到 evidenceRefs —— " + fact);
            }
        }
    }

    @Test
    void evolveSuggestRegular() throws IOException {
        for (JsonNode row : load("/eval/ad-evolve-suggest/regular.jsonl")) {
            assertEvolveCase(row);
        }
    }

    @Test
    void evolveSuggestBadcase() throws IOException {
        for (JsonNode row : load("/eval/ad-evolve-suggest/badcase.jsonl")) {
            assertEvolveCase(row);
        }
    }
}

package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;
import com.kuoge.agentos.skill.md.SkillLibrary;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReviewDraftSkill 提示词外部化测试：
 * 接入 SkillLibrary 后提示词来自 md 模板；库缺失时回退内置模板（降级不失效）。
 */
class ReviewDraftSkillTest {

    private AttributionMapService.Observation observation() {
        return new AttributionMapService.Observation("di_1", 50000, 10000, 500,
                3, 1, 1, List.of("ad_fact_platform:ad_1"), List.of());
    }

    /** 捕获实际发给模型的提示词，返回固定草稿 */
    private LlmClient capturing(AtomicReference<String> captured) {
        return (systemPrompt, messages) -> {
            captured.set(messages.get(0).extractText());
            return LlmResponse.text("草稿");
        };
    }

    @Test
    void promptComesFromExternalizedTemplate() {
        AtomicReference<String> captured = new AtomicReference<>();
        ReviewDraftSkill skill = new ReviewDraftSkill(capturing(captured), new SkillLibrary());

        SkillResult<String> result = skill.run(SkillContext.of("t1"), observation());

        assertEquals("草稿", result.output());
        String prompt = captured.get();
        assertTrue(prompt.contains("spend = 50000"), "提示词应由 md 模板渲染并注入快照变量");
        assertTrue(prompt.contains("证据指针：ad_fact_platform:ad_1"));
        assertTrue(prompt.contains("四段式"), "references 中的格式规则应按需并入提示词");
        assertFalse(prompt.contains("{{"), "占位符必须全部渲染，不得带空洞进模型");
    }

    @Test
    void fallsBackToBuiltinTemplateWhenLibraryMissing() {
        AtomicReference<String> captured = new AtomicReference<>();
        ReviewDraftSkill skill = new ReviewDraftSkill(capturing(captured));   // 无 SkillLibrary 的兼容构造

        skill.run(SkillContext.of("t1"), observation());

        assertTrue(captured.get().contains("spend=50000"), "库缺失时回退内置模板");
    }

    @Test
    void nullLlmClientProducesTemplateDraft() {
        ReviewDraftSkill skill = new ReviewDraftSkill(null, new SkillLibrary());
        SkillResult<String> result = skill.run(SkillContext.of("t1"), observation());
        assertTrue(result.output().contains("【事实】"), "无 LLM 客户端时产模板草稿");
        assertFalse(result.assumption());
    }
}

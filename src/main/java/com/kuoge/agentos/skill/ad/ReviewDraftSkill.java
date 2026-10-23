package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.skill.AiSkill;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;
import com.kuoge.agentos.skill.md.SkillLibrary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ad.review.draft 复盘草稿生成（方案 3.4，AI Skill，不写业务状态只出草稿）。
 * 分岗位草稿（投流商品 / 美工表达 / 运营排序），固定四段式：事实 / 限制 / 假设 / 建议。
 * 每个结论挂证据指针；事实、假设、规则三层分开。
 *
 * <p>提示词外部化（SKILL.md 规范）：模板位于 resources/skills/ad-review-draft/SKILL.md，
 * 经 {@link SkillLibrary} 三级渐进加载；库中无此模板时回退内置模板（降级不失效）。
 */
public class ReviewDraftSkill implements AiSkill<AttributionMapService.Observation, String> {

    /** 库中模板名（与 skills/ 目录下文件夹名一致） */
    static final String SKILL_NAME = "ad-review-draft";
    /** 四段式格式规则模板键（contract.yaml templates.FORMAT，按需并入提示词） */
    static final String FORMAT_REFERENCE = "FORMAT";

    private final LlmClient llmClient;
    private final SkillLibrary skillLibrary;

    public ReviewDraftSkill(LlmClient llmClient, SkillLibrary skillLibrary) {
        this.llmClient = llmClient;
        this.skillLibrary = skillLibrary;
    }

    /** 兼容构造：无 SkillLibrary 时使用内置模板 */
    public ReviewDraftSkill(LlmClient llmClient) {
        this(llmClient, null);
    }

    @Override
    public SkillResult<String> run(SkillContext ctx, AttributionMapService.Observation obs) {
        String prompt = buildPrompt(obs);
        String draft = llmClient == null
                ? templateDraft(obs)
                : llmClient.call("投流复盘助手",
                        List.of(com.kuoge.agentos.core.session.ConversationMessage.userText(prompt)))
                        .extractText();
        return SkillResult.evidence(draft, obs.evidenceRefs());
    }

    /** 优先用外部化模板渲染；库不可用或模板缺失时回退内置模板 */
    private String buildPrompt(AttributionMapService.Observation obs) {
        Map<String, Object> vars = promptVars(obs);
        if (skillLibrary != null && skillLibrary.contains(SKILL_NAME)) {
            StringBuilder prompt = new StringBuilder(skillLibrary.render(SKILL_NAME, "BODY", vars));
            if (skillLibrary.contract(SKILL_NAME).templates().containsKey(FORMAT_REFERENCE)) {
                // 格式规则模板按需并入提示词
                prompt.append("\n\n").append(skillLibrary.content(SKILL_NAME, FORMAT_REFERENCE));
            }
            return prompt.toString();
        }
        return fallbackPrompt(obs);
    }

    private Map<String, Object> promptVars(AttributionMapService.Observation obs) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("spendCents", obs.spendCents());
        vars.put("impressions", obs.impressions());
        vars.put("clicks", obs.clicks());
        vars.put("attributedOrders", obs.attributedOrders());
        vars.put("crossProductOrders", obs.crossProductOrders());
        vars.put("unknownOrders", obs.unknownOrders());
        vars.put("evidenceRefs", String.join(", ", obs.evidenceRefs()));
        return vars;
    }

    private String fallbackPrompt(AttributionMapService.Observation obs) {
        return """
                你是投流复盘助手。基于以下 Observation 快照生成四段式复盘草稿（事实 / 限制 / 假设 / 建议）。
                要求：每个结论挂证据指针；无证据的结论必须标注"假设"。
                快照：spend=%d, impressions=%d, clicks=%d, attributedOrders=%d, crossProductOrders=%d, unknownOrders=%d
                证据：%s
                """.formatted(obs.spendCents(), obs.impressions(), obs.clicks(),
                obs.attributedOrders(), obs.crossProductOrders(), obs.unknownOrders(),
                String.join(", ", obs.evidenceRefs()));
    }

    private String templateDraft(AttributionMapService.Observation obs) {
        return """
                【事实】周期内花费 %d 分，曝光 %d，点击 %d；归因成交 %d 单（跨品 %d 单，关联不明 %d 单）。
                【限制】数据范围以 Observation 快照为准，未包含毛利 / 库存 / 退款口径。
                【假设】点击转化表现需结合更长观察窗口确认。
                【建议】下一周期维持当前投放结构，待跨品归因样本充足后再调整排序。
                """.formatted(obs.spendCents(), obs.impressions(), obs.clicks(),
                obs.attributedOrders(), obs.crossProductOrders(), obs.unknownOrders());
    }
}

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
 *
 * <p>PostReasoning 证据指针校验（方案「幻觉校验」）：LLM 产出草稿后过 {@link EvidenceGuard}，
 * 不通过则打回重生成一次（prompt 尾部追加证据清单强约束）；重生成仍不过则降级输出
 * 「限制说明」草稿（方案降级行为：证据不足 → 输出限制说明，不编造结论）。
 * 内置 templateDraft 路径同样过 guard，保证两条产出路径口径统一。
 */
public class ReviewDraftSkill implements AiSkill<AttributionMapService.Observation, String> {

    /** 库中模板名（与 skills/ 目录下文件夹名一致） */
    static final String SKILL_NAME = "ad-review-draft";
    /** 四段式格式规则模板键（contract.yaml templates.FORMAT，按需并入提示词） */
    static final String FORMAT_REFERENCE = "FORMAT";
    /** 打回重生成时追加的证据强约束标记（评测 / 测试据此识别重生成 prompt） */
    static final String EVIDENCE_CONSTRAINT_MARKER = "证据强约束";

    private final LlmClient llmClient;
    private final SkillLibrary skillLibrary;
    private final EvidenceGuard evidenceGuard;

    public ReviewDraftSkill(LlmClient llmClient, SkillLibrary skillLibrary, EvidenceGuard evidenceGuard) {
        this.llmClient = llmClient;
        this.skillLibrary = skillLibrary;
        this.evidenceGuard = evidenceGuard == null ? new EvidenceGuard() : evidenceGuard;
    }

    public ReviewDraftSkill(LlmClient llmClient, SkillLibrary skillLibrary) {
        this(llmClient, skillLibrary, new EvidenceGuard());
    }

    /** 兼容构造：无 SkillLibrary 时使用内置模板 */
    public ReviewDraftSkill(LlmClient llmClient) {
        this(llmClient, null);
    }

    @Override
    public SkillResult<String> run(SkillContext ctx, AttributionMapService.Observation obs) {
        if (llmClient == null) {
            // 内置模板草稿由快照直出、事实段附证据标注；同样过 guard 保证口径统一
            String draft = templateDraft(obs);
            return verdict(draft, obs, evidenceGuard.check(draft, obs.evidenceRefs()));
        }
        String draft = callLlm(buildPrompt(obs));
        EvidenceGuard.Check check = evidenceGuard.check(draft, obs.evidenceRefs());
        if (!check.pass()) {
            // 无证据即打回：重生成一次，prompt 尾部追加证据清单强约束
            draft = callLlm(buildPrompt(obs) + evidenceConstraint(obs));
            check = evidenceGuard.check(draft, obs.evidenceRefs());
        }
        return verdict(draft, obs, check);
    }

    private String callLlm(String prompt) {
        return llmClient.call("投流复盘助手",
                        List.of(com.kuoge.agentos.core.session.ConversationMessage.userText(prompt)))
                .extractText();
    }

    /** 校验通过 → 挂证据返回；仍不通过 → 降级为限制说明草稿，不编造结论 */
    private SkillResult<String> verdict(String draft, AttributionMapService.Observation obs,
                                        EvidenceGuard.Check check) {
        if (check.pass()) {
            return SkillResult.evidence(draft, obs.evidenceRefs());
        }
        return SkillResult.assumption(limitationDraft(obs, check.violations()),
                "证据不足，复盘草稿降级为限制说明");
    }

    /** 打回重生成的证据清单强约束（追加在 prompt 尾部） */
    private String evidenceConstraint(AttributionMapService.Observation obs) {
        return "\n\n" + EVIDENCE_CONSTRAINT_MARKER + "：以下证据指针必须在事实段逐字引用"
                + "（每条事实结论至少挂一个）：" + String.join(", ", obs.evidenceRefs())
                + "；无证据支撑的内容只能写入【假设】段并标注\"假设\"。";
    }

    /**
     * 降级草稿（方案：证据不足 → 输出限制说明，不编造结论）。
     * 事实段留空（保证无指针结论比例为零），缺口与被驳回结论全部进限制段；
     * 产出自身过 EvidenceGuard 必须干净。
     */
    private String limitationDraft(AttributionMapService.Observation obs, List<String> violations) {
        StringBuilder draft = new StringBuilder();
        draft.append("【事实】\n");
        draft.append("【限制】证据不足，本次复盘仅输出限制说明，不输出事实性结论。");
        if (obs.evidenceRefs().isEmpty()) {
            draft.append("当前快照无任何证据指针（数据回流未产生平台事实）。");
        }
        if (!violations.isEmpty()) {
            // 只记条数不回引原文：被驳回结论属编造内容，不得残留在降级输出里
            draft.append("模型产出中 ").append(violations.size()).append(" 条结论无证据指针，已驳回。");
        }
        if (!obs.gaps().isEmpty()) {
            draft.append("数据缺口：").append(String.join(", ", obs.gaps())).append("。");
        }
        draft.append("\n");
        draft.append("【假设】无（证据不足时不做推断）。\n");
        draft.append("【建议】补齐数据回流与归因映射后重新生成复盘草稿。\n");
        return draft.toString();
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
        // 事实段由快照直出并附证据指针标注（EvidenceGuard 口径：事实结论必须挂证据指针）
        String evidenceTag = obs.evidenceRefs().isEmpty()
                ? "" : "（证据: " + String.join(", ", obs.evidenceRefs()) + "）";
        return """
                【事实】周期内花费 %d 分，曝光 %d，点击 %d；归因成交 %d 单（跨品 %d 单，关联不明 %d 单）。%s
                【限制】数据范围以 Observation 快照为准，未包含毛利 / 库存 / 退款口径。
                【假设】点击转化表现需结合更长观察窗口确认。
                【建议】下一周期维持当前投放结构，待跨品归因样本充足后再调整排序。
                """.formatted(obs.spendCents(), obs.impressions(), obs.clicks(),
                obs.attributedOrders(), obs.crossProductOrders(), obs.unknownOrders(), evidenceTag);
    }
}

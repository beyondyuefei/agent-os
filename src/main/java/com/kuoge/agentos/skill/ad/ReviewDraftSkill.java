package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.skill.AiSkill;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;

import java.util.List;

/**
 * ad.review.draft 复盘草稿生成（方案 3.4，AI Skill，不写业务状态只出草稿）。
 * 分岗位草稿（投流商品 / 美工表达 / 运营排序），固定四段式：事实 / 限制 / 假设 / 建议。
 * 每个结论挂证据指针；事实、假设、规则三层分开。
 */
public class ReviewDraftSkill implements AiSkill<AttributionMapService.Observation, String> {

    private final LlmClient llmClient;

    public ReviewDraftSkill(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public SkillResult<String> run(SkillContext ctx, AttributionMapService.Observation obs) {
        String prompt = """
                你是投流复盘助手。基于以下 Observation 快照生成四段式复盘草稿（事实 / 限制 / 假设 / 建议）。
                要求：每个结论挂证据指针；无证据的结论必须标注"假设"。
                快照：spend=%d, impressions=%d, clicks=%d, attributedOrders=%d, crossProductOrders=%d, unknownOrders=%d
                证据：%s
                """.formatted(obs.spendCents(), obs.impressions(), obs.clicks(),
                obs.attributedOrders(), obs.crossProductOrders(), obs.unknownOrders(),
                String.join(", ", obs.evidenceRefs()));
        String draft = llmClient == null
                ? templateDraft(obs)
                : llmClient.call("投流复盘助手",
                        java.util.List.of(com.kuoge.agentos.core.session.ConversationMessage.userText(prompt)))
                        .extractText();
        return SkillResult.evidence(draft, obs.evidenceRefs());
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

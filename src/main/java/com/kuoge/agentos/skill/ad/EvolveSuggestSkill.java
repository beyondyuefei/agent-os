package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.AutonomyLevel;
import com.kuoge.agentos.capability.PolicyProfile;
import com.kuoge.agentos.skill.AiSkill;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;
import com.kuoge.agentos.skill.md.SkillLibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ad.evolve.suggest 效果进化建议（方案 3.3 ④，AI Skill，PRO/KA 双版本线）。
 * 同一能力、两个档位，差异全部落在 policy_profile 上，由网关注入；
 * 诊断深度、数据范围、执行形态均由策略配置驱动，不加 if 分支判断档位。
 * 输出生效动作按类型路由（启停 → ops.control；内容 / 预算 → 对应服务）。
 *
 * <p>话术外部化：诊断判断逻辑（阈值比较、规则命中）保留在本类；
 * 给人看的事实描述 / 建议话术 / 实验设计文案来自
 * resources/skills/ad-evolve-suggest/references/ 下的 md 模板，运营可直接调整。
 * 库不可用或模板缺失时回退内置文案（降级不失效）。
 */
public class EvolveSuggestSkill implements AiSkill<AttributionMapService.Observation, EvolveSuggestSkill.Suggestion> {

    /** 库中模板名（与 skills/ 目录下文件夹名一致） */
    static final String SKILL_NAME = "ad-evolve-suggest";

    /** 进化建议（PRO：固定四段建议卡片；KA：建议 + 实验设计 + 经验规则候选） */
    public record Suggestion(List<String> facts,
                             List<String> limits,
                             List<String> suggestions,
                             List<String> ruleRefs,
                             /** KA：实验设计 */
                             String experimentDesign,
                             /** 生效动作类型路由：OPS_CONTROL / CONTENT / BUDGET / NONE */
                             String actionRoute) {
        public Suggestion {
            facts = facts == null ? List.of() : List.copyOf(facts);
            limits = limits == null ? List.of() : List.copyOf(limits);
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
            ruleRefs = ruleRefs == null ? List.of() : List.copyOf(ruleRefs);
        }
    }

    /** 版本线标识：1.x = PRO（规则模板诊断），2.x = KA（多维归因） */
    private final String versionLine;
    private final SkillLibrary skillLibrary;

    public EvolveSuggestSkill(String versionLine) {
        this(versionLine, null);
    }

    public EvolveSuggestSkill(String versionLine, SkillLibrary skillLibrary) {
        this.versionLine = versionLine;
        this.skillLibrary = skillLibrary;
    }

    public String versionLine() {
        return versionLine;
    }

    /** 事实行挂证据标注进 facts；快照无证据指针（映射不上）时移入 limits */
    private static void addFact(List<String> facts, List<String> limits, String text, String evidenceTag) {
        if (evidenceTag == null) {
            limits.add(text + "（无证据指针，移入限制）");
        } else {
            facts.add(text + evidenceTag);
        }
    }

    /**
     * 取诊断话术：优先契约库渲染（数据库发布内容 / 契约默认值）；
     * 库不可用 / 模板缺失 / 渲染失败时回退内置文案。
     */
    private String wording(String templateKey, Map<String, ?> vars, String fallback) {
        if (skillLibrary == null || !skillLibrary.contains(SKILL_NAME)) {
            return fallback;
        }
        try {
            return skillLibrary.render(SKILL_NAME, templateKey, vars).trim();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    @Override
    public SkillResult<Suggestion> run(SkillContext ctx, AttributionMapService.Observation obs) {
        PolicyProfile policy = ctx.policy();
        if (policy == null) {
            throw new IllegalStateException("EvolveSuggestSkill requires gateway-injected PolicyProfile");
        }
        List<String> facts = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<String> ruleRefs = new ArrayList<>();
        List<String> limits = new ArrayList<>(List.of("数据范围: " + String.join(", ", policy.dataScope())));

        // PostReasoning 证据指针校验（方案「幻觉校验」）：facts 每行必须能映射到
        // Observation.evidenceRefs —— 以简洁形式在行尾附证据标注；快照无证据指针时
        // 映射不上，事实行移入 limits，不产出无指针结论（验收口径：无指针结论比例为零）
        String evidenceTag = obs.evidenceRefs().isEmpty()
                ? null : "（证据: " + String.join(", ", obs.evidenceRefs()) + "）";

        // 诊断深度由 policy.thresholds 决定（规则模板：CTR < 阈值、点击 N 小时无转化）
        double ctrLow = ((Number) policy.thresholds().getOrDefault("ctr_low", 0.02)).doubleValue();
        double ctr = obs.impressions() == 0 ? 0 : (double) obs.clicks() / obs.impressions();
        String ctrFormatted = String.format("%.4f", ctr);
        addFact(facts, limits, wording("FACT_CTR",
                Map.of("ctr", ctrFormatted, "ctrLow", ctrLow),
                "CTR=" + ctrFormatted + "（阈值 " + ctrLow + "）"), evidenceTag);
        if (obs.impressions() > 0 && ctr < ctrLow) {
            suggestions.add(wording("RULE_CTR_LOW",
                    Map.of("ctrLow", ctrLow),
                    "CTR 低于阈值，建议更换素材或调整人群定向"));
            ruleRefs.add("RULE_CTR_LOW");
        }
        if (obs.clicks() > 0 && obs.attributedOrders() == 0) {
            suggestions.add(wording("RULE_CLICK_NO_CONVERT",
                    Map.of(),
                    "点击无转化，建议检查落地页与商品价格竞争力"));
            ruleRefs.add("RULE_CLICK_NO_CONVERT");
        }

        boolean ka = versionLine.startsWith("2");
        String experimentDesign = null;
        if (ka) {
            // KA 版：多维诊断与原因假设（素材 / 商品 / 排序 / 人群），输出实验设计
            if (obs.crossProductOrders() > 0) {
                addFact(facts, limits, wording("FACT_CROSS_PRODUCT",
                        Map.of("count", obs.crossProductOrders()),
                        "跨品成交 " + obs.crossProductOrders() + " 单：入口商品与购买商品不一致"),
                        evidenceTag);
                suggestions.add(wording("RULE_CROSS_PRODUCT",
                        Map.of(),
                        "假设：排序结构导致跨品购买，建议 A/B 实验验证排序调整"));
                ruleRefs.add("RULE_CROSS_PRODUCT");
            }
            experimentDesign = wording("EXPERIMENT_DESIGN",
                    Map.of(),
                    "A/B: 对照组维持现排序，实验组将高毛利品前置，观察窗口 7 天");
        }

        // 执行形态由 autonomy_level 决定：SUGGEST_ONLY → 建议卡片；AUTO_LOW_RISK → 低风险自动
        String actionRoute = policy.autonomyLevel() == AutonomyLevel.AUTO_LOW_RISK
                ? "OPS_CONTROL" : "NONE";
        Suggestion suggestion = new Suggestion(facts, limits, suggestions, ruleRefs,
                experimentDesign, actionRoute);
        return SkillResult.evidence(suggestion, obs.evidenceRefs());
    }
}

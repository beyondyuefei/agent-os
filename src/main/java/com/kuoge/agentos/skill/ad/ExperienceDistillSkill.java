package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.skill.AiSkill;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;
import com.kuoge.agentos.skill.md.PromptTemplate;
import com.kuoge.agentos.skill.md.SkillLibrary;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ad.experience.distill 经验沉淀（方案 3.4，AI Skill，只写经验候选库）。
 * PRO = 候选级（人工标注）；KA = 审核入库级（候选自动入库，审核后生效）。
 * 铁律：未审核规则不可被任何 Skill 读为生效配置；采用记录可回看。
 *
 * <p>文案外部化：候选规则与适用条件占位的措辞来自
 * resources/skills/ad-experience-distill/references/ 下的 md 模板；
 * 入库门禁逻辑（approved=false、approve 生效、PRO 不入库）保留在本类。
 * 库不可用或模板缺失时回退内置文案（降级不失效）。
 */
public class ExperienceDistillSkill implements AiSkill<String, AdFactStore.Experience> {

    /** 库中模板名（与 skills/ 目录下文件夹名一致） */
    static final String SKILL_NAME = "ad-experience-distill";

    /** PRO=候选级（approved=false）；KA=审核入库级（自动入库但仍需审核才生效） */
    private final boolean kaLine;
    private final AdFactStore factStore;
    private final SkillLibrary skillLibrary;

    public ExperienceDistillSkill(boolean kaLine, AdFactStore factStore) {
        this(kaLine, factStore, null);
    }

    public ExperienceDistillSkill(boolean kaLine, AdFactStore factStore, SkillLibrary skillLibrary) {
        this.kaLine = kaLine;
        this.factStore = factStore;
        this.skillLibrary = skillLibrary;
    }

    @Override
    public SkillResult<AdFactStore.Experience> run(SkillContext ctx, String confirmedReview) {
        AdFactStore.Experience candidate = new AdFactStore.Experience(
                "exp_" + UUID.randomUUID().toString().substring(0, 8),
                wording("RULE_CANDIDATE.md", Map.of("review", confirmedReview),
                        "从复盘提炼的经验规则候选：" + confirmedReview),
                "review:" + ctx.tenantId(),
                wording("CONDITION_PLACEHOLDER.md", Map.of(), "适用条件待人工标注"),
                false);   // 审核前一律不生效
        if (kaLine) {
            // KA：候选自动入库（仍未审核，生效需审核）
            factStore.saveExperience(candidate);
        }
        // PRO：仅生成候选，人工标注后才入库
        return SkillResult.evidence(candidate, List.of(candidate.source()));
    }

    /** 人工审核通过 → 入库生效 */
    public AdFactStore.Experience approve(AdFactStore.Experience candidate) {
        AdFactStore.Experience approved = new AdFactStore.Experience(
                candidate.id(), candidate.rule(), candidate.source(),
                candidate.applicableCondition(), true);
        factStore.saveExperience(approved);
        return approved;
    }

    /** 取文案：优先 md 模板渲染；库不可用 / 模板缺失 / 渲染失败时回退内置文案 */
    private String wording(String reference, Map<String, ?> vars, String fallback) {
        if (skillLibrary == null || !skillLibrary.contains(SKILL_NAME)) {
            return fallback;
        }
        try {
            return PromptTemplate.render(
                    skillLibrary.loadReference(SKILL_NAME, reference), vars).trim();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}

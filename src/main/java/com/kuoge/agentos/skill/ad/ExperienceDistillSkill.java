package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.skill.AiSkill;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.SkillResult;

import java.util.List;
import java.util.UUID;

/**
 * ad.experience.distill 经验沉淀（方案 3.4，AI Skill，只写经验候选库）。
 * PRO = 候选级（人工标注）；KA = 审核入库级（候选自动入库，审核后生效）。
 * 铁律：未审核规则不可被任何 Skill 读为生效配置；采用记录可回看。
 */
public class ExperienceDistillSkill implements AiSkill<String, AdFactStore.Experience> {

    /** PRO=候选级（approved=false）；KA=审核入库级（自动入库但仍需审核才生效） */
    private final boolean kaLine;
    private final AdFactStore factStore;

    public ExperienceDistillSkill(boolean kaLine, AdFactStore factStore) {
        this.kaLine = kaLine;
        this.factStore = factStore;
    }

    @Override
    public SkillResult<AdFactStore.Experience> run(SkillContext ctx, String confirmedReview) {
        AdFactStore.Experience candidate = new AdFactStore.Experience(
                "exp_" + UUID.randomUUID().toString().substring(0, 8),
                "从复盘提炼的经验规则候选：" + confirmedReview,
                "review:" + ctx.tenantId(),
                "适用条件待人工标注",
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
}

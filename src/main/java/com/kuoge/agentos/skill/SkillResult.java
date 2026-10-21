package com.kuoge.agentos.skill;

import java.util.List;

/**
 * AI Skill 输出包装：草稿 / 建议 / 假设，不直接写业务状态。
 *
 * @param evidenceRefs 证据指针（幻觉校验：结论必须挂证据指针，无证据即打回）
 * @param assumption   true = 无充分证据的结论，必须标注"假设"
 */
public record SkillResult<O>(
        O output,
        boolean assumption,
        List<String> evidenceRefs,
        String note
) {

    public SkillResult {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }

    public static <O> SkillResult<O> evidence(O output, List<String> evidenceRefs) {
        return new SkillResult<>(output, false, evidenceRefs, null);
    }

    public static <O> SkillResult<O> assumption(O output, String note) {
        return new SkillResult<>(output, true, List.of(), note);
    }
}

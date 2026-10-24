package com.kuoge.agentos.skill.ad;

import java.util.ArrayList;
import java.util.List;

/**
 * PostReasoning 证据指针校验（方案「幻觉校验」，确定性规则实现，不经 LLM）。
 * AI Skill 直连 LlmClient、不经 ConversationRuntime，故校验落在 skill 层而非运行时层：
 * 四段式复盘草稿（【事实】/【限制】/【假设】/【建议】）中，【事实】段的每条结论
 * 必须引用 Observation.evidenceRefs 里至少一个证据指针；无证据的内容只允许出现在
 * 【假设】段或在行内显式标注"假设"。验收口径：无指针结论比例为零。
 */
public class EvidenceGuard {

    /** 校验结果：pass = 事实段无违规；violations = 无证据的事实行原文 */
    public record Check(boolean pass, List<String> violations) {
        public Check {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }

    /** 事实段标记 */
    static final String FACT_SECTION = "【事实】";
    /** 四段式段标记（行首出现即切换所属段） */
    private static final List<String> SECTION_MARKERS =
            List.of("【事实】", "【限制】", "【假设】", "【建议】");

    /**
     * 校验草稿事实段。规则：
     * <ul>
     *   <li>行首段标记决定该行及后续行的所属段，直至下一个段标记；</li>
     *   <li>事实段逐行检查是否包含任一 evidenceRef 子串（行内含"假设"标注的跳过）；</li>
     *   <li>evidenceRefs 为空时，事实段任何非空行都算违规；</li>
     *   <li>限制 / 假设 / 建议段不校验。</li>
     * </ul>
     */
    public Check check(String draft, List<String> evidenceRefs) {
        List<String> refs = evidenceRefs == null ? List.of() : evidenceRefs;
        List<String> violations = new ArrayList<>();
        String section = null;
        for (String rawLine : (draft == null ? "" : draft).split("\\R")) {
            String line = rawLine.trim();
            for (String marker : SECTION_MARKERS) {
                if (line.startsWith(marker)) {
                    section = marker;
                    line = line.substring(marker.length()).trim();
                    break;
                }
            }
            if (line.isEmpty() || !FACT_SECTION.equals(section) || line.contains("假设")) {
                continue;
            }
            if (refs.stream().noneMatch(line::contains)) {
                violations.add(line);
            }
        }
        return new Check(violations.isEmpty(), violations);
    }
}

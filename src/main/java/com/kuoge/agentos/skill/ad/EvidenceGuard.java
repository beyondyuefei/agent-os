package com.kuoge.agentos.skill.ad;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostReasoning 证据指针校验（方案「幻觉校验」，确定性规则实现，不经 LLM）。
 * AI Skill 直连 LlmClient、不经 ConversationRuntime，故校验落在 skill 层而非运行时层：
 * 四段式复盘草稿（事实 / 限制 / 假设 / 建议）中，事实段的每条结论
 * 必须引用 Observation.evidenceRefs 里至少一个证据指针；无证据的内容只允许出现在
 * 假设段或在行内显式标注"假设"。验收口径：无指针结论比例为零。
 *
 * <p>段标记兼容两种写法：模板约定的【事实】行首标记，以及真实模型常见的
 * markdown / 序号标题（如 {@code ## 一、事实}、{@code **假设**}）。
 * 整篇识别不出任何段结构时按最严口径处理：全部内容视作事实段逐行校验——
 * 防止模型换格式绕过校验（真实 K3 冒烟中发现过该绕过路径）。
 */
public class EvidenceGuard {

    /** 校验结果：pass = 事实段无违规；violations = 无证据的事实行原文 */
    public record Check(boolean pass, List<String> violations) {
        public Check {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }

    /** 事实段标记（规范化后的段名） */
    static final String FACT_SECTION = "事实";
    /** 四段式段名 */
    private static final List<String> SECTIONS = List.of("事实", "限制", "假设", "建议");
    /** 标题形态：允许 # / * / > / - / 空白 前导 + 可选序号（1. / 一、）+ 段名 + 收尾 * 号 */
    private static final Pattern HEADER = Pattern.compile(
            "^[#*>\\-\\s]*(?:[0-9一二三四五六七八九十]+[、.．]\\s*)?(事实|限制|假设|建议)[\\s*]*$");

    /**
     * 校验草稿事实段。规则：
     * <ul>
     *   <li>行首段标记决定该行及后续行的所属段，直至下一个段标记；</li>
     *   <li>事实段逐行检查是否包含任一 evidenceRef 子串（行内含"假设"标注的跳过）；</li>
     *   <li>evidenceRefs 为空时，事实段任何非空行都算违规；</li>
     *   <li>限制 / 假设 / 建议段不校验；</li>
     *   <li>整篇无段结构时全部按事实段处理（最严口径，防格式绕过）。</li>
     * </ul>
     */
    public Check check(String draft, List<String> evidenceRefs) {
        List<String> refs = evidenceRefs == null ? List.of() : evidenceRefs;
        List<String> violations = new ArrayList<>();
        String text = draft == null ? "" : draft;
        String section = hasAnySection(text) ? null : FACT_SECTION;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            String header = sectionOf(line);
            if (header != null) {
                section = header;
                // 【事实】xxx 行内紧跟内容的，剥掉标记后继续校验剩余部分；
                // markdown 标题行（## 一、事实）本身无内容，直接跳过
                String bracketed = "【" + header + "】";
                if (line.startsWith(bracketed)) {
                    line = line.substring(bracketed.length()).trim();
                } else {
                    continue;
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

    /** 识别行是否为段标题：【事实】行首标记，或 markdown / 序号标题；命中返回规范段名 */
    private String sectionOf(String line) {
        for (String name : SECTIONS) {
            String bracketed = "【" + name + "】";
            if (line.startsWith(bracketed)) {
                return name;
            }
        }
        Matcher matcher = HEADER.matcher(line);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private boolean hasAnySection(String draft) {
        for (String rawLine : draft.split("\\R")) {
            if (sectionOf(rawLine.trim()) != null) {
                return true;
            }
        }
        return false;
    }
}

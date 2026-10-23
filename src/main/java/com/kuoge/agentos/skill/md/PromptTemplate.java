package com.kuoge.agentos.skill.md;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板工具：{{variable}} 占位符渲染与变量提取。
 * 渲染时未提供的变量立即失败（fail-fast），防止带空洞的内容进入模型或用户界面。
 */
public final class PromptTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private PromptTemplate() {}

    public static String render(String template, Map<String, ?> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Prompt template variable missing: {{" + key + "}}");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** 提取内容中引用的全部变量（保存时契约校验用） */
    public static Set<String> extractVariables(String template) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }
}

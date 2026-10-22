package com.kuoge.agentos.skill.md;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板渲染：{{variable}} 占位符替换。
 * 未提供的变量立即失败（fail-fast），防止带空洞的提示词进入模型。
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
}

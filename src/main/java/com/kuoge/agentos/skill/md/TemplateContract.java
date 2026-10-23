package com.kuoge.agentos.skill.md;

import java.util.Map;

/**
 * 单个模板的契约：变量声明 + 默认内容（首次启动的种子数据）。
 */
public record TemplateContract(
        /** 变量名 → 契约 */
        Map<String, VariableContract> variables,
        /** 默认内容（种子；运行时被数据库内容取代） */
        String defaultContent
) {
    public TemplateContract {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        defaultContent = defaultContent == null ? "" : defaultContent;
    }
}

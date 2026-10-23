package com.kuoge.agentos.skill.md;

/**
 * 模板变量契约：代码与内容之间的接口声明。
 * required = true 的变量，渲染时调用方必须提供（缺失即 fail-fast）。
 */
public record VariableContract(
        /** string | number */
        String type,
        boolean required,
        String description
) {}

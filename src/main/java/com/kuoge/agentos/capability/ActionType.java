package com.kuoge.agentos.capability;

import java.util.Map;
import java.util.Set;

/**
 * 动作分类（方案 6 / 8.1）：风险等级由服务端动作注册表判定，调用方不得自报。
 * <ul>
 *   <li>RISK 风险动作：新建 / 启用 / 恢复 / 加预算 / 改条件 —— 每次执行前五项重检，受套餐门禁阻断</li>
 *   <li>STOP 停止类动作：暂停 / 结束 / 查询 / 对账 —— 不受套餐门禁阻断，走独立审计路径</li>
 *   <li>READ 只读动作：不做授权检查但全量留痕</li>
 * </ul>
 */
public enum ActionType {
    RISK,
    STOP,
    READ;

    private static final Map<String, ActionType> REGISTRY = Map.ofEntries(
            Map.entry("ITEM_CREATE", RISK),
            Map.entry("AD_CREATE", RISK),
            Map.entry("AD_ENABLE", RISK),
            Map.entry("AD_RESUME", RISK),
            Map.entry("BUDGET_INCREASE", RISK),
            Map.entry("CONDITION_CHANGE", RISK),
            Map.entry("CREATIVE_GENERATE", RISK),
            Map.entry("EVOLVE_EXECUTE", RISK),
            Map.entry("AD_PAUSE", STOP),
            Map.entry("AD_END", STOP),
            Map.entry("RECONCILE", STOP),
            Map.entry("STATUS_QUERY", READ),
            Map.entry("INSIGHT_QUERY", READ),
            Map.entry("EVOLVE_SUGGEST", READ),
            Map.entry("REVIEW_DRAFT", READ),
            Map.entry("EXPERIENCE_DISTILL", READ)
    );

    private static final Set<String> KNOWN = REGISTRY.keySet();

    /** 服务端动作注册表：未知动作一律按风险动作处理（安全默认） */
    public static ActionType of(String action) {
        if (action == null || !KNOWN.contains(action)) {
            return RISK;
        }
        return REGISTRY.get(action);
    }
}

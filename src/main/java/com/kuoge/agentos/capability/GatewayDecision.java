package com.kuoge.agentos.capability;

/**
 * 网关判定输出（方案 8.1 GatewayCheckOutput）。
 */
public record GatewayDecision(
        Decision decision,
        RejectCode rejectCode,
        /** ALLOW / DEGRADED 时注入执行上下文的策略快照 */
        PolicyProfile policy,
        /** 绑定的 skill 版本线 */
        String skillVersion,
        /** DEGRADED 时给出建议形态与升级线索 */
        String degradeNote
) {

    public boolean allowed() {
        return decision == Decision.ALLOW;
    }

    public boolean rejected() {
        return decision == Decision.REJECTED;
    }

    public static GatewayDecision allow(PolicyProfile policy, String skillVersion) {
        return new GatewayDecision(Decision.ALLOW, null, policy, skillVersion, null);
    }

    public static GatewayDecision degraded(PolicyProfile policy, String skillVersion, String note) {
        return new GatewayDecision(Decision.DEGRADED, null, policy, skillVersion, note);
    }

    public static GatewayDecision rejected(RejectCode code) {
        return new GatewayDecision(Decision.REJECTED, code, null, null, null);
    }
}

package com.kuoge.agentos.capability;

import com.kuoge.agentos.core.hook.HookResult;
import com.kuoge.agentos.core.hook.ToolHook;
import com.kuoge.agentos.infrastructure.tenant.TenantContext;

import java.util.List;
import java.util.Map;

/**
 * 网关钩子：挂在 Agent 执行循环 PreToolCall 点位（方案 2.2 生命周期钩子）。
 * 工具名 → 能力/动作映射由服务端注册表维护，业务 Skill 零感知。
 */
public class CapabilityGatewayHook implements ToolHook {

    /** 工具调用路由：toolName → [capability, action] */
    public record ToolRoute(CapabilityCode capability, String action) {}

    private final CapabilityGateway gateway;
    private final Map<String, ToolRoute> routes;

    public CapabilityGatewayHook(CapabilityGateway gateway, Map<String, ToolRoute> routes) {
        this.gateway = gateway;
        this.routes = routes == null ? Map.of() : Map.copyOf(routes);
    }

    @Override
    public HookResult preToolUse(String toolName, String input) {
        ToolRoute route = routes.get(toolName);
        if (route == null) {
            return HookResult.allow();
        }
        GatewayDecision decision = gateway.check(TenantContext.get(), route.capability(), route.action());
        if (decision.rejected()) {
            return HookResult.deny("Capability gateway rejected tool [" + toolName + "]: "
                    + decision.rejectCode());
        }
        return HookResult.allow();
    }
}

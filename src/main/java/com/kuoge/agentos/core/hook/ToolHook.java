package com.kuoge.agentos.core.hook;

public interface ToolHook {

    default HookResult preToolUse(String toolName, String input) {
        return HookResult.allow();
    }

    default HookResult postToolUse(String toolName, String input, String output) {
        return HookResult.allow();
    }

    default HookResult postToolUseFailure(String toolName, String input, String output) {
        return HookResult.allow();
    }
}

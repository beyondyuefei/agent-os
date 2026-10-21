package com.kuoge.agentos.core.permission;

public class PermissionPolicy {

    private final PermissionMode mode;

    public PermissionPolicy(PermissionMode mode) {
        this.mode = mode;
    }

    public PermissionOutcome authorize(String toolName, String input) {
        return switch (mode) {
            case Allow -> new PermissionOutcome.Allow();
            case Prompt, WorkspaceWrite, DangerFullAccess -> {
                if (isDangerous(toolName, input)) {
                    yield new PermissionOutcome.Ask("Dangerous operation requires approval: " + toolName);
                }
                yield new PermissionOutcome.Allow();
            }
        };
    }

    private boolean isDangerous(String toolName, String input) {
        String lower = input.toLowerCase();
        return toolName.contains("bash") && (lower.contains("rm -rf") || lower.contains("delete") || lower.contains("drop"));
    }

    public static PermissionPolicy allowAll() {
        return new PermissionPolicy(PermissionMode.Allow);
    }
}

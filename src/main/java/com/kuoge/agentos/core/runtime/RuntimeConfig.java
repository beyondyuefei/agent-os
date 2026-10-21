package com.kuoge.agentos.core.runtime;

import com.kuoge.agentos.core.permission.PermissionPolicy;

public record RuntimeConfig(
        String systemPrompt,
        int maxIterationsPerTurn,
        int maxTurnsPerSession,
        PermissionPolicy permissionPolicy,
        boolean autoCompactionEnabled,
        int autoCompactionTokenThreshold
) {

    public RuntimeConfig {
        if (systemPrompt == null) systemPrompt = "";
        maxIterationsPerTurn = Math.max(1, maxIterationsPerTurn);
        maxTurnsPerSession = Math.max(1, maxTurnsPerSession);
        if (permissionPolicy == null) permissionPolicy = PermissionPolicy.allowAll();
        autoCompactionTokenThreshold = Math.max(1000, autoCompactionTokenThreshold);
    }

    public static RuntimeConfig defaults() {
        return new RuntimeConfig(
                "You are a helpful AI agent for cross-border e-commerce operations.",
                10,
                10,
                PermissionPolicy.allowAll(),
                false,
                100_000
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String systemPrompt;
        private int maxIterationsPerTurn = 10;
        private int maxTurnsPerSession = 10;
        private PermissionPolicy permissionPolicy = PermissionPolicy.allowAll();
        private boolean autoCompactionEnabled;
        private int autoCompactionTokenThreshold = 100_000;

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxIterationsPerTurn(int maxIterationsPerTurn) {
            this.maxIterationsPerTurn = maxIterationsPerTurn;
            return this;
        }

        public Builder maxTurnsPerSession(int maxTurnsPerSession) {
            this.maxTurnsPerSession = maxTurnsPerSession;
            return this;
        }

        public Builder permissionPolicy(PermissionPolicy permissionPolicy) {
            this.permissionPolicy = permissionPolicy;
            return this;
        }

        public Builder autoCompactionEnabled(boolean autoCompactionEnabled) {
            this.autoCompactionEnabled = autoCompactionEnabled;
            return this;
        }

        public Builder autoCompactionTokenThreshold(int autoCompactionTokenThreshold) {
            this.autoCompactionTokenThreshold = autoCompactionTokenThreshold;
            return this;
        }

        public RuntimeConfig build() {
            return new RuntimeConfig(systemPrompt, maxIterationsPerTurn, maxTurnsPerSession,
                    permissionPolicy, autoCompactionEnabled, autoCompactionTokenThreshold);
        }
    }
}

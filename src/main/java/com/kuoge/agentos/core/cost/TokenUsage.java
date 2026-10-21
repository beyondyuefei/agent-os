package com.kuoge.agentos.core.cost;

public record TokenUsage(int inputTokens, int outputTokens) {

    public TokenUsage {
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
    }

    public static TokenUsage empty() {
        return new TokenUsage(0, 0);
    }

    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(this.inputTokens + other.inputTokens, this.outputTokens + other.outputTokens);
    }
}

package com.kuoge.agentos.core.cost;

public class UsageTracker {

    private int totalInputTokens = 0;
    private int totalOutputTokens = 0;
    private int llmCalls = 0;
    private int toolCalls = 0;

    public void recordLlm(TokenUsage usage) {
        totalInputTokens += usage.inputTokens();
        totalOutputTokens += usage.outputTokens();
        llmCalls++;
    }

    public void recordTool() {
        toolCalls++;
    }

    public TokenUsage total() {
        return new TokenUsage(totalInputTokens, totalOutputTokens);
    }

    public String report() {
        return String.format("Usage[llmCalls=%d, toolCalls=%d, inputTokens=%d, outputTokens=%d]",
                llmCalls, toolCalls, totalInputTokens, totalOutputTokens);
    }
}

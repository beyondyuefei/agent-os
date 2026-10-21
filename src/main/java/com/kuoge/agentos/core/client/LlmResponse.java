package com.kuoge.agentos.core.client;

import com.kuoge.agentos.core.session.ContentBlock;
import com.kuoge.agentos.core.cost.TokenUsage;

import java.util.Collections;
import java.util.List;

/**
 * LLM 结构化响应。
 */
public record LlmResponse(
        List<ContentBlock> blocks,
        TokenUsage usage
) {

    public LlmResponse {
        blocks = blocks == null ? Collections.emptyList() : List.copyOf(blocks);
        usage = usage == null ? TokenUsage.empty() : usage;
    }

    public static LlmResponse text(String text) {
        return new LlmResponse(List.of(new ContentBlock.TextBlock(text)), TokenUsage.empty());
    }

    public static LlmResponse withToolUse(String toolUseId, String toolName, String input) {
        return new LlmResponse(
                List.of(new ContentBlock.ToolUseBlock(toolUseId, toolName, input)),
                TokenUsage.empty()
        );
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public List<ContentBlock.ToolUseBlock> toolUses() {
        return blocks.stream()
                .filter(b -> b instanceof ContentBlock.ToolUseBlock)
                .map(b -> (ContentBlock.ToolUseBlock) b)
                .toList();
    }

    public String extractText() {
        return blocks.stream()
                .filter(b -> b instanceof ContentBlock.TextBlock)
                .map(b -> ((ContentBlock.TextBlock) b).text())
                .findFirst()
                .orElse("");
    }
}

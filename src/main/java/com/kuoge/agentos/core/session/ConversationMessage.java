package com.kuoge.agentos.core.session;

import com.kuoge.agentos.core.cost.TokenUsage;

import java.util.Collections;
import java.util.List;

public record ConversationMessage(
        MessageRole role,
        List<ContentBlock> blocks,
        TokenUsage usage
) {

    public ConversationMessage {
        blocks = blocks == null ? Collections.emptyList() : List.copyOf(blocks);
        usage = usage == null ? TokenUsage.empty() : usage;
    }

    public static ConversationMessage userText(String text) {
        return new ConversationMessage(MessageRole.USER, List.of(new ContentBlock.TextBlock(text)), TokenUsage.empty());
    }

    public static ConversationMessage systemText(String text) {
        return new ConversationMessage(MessageRole.SYSTEM, List.of(new ContentBlock.TextBlock(text)), TokenUsage.empty());
    }

    public static ConversationMessage toolResult(String toolUseId, String toolName, String output, boolean isError) {
        return new ConversationMessage(
                MessageRole.TOOL,
                List.of(new ContentBlock.ToolResultBlock(toolUseId, toolName, output, isError)),
                TokenUsage.empty()
        );
    }

    public String extractText() {
        return blocks.stream()
                .filter(b -> b instanceof ContentBlock.TextBlock)
                .map(b -> ((ContentBlock.TextBlock) b).text())
                .findFirst()
                .orElse("");
    }

    public List<ContentBlock.ToolUseBlock> toolUses() {
        return blocks.stream()
                .filter(b -> b instanceof ContentBlock.ToolUseBlock)
                .map(b -> (ContentBlock.ToolUseBlock) b)
                .toList();
    }

    public int estimateTokens() {
        return blocks.stream().mapToInt(ContentBlock::estimateTokens).sum();
    }
}

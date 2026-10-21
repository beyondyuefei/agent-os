package com.kuoge.agentos.core.session;

public sealed interface ContentBlock {

    int estimateTokens();

    record TextBlock(String text) implements ContentBlock {
        @Override
        public int estimateTokens() {
            return text.length() / 4 + 1;
        }
    }

    record ThinkingBlock(String thinking, String signature) implements ContentBlock {
        @Override
        public int estimateTokens() {
            return thinking.length() / 4 + 1;
        }
    }

    record ToolUseBlock(String toolUseId, String toolName, String input) implements ContentBlock {
        @Override
        public int estimateTokens() {
            return (toolName.length() + input.length()) / 4 + 1;
        }
    }

    record ToolResultBlock(String toolUseId, String toolName, String output, boolean isError)
            implements ContentBlock {
        @Override
        public int estimateTokens() {
            return (toolName.length() + output.length()) / 4 + 1;
        }
    }
}

package com.kuoge.agentos.core.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Agent 会话状态管理器。
 */
public class AgentSession {

    private final String sessionId;
    private final List<ConversationMessage> messages = new ArrayList<>();

    public AgentSession() {
        this.sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public AgentSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void pushMessage(ConversationMessage message) {
        validateMessageOrder(message);
        messages.add(message);
    }

    public void pushUserText(String text) {
        pushMessage(ConversationMessage.userText(text));
    }

    public List<ConversationMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public void clear() {
        messages.clear();
    }

    public int estimateTotalTokens() {
        return messages.stream().mapToInt(ConversationMessage::estimateTokens).sum();
    }

    private void validateMessageOrder(ConversationMessage message) {
        if (message.role() == MessageRole.TOOL) {
            List<ContentBlock.ToolResultBlock> results = message.blocks().stream()
                    .filter(b -> b instanceof ContentBlock.ToolResultBlock)
                    .map(b -> (ContentBlock.ToolResultBlock) b)
                    .toList();
            for (ContentBlock.ToolResultBlock tr : results) {
                boolean hasMatching = messages.stream().anyMatch(m ->
                        m.role() == MessageRole.ASSISTANT && m.toolUses().stream()
                                .anyMatch(tu -> tu.toolUseId().equals(tr.toolUseId()))
                );
                if (!hasMatching) {
                    throw new IllegalStateException("ToolResult has no matching ToolUse: " + tr.toolUseId());
                }
            }
        }
    }
}

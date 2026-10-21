package com.kuoge.agentos.core.runtime;

import com.kuoge.agentos.core.cost.TokenUsage;
import com.kuoge.agentos.core.session.ConversationMessage;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

@Builder
public record TurnSummary(
        String userInput,
        List<ConversationMessage> assistantMessages,
        List<ConversationMessage> toolResults,
        int iterations,
        TokenUsage usage,
        String stopReason
) {

    public TurnSummary {
        assistantMessages = assistantMessages == null ? Collections.emptyList() : List.copyOf(assistantMessages);
        toolResults = toolResults == null ? Collections.emptyList() : List.copyOf(toolResults);
        userInput = userInput == null ? "" : userInput;
        stopReason = stopReason == null ? "" : stopReason;
    }

    public String finalAnswer() {
        if (assistantMessages.isEmpty()) return "";
        return assistantMessages.get(assistantMessages.size() - 1).extractText();
    }
}

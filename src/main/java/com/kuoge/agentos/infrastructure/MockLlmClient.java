package com.kuoge.agentos.infrastructure;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.core.session.ConversationMessage;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 测试用 Mock LLM 客户端。
 */
public class MockLlmClient implements LlmClient {

    private final Function<List<ConversationMessage>, LlmResponse> responder;

    public MockLlmClient(Function<List<ConversationMessage>, LlmResponse> responder) {
        this.responder = responder;
    }

    @Override
    public LlmResponse call(String systemPrompt, List<ConversationMessage> messages) {
        return responder.apply(messages);
    }

    public static MockLlmClient directText(String text) {
        return new MockLlmClient(messages -> LlmResponse.text(text));
    }

    public static MockLlmClient keyword(Map<String, String> keywordToAnswer) {
        return new MockLlmClient(messages -> {
            String input = messages.isEmpty() ? "" : messages.get(messages.size() - 1).extractText();
            for (Map.Entry<String, String> entry : keywordToAnswer.entrySet()) {
                if (input.contains(entry.getKey())) {
                    return LlmResponse.text(entry.getValue());
                }
            }
            return LlmResponse.text("default answer");
        });
    }
}

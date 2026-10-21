package com.kuoge.agentos.core.client;

import com.kuoge.agentos.core.session.ConversationMessage;

import java.util.List;

/**
 * LLM 客户端接口。
 */
@FunctionalInterface
public interface LlmClient {

    LlmResponse call(String systemPrompt, List<ConversationMessage> messages);
}

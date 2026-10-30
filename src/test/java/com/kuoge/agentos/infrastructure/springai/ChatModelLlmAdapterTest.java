package com.kuoge.agentos.infrastructure.springai;

import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.core.session.ConversationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ChatModelLlmAdapter 流式聚合测试：流式分块聚合成完整 LlmResponse，业务无感；
 * streaming=false 时回退非流式 call。
 */
class ChatModelLlmAdapterTest {

    private ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void streamingChunksAreAggregated() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chunk("【事实】周期内花费 "), chunk("50000 分。")));
        ChatModelLlmAdapter adapter = new ChatModelLlmAdapter(chatModel, true);

        LlmResponse response = adapter.call("系统提示", List.of(ConversationMessage.userText("复盘一下")));

        assertEquals("【事实】周期内花费 50000 分。", response.extractText());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void streamingDisabledFallsBackToCall() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chunk("完整回答"));
        ChatModelLlmAdapter adapter = new ChatModelLlmAdapter(chatModel, false);

        LlmResponse response = adapter.call(null, List.of(ConversationMessage.userText("hi")));

        assertEquals("完整回答", response.extractText());
        verify(chatModel, never()).stream(any(Prompt.class));
    }

    @Test
    void emptyStreamYieldsEmptyResponse() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());
        ChatModelLlmAdapter adapter = new ChatModelLlmAdapter(chatModel, true);

        LlmResponse response = adapter.call(null, List.of(ConversationMessage.userText("hi")));

        assertTrue(response.isEmpty());
    }
}

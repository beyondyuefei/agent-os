package com.kuoge.agentos.infrastructure.springai;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.core.cost.TokenUsage;
import com.kuoge.agentos.core.session.ContentBlock;
import com.kuoge.agentos.core.session.ConversationMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Spring AI ChatModel 适配为 agent-os 的 LlmClient。
 *
 * <p>把 agent-os 的结构化消息转换为 Spring AI Message，调用底层模型后返回 LlmResponse。
 *
 * <p>默认走流式（{@code chatModel.stream} + MessageAggregator 聚合成完整响应）：
 * 对业务完全无感（接口与返回结构不变），但流式连接在思考型模型长生成期间持续有数据，
 * 避免非流式长连接被中间层 idle 超时掐断（k3.1-pro max 档位实测踩过）。
 * 可用 agentos.llm.streaming=false 回退为非流式。
 */
@Component
public class ChatModelLlmAdapter implements LlmClient {

    private final ChatModel chatModel;
    private final boolean streaming;

    public ChatModelLlmAdapter(ChatModel chatModel,
                               @Value("${agentos.llm.streaming:true}") boolean streaming) {
        this.chatModel = chatModel;
        this.streaming = streaming;
    }

    @Override
    public LlmResponse call(String systemPrompt, List<ConversationMessage> messages) {
        List<Message> springMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            springMessages.add(new SystemMessage(systemPrompt));
        }
        for (ConversationMessage msg : messages) {
            springMessages.add(toSpringMessage(msg));
        }

        Prompt prompt = new Prompt(springMessages);
        ChatResponse response = streaming ? callStreaming(prompt) : chatModel.call(prompt);

        List<ContentBlock> blocks = extractBlocks(response);
        TokenUsage usage = extractUsage(response);
        return new LlmResponse(blocks, usage);
    }

    /** 流式调用 + 聚合为完整响应（aggregationConsumer 在流结束时收到聚合结果） */
    private ChatResponse callStreaming(Prompt prompt) {
        java.util.concurrent.atomic.AtomicReference<ChatResponse> aggregated = new java.util.concurrent.atomic.AtomicReference<>();
        new MessageAggregator().aggregate(chatModel.stream(prompt), aggregated::set).blockLast();
        return aggregated.get();
    }

    private Message toSpringMessage(ConversationMessage msg) {
        return switch (msg.role()) {
            case SYSTEM -> new SystemMessage(msg.extractText());
            case USER -> new UserMessage(msg.extractText());
            case ASSISTANT -> {
                List<ContentBlock.ToolUseBlock> toolUses = msg.toolUses();
                if (toolUses.isEmpty()) {
                    yield new AssistantMessage(msg.extractText());
                }
                List<AssistantMessage.ToolCall> toolCalls = toolUses.stream()
                        .map(tu -> new AssistantMessage.ToolCall(tu.toolUseId(), "function", tu.toolName(), tu.input()))
                        .toList();
                yield AssistantMessage.builder()
                        .content(msg.extractText())
                        .toolCalls(toolCalls)
                        .build();
            }
            case TOOL -> {
                List<ToolResponseMessage.ToolResponse> responses = msg.blocks().stream()
                        .filter(b -> b instanceof ContentBlock.ToolResultBlock)
                        .map(b -> (ContentBlock.ToolResultBlock) b)
                        .map(tr -> new ToolResponseMessage.ToolResponse(tr.toolUseId(), tr.toolName(), tr.output()))
                        .toList();
                yield ToolResponseMessage.builder().responses(responses).build();
            }
        };
    }

    private List<ContentBlock> extractBlocks(ChatResponse response) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return blocks;
        }

        AssistantMessage output = (AssistantMessage) response.getResult().getOutput();
        String text = output.getText();
        if (text != null && !text.isBlank()) {
            blocks.add(new ContentBlock.TextBlock(text));
        }

        if (output.hasToolCalls()) {
            for (AssistantMessage.ToolCall tc : output.getToolCalls()) {
                blocks.add(new ContentBlock.ToolUseBlock(tc.id(), tc.name(), tc.arguments()));
            }
        }
        return blocks;
    }

    private TokenUsage extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return TokenUsage.empty();
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return TokenUsage.empty();
        }
        return new TokenUsage(
                usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens()
        );
    }
}

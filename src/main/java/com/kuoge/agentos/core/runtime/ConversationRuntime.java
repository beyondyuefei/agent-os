package com.kuoge.agentos.core.runtime;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.core.cost.TokenUsage;
import com.kuoge.agentos.core.cost.UsageTracker;
import com.kuoge.agentos.core.hook.HookEvent;
import com.kuoge.agentos.core.hook.HookResult;
import com.kuoge.agentos.core.hook.ToolHook;
import com.kuoge.agentos.core.permission.PermissionOutcome;
import com.kuoge.agentos.core.session.AgentSession;
import com.kuoge.agentos.core.session.ContentBlock;
import com.kuoge.agentos.core.session.ConversationMessage;
import com.kuoge.agentos.core.session.MessageRole;
import com.kuoge.agentos.core.tool.Tool;
import com.kuoge.agentos.core.tool.ToolRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 生产级 ReAct 运行时 —— 核心对话循环。
 */
@Slf4j
public class ConversationRuntime {

    @Getter
    private final AgentSession session;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final RuntimeConfig config;
    private final UsageTracker usageTracker;
    private final List<ToolHook> hooks = new ArrayList<>();

    @Getter
    private int turnCount = 0;

    public ConversationRuntime(AgentSession session, LlmClient llmClient,
                               ToolRegistry toolRegistry, RuntimeConfig config) {
        this.session = session;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.usageTracker = new UsageTracker();
    }

    public ConversationRuntime addHook(ToolHook hook) {
        this.hooks.add(hook);
        return this;
    }

    public TurnSummary runTurn(String userInput) {
        if (turnCount >= config.maxTurnsPerSession()) {
            return buildSummary(userInput, List.of(), List.of(), 0, TokenUsage.empty(), "max_turns_reached");
        }

        session.pushUserText(userInput);
        List<ConversationMessage> assistantMessages = new ArrayList<>();
        List<ConversationMessage> toolResults = new ArrayList<>();
        int iterations = 0;
        TokenUsage turnUsage = TokenUsage.empty();

        while (true) {
            iterations++;
            if (iterations > config.maxIterationsPerTurn()) {
                return buildSummary(userInput, assistantMessages, toolResults, iterations,
                        turnUsage, "max_iterations_reached");
            }

            LlmResponse response = llmClient.call(config.systemPrompt(), session.getMessages());
            if (response == null || response.isEmpty()) {
                return buildSummary(userInput, assistantMessages, toolResults, iterations,
                        turnUsage, "empty_response");
            }

            usageTracker.recordLlm(response.usage());
            turnUsage = turnUsage.add(response.usage());

            ConversationMessage assistantMsg = new ConversationMessage(MessageRole.ASSISTANT, response.blocks(), response.usage());
            session.pushMessage(assistantMsg);
            assistantMessages.add(assistantMsg);

            List<ContentBlock.ToolUseBlock> pendingTools = response.toolUses();
            if (pendingTools.isEmpty()) {
                break;
            }

            for (ContentBlock.ToolUseBlock toolUse : pendingTools) {
                ConversationMessage result = executeToolUse(toolUse);
                session.pushMessage(result);
                toolResults.add(result);
            }
        }

        turnCount++;
        return buildSummary(userInput, assistantMessages, toolResults, iterations, turnUsage, "completed");
    }

    private ConversationMessage executeToolUse(ContentBlock.ToolUseBlock toolUse) {
        String toolName = toolUse.toolName();
        String input = toolUse.input();
        String toolUseId = toolUse.toolUseId();

        HookResult preHook = runHooks(HookEvent.PreToolUse, toolName, input, null);
        if (preHook.denied() || preHook.cancelled()) {
            return ConversationMessage.toolResult(toolUseId, toolName,
                    "Denied by hook: " + preHook.messages(), true);
        }
        String effectiveInput = preHook.updatedInput() != null ? preHook.updatedInput() : input;

        PermissionOutcome permission = config.permissionPolicy().authorize(toolName, effectiveInput);
        if (permission instanceof PermissionOutcome.Deny deny) {
            return ConversationMessage.toolResult(toolUseId, toolName, "Permission denied: " + deny.reason(), true);
        }
        if (permission instanceof PermissionOutcome.Ask ask) {
            return ConversationMessage.toolResult(toolUseId, toolName, "Permission requires approval: " + ask.reason(), true);
        }

        String output;
        boolean isError;
        try {
            Tool tool = toolRegistry.find(toolName).orElse(null);
            if (tool == null) {
                output = "Unknown tool: " + toolName;
                isError = true;
            } else {
                Map<String, Object> args = parseToolInput(effectiveInput);
                output = tool.execute(args);
                isError = false;
                usageTracker.recordTool();
            }
        } catch (Exception e) {
            output = "Tool execution error: " + e.getMessage();
            isError = true;
        }

        HookEvent postEvent = isError ? HookEvent.PostToolUseFailure : HookEvent.PostToolUse;
        HookResult postHook = runHooks(postEvent, toolName, effectiveInput, output);
        if (postHook.denied() || postHook.failed() || postHook.cancelled()) {
            isError = true;
        }

        return ConversationMessage.toolResult(toolUseId, toolName, output, isError);
    }

    private HookResult runHooks(HookEvent event, String toolName, String input, String output) {
        HookResult merged = HookResult.allow();
        for (ToolHook hook : hooks) {
            HookResult result = switch (event) {
                case PreToolUse -> hook.preToolUse(toolName, input);
                case PostToolUse -> hook.postToolUse(toolName, input, output);
                case PostToolUseFailure -> hook.postToolUseFailure(toolName, input, output);
            };
            merged = merge(merged, result);
            if (result.denied() || result.failed() || result.cancelled()) break;
        }
        return merged;
    }

    private HookResult merge(HookResult a, HookResult b) {
        List<String> messages = new ArrayList<>(a.messages());
        messages.addAll(b.messages());
        return new HookResult(
                a.denied() || b.denied(),
                a.failed() || b.failed(),
                a.cancelled() || b.cancelled(),
                messages,
                b.updatedInput() != null ? b.updatedInput() : a.updatedInput()
        );
    }

    private Map<String, Object> parseToolInput(String input) {
        if (input == null || input.isBlank()) return Map.of();
        String trimmed = input.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                return parseSimpleJson(trimmed);
            } catch (Exception e) {
                return Map.of("raw", input);
            }
        }
        return Map.of("raw", input);
    }

    private Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> result = new java.util.HashMap<>();
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) return result;
        String[] pairs = content.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "").replace("'", "");
                String value = kv[1].trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        return result;
    }

    private TurnSummary buildSummary(String userInput, List<ConversationMessage> assistantMessages,
                                      List<ConversationMessage> toolResults, int iterations,
                                      TokenUsage usage, String stopReason) {
        return TurnSummary.builder()
                .userInput(userInput)
                .assistantMessages(assistantMessages)
                .toolResults(toolResults)
                .iterations(iterations)
                .usage(usage)
                .stopReason(stopReason)
                .build();
    }
}

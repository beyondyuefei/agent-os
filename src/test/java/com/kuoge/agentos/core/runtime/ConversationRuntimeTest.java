package com.kuoge.agentos.core.runtime;

import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.core.session.AgentSession;
import com.kuoge.agentos.core.session.ConversationMessage;
import com.kuoge.agentos.core.tool.Tool;
import com.kuoge.agentos.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversationRuntimeTest {

    @Test
    void testSingleTurnTextResponse() {
        LlmClient llm = (systemPrompt, messages) -> LlmResponse.text("Hello!");
        ToolRegistry tools = new ToolRegistry();
        ConversationRuntime runtime = new ConversationRuntime(new AgentSession(), llm, tools, RuntimeConfig.defaults());

        TurnSummary summary = runtime.runTurn("Hi");

        assertEquals("completed", summary.stopReason());
        assertEquals("Hello!", summary.finalAnswer());
    }

    @Test
    void testToolUseChain() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new Tool() {
            @Override public String name() { return "calculator"; }
            @Override public String description() { return "Calculate expression"; }
            @Override public String execute(Map<String, Object> args) { return "100"; }
        });

        LlmClient llm = new LlmClient() {
            private int call = 0;
            @Override public LlmResponse call(String systemPrompt, List<ConversationMessage> messages) {
                call++;
                if (call == 1) {
                    return LlmResponse.withToolUse("tu-1", "calculator", "{\"expression\":\"25*4\"}");
                }
                return LlmResponse.text("The result is 100.");
            }
        };

        ConversationRuntime runtime = new ConversationRuntime(new AgentSession(), llm, tools, RuntimeConfig.defaults());
        TurnSummary summary = runtime.runTurn("What is 25*4?");

        assertEquals("completed", summary.stopReason());
        assertEquals(2, summary.iterations());
        assertEquals("The result is 100.", summary.finalAnswer());
    }
}

package com.kuoge.agentos.orchestration.checkpoint;

import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.session.ConversationMessage;
import com.kuoge.agentos.orchestration.AgentTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 验证检查点 —— 借鉴 Hermes Agent 的 validation checkpoint。
 *
 * <p>在关键任务完成后，对子 Agent 的输出进行规则或 LLM 评估，防止错误在 DAG 中 cascade。
 * 若检查失败，可触发重试、跳过或停止整条工作流。
 */
@Slf4j
@Component
public class CheckpointEvaluator {

    private final LlmClient llmClient;

    public CheckpointEvaluator() {
        this(null);
    }

    public CheckpointEvaluator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 根据检查点配置评估 Agent 输出。
     *
     * @param task       当前任务
     * @param result     子 Agent 执行结果
     * @param checkpoint 检查点定义，如 {@code Map.of("type", "rule", "mustContain", "PASSED")}
     */
    public ValidationResult evaluate(AgentTask task, AgentResult result, Map<String, Object> checkpoint) {
        if (checkpoint == null || checkpoint.isEmpty()) {
            return ValidationResult.pass("No checkpoint configured");
        }

        String type = String.valueOf(checkpoint.getOrDefault("type", "rule"));
        return switch (type) {
            case "rule" -> evaluateByRule(result, checkpoint);
            case "llm" -> evaluateByLlm(task, result, checkpoint);
            default -> ValidationResult.pass("Unknown checkpoint type: " + type);
        };
    }

    private ValidationResult evaluateByRule(AgentResult result, Map<String, Object> checkpoint) {
        String output = result.output();
        String mustContain = (String) checkpoint.get("mustContain");
        if (mustContain != null && !output.contains(mustContain)) {
            return ValidationResult.retry("Output must contain '" + mustContain + "' but was: " + output);
        }

        String mustNotContain = (String) checkpoint.get("mustNotContain");
        if (mustNotContain != null && output.contains(mustNotContain)) {
            return ValidationResult.retry("Output must not contain '" + mustNotContain + "'");
        }

        Number minLatency = (Number) checkpoint.get("minLatencyMs");
        if (minLatency != null && result.latencyMs() < minLatency.longValue()) {
            return ValidationResult.retry("Latency too low, possible empty execution");
        }

        return ValidationResult.pass("Rule checkpoint passed");
    }

    private ValidationResult evaluateByLlm(AgentTask task, AgentResult result, Map<String, Object> checkpoint) {
        if (llmClient == null) {
            log.warn("LLM checkpoint requested but no LlmClient provided, treating as pass");
            return ValidationResult.pass("LLM checkpoint skipped: no LlmClient");
        }

        String criteria = String.valueOf(checkpoint.getOrDefault("criteria", "Validate the result is reasonable"));
        String prompt = """
                You are a strict validator. Evaluate whether the following agent output meets the criteria.
                Respond with only PASS or FAIL and a one-sentence reason.

                Agent type: %s
                Task instruction: %s
                Criteria: %s

                Output:
                %s
                """.formatted(task.agentType(), task.instruction(), criteria, result.output());

        try {
            var response = llmClient.call("You are a validator.", List.of(ConversationMessage.userText(prompt)));
            String text = response.extractText().trim().toUpperCase();
            if (text.startsWith("PASS")) {
                return ValidationResult.pass("LLM checkpoint passed");
            }
            return ValidationResult.retry("LLM checkpoint failed: " + text);
        } catch (Exception e) {
            log.error("LLM checkpoint evaluation failed", e);
            return ValidationResult.retry("LLM checkpoint error: " + e.getMessage());
        }
    }
}

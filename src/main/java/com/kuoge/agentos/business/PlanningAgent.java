package com.kuoge.agentos.business;

import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.client.LlmResponse;
import com.kuoge.agentos.core.session.ConversationMessage;
import com.kuoge.agentos.core.tool.ToolRegistry;
import com.kuoge.agentos.orchestration.plan.ExecutionPlan;
import com.kuoge.agentos.orchestration.plan.SubTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 规划 Agent —— 系统的“意图识别 + 子 Agent 生成器”。
 *
 * <p>借鉴 Hermes Agent 的 orchestrator 角色与 Claude Code 的 /plan 命令：
 * 接收自然语言经营目标，识别用户意图、提取关键参数（目标市场、SKU 数量），
 * 输出结构化 {@link ExecutionPlan}，由 {@link com.kuoge.agentos.orchestration.DynamicDagEngine}
 * 进一步转换为可执行的 DAG。
 *
 * <p>创建子 Agent 的触发条件：
 * <ul>
 *   <li>目标包含批量 SKU 经营（选品 → 上架 N 个 SKU）</li>
 *   <li>目标涉及多阶段经营闭环（准入/利润/库存/增长/SEO/UGC/私域/复盘）</li>
 *   <li>某阶段输出需要独立校验（自动插入 checkpoint）</li>
 *   <li>任务需要人工审批（如刊登、增长计划）</li>
 * </ul>
 */
@Slf4j
@Component
public class PlanningAgent extends BaseBusinessAgent {

    private static final int MAX_SKU_LIMIT = 50;

    public PlanningAgent(LlmClient llmClient) {
        super("planning", llmClient);
    }

    /**
     * 基于用户目标生成执行计划。
     *
     * @param tenantId   租户 ID
     * @param workflowId 工作流 ID
     * @param userGoal   自然语言目标，如“本周给美国站自动选 20 个潜力 SKU 并上架”
     * @param toolRegistry 工具注册表（PlanningAgent 本身不需要工具，但接口要求）
     * @return ExecutionPlan
     */
    public ExecutionPlan plan(String tenantId, String workflowId, String userGoal, ToolRegistry toolRegistry) {
        AgentContext context = AgentContext.of(tenantId, "system", "session-plan", workflowId, userGoal, toolRegistry);
        AgentResult result = execute(context);

        if (!result.success()) {
            log.warn("PlanningAgent failed to generate plan, falling back to deterministic plan. Output: {}", result.output());
            return fallbackPlan(workflowId, tenantId, userGoal);
        }

        try {
            return parsePlan(result.output(), workflowId, tenantId, userGoal);
        } catch (Exception e) {
            log.warn("Failed to parse LLM plan, falling back to deterministic plan", e);
            return fallbackPlan(workflowId, tenantId, userGoal);
        }
    }

    @Override
    public AgentResult execute(AgentContext context) {
        String prompt = """
                You are a planning specialist for a cross-border e-commerce multi-agent system.
                Analyze the user goal and produce a structured execution plan in the following JSON format:

                {
                  "targetMarket": "US",
                  "targetSkuCount": 5,
                  "subTasks": [
                    {"taskId": "research", "agentType": "product-research", "instruction": "...", "dependsOn": [], "requiresApproval": false},
                    {"taskId": "sku1-ABC-admission", "agentType": "product-admission", "instruction": "...", "dependsOn": ["research"], "requiresApproval": false, "sku": "ABC"},
                    ...
                  ]
                }

                Available agent types:
                - product-research: market research and candidate SKU selection
                - product-admission: compliance and category check
                - profit-guard: margin calculation
                - inventory-risk: stock and demand forecast
                - natural-growth: organic growth plan
                - seo: SEO optimization
                - ugc: UGC content generation
                - private-domain: private domain strategy
                - listing-publish: publish listing to marketplace
                - data-review: performance review

                Constraints:
                - targetSkuCount must be <= 50.
                - Each taskId must be unique.
                - dependsOn must reference existing taskIds.
                - If the goal mentions "list" / "publish" / "上架", include listing-publish tasks.
                - If the goal mentions "research" / "select" / "选品", include product-research tasks.

                User goal: %s
                """.formatted(context.instruction());

        return runWithRuntime(context, prompt, new ToolRegistry());
    }

    private ExecutionPlan parsePlan(String output, String workflowId, String tenantId, String userGoal) {
        // 提取 JSON 块（允许模型输出 markdown 代码块）
        String json = extractJson(output);
        Map<String, Object> map = parseSimpleJson(json);

        String targetMarket = String.valueOf(map.getOrDefault("targetMarket", "US"));
        int targetSkuCount = parseInt(map.getOrDefault("targetSkuCount", 1), 1);
        targetSkuCount = Math.min(targetSkuCount, MAX_SKU_LIMIT);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawTasks = (List<Map<String, Object>>) map.get("subTasks");
        List<SubTask> subTasks = new ArrayList<>();

        if (rawTasks != null) {
            for (Map<String, Object> raw : rawTasks) {
                subTasks.add(toSubTask(raw));
            }
        }

        if (subTasks.isEmpty()) {
            throw new IllegalStateException("LLM plan contains no subTasks");
        }

        return ExecutionPlan.builder()
                .workflowId(workflowId)
                .tenantId(tenantId)
                .userGoal(userGoal)
                .targetMarket(targetMarket)
                .targetSkuCount(targetSkuCount)
                .subTasks(subTasks)
                .metadata(Map.of("source", "llm-plan"))
                .build();
    }

    private SubTask toSubTask(Map<String, Object> raw) {
        return SubTask.builder()
                .taskId(String.valueOf(raw.get("taskId")))
                .agentType(String.valueOf(raw.get("agentType")))
                .instruction(String.valueOf(raw.getOrDefault("instruction", "")))
                .params(Map.of())
                .dependsOn(parseStringSet(raw.get("dependsOn")))
                .requiresApproval(Boolean.parseBoolean(String.valueOf(raw.getOrDefault("requiresApproval", false))))
                .sku(String.valueOf(raw.getOrDefault("sku", "")))
                .build();
    }

    private ExecutionPlan fallbackPlan(String workflowId, String tenantId, String userGoal) {
        ParsedGoal goal = parseGoal(userGoal);
        String targetMarket = goal.market != null ? goal.market : "US";
        int count = Math.min(goal.count > 0 ? goal.count : 3, MAX_SKU_LIMIT);

        List<String> candidateSkus = generateMockSkus(count);

        return ExecutionPlan.builder()
                .workflowId(workflowId)
                .tenantId(tenantId)
                .userGoal(userGoal)
                .targetMarket(targetMarket)
                .targetSkuCount(count)
                .subTasks(buildFallbackSubTasks(candidateSkus, targetMarket))
                .metadata(Map.of("source", "fallback-plan", "reason", "llm-plan-failed"))
                .build();
    }

    private List<SubTask> buildFallbackSubTasks(List<String> candidateSkus, String targetMarket) {
        List<SubTask> subTasks = new ArrayList<>();

        subTasks.add(SubTask.builder()
                .taskId("research")
                .agentType("product-research")
                .instruction("Research market trends and identify " + candidateSkus.size() + " candidate SKUs for " + targetMarket)
                .params(Map.of("targetMarket", targetMarket))
                .build());

        for (String sku : candidateSkus) {
            subTasks.add(SubTask.builder()
                    .taskId(sku + "-admission")
                    .agentType("product-admission")
                    .instruction("Evaluate admission for SKU=" + sku)
                    .dependsOn(Set.of("research"))
                    .sku(sku)
                    .build());

            subTasks.add(SubTask.builder()
                    .taskId(sku + "-profit")
                    .agentType("profit-guard")
                    .instruction("Compute profit for SKU=" + sku)
                    .dependsOn(Set.of(sku + "-admission"))
                    .sku(sku)
                    .build());

            subTasks.add(SubTask.builder()
                    .taskId(sku + "-publish")
                    .agentType("listing-publish")
                    .instruction("Publish SKU=" + sku + " to " + targetMarket)
                    .dependsOn(Set.of(sku + "-profit"))
                    .sku(sku)
                    .requiresApproval(true)
                    .build());
        }

        return subTasks;
    }

    private ParsedGoal parseGoal(String userGoal) {
        ParsedGoal goal = new ParsedGoal();
        String lower = userGoal.toLowerCase();

        if (lower.contains("us") || lower.contains("美国") || lower.contains("america")) {
            goal.market = "US";
        } else if (lower.contains("eu") || lower.contains("欧洲")) {
            goal.market = "EU";
        } else if (lower.contains("jp") || lower.contains("日本")) {
            goal.market = "JP";
        }

        // 提取数字
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(userGoal);
        if (matcher.find()) {
            goal.count = Integer.parseInt(matcher.group());
        }

        return goal;
    }

    private List<String> generateMockSkus(int count) {
        List<String> skus = new ArrayList<>();
        String[] prefixes = {"ORTH-LOAFER", "RUN-SHOE", "TENNIS-BAG", "YOGA-MAT", "HIKING-BOOT"};
        for (int i = 0; i < count; i++) {
            skus.add(prefixes[i % prefixes.length] + "-" + targetMarket(i) + "-" + String.format("%02d", i + 1));
        }
        return skus;
    }

    private String targetMarket(int index) {
        return index % 2 == 0 ? "US" : "EU";
    }

    private String extractJson(String output) {
        String trimmed = output.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("{");
            int end = trimmed.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isBlank()) return result;
        String content = json.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            content = content.substring(1, content.length() - 1).trim();
        }
        if (content.isEmpty()) return result;

        // 简单按顶层键拆分，不处理嵌套数组/对象的复杂解析
        String[] pairs = splitTopLevelPairs(content);
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = stripQuotes(kv[0].trim());
                String value = kv[1].trim();
                result.put(key, parseValue(value));
            }
        }
        return result;
    }

    private String[] splitTopLevelPairs(String content) {
        List<String> pairs = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char prev = 0;

        for (char c : content.toCharArray()) {
            if (c == '"' && prev != '\\') {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{' || c == '[') depth++;
                if (c == '}' || c == ']') depth--;
                if (c == ',' && depth == 0) {
                    pairs.add(current.toString().trim());
                    current.setLength(0);
                    prev = c;
                    continue;
                }
            }
            current.append(c);
            prev = c;
        }
        if (!current.isEmpty()) {
            pairs.add(current.toString().trim());
        }
        return pairs.toArray(new String[0]);
    }

    private Object parseValue(String value) {
        value = value.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            String inner = value.substring(1, value.length() - 1).trim();
            if (inner.isEmpty()) return List.of();
            List<Object> list = new ArrayList<>();
            for (String item : splitTopLevelPairs(inner)) {
                list.add(parseValue(item));
            }
            return list;
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            return parseSimpleJson(value);
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        if (value.equalsIgnoreCase("null")) return null;
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private int parseInt(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Set<String> parseStringSet(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        if (value instanceof Set<?> set) {
            return set.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of();
    }

    private static class ParsedGoal {
        String market;
        int count;
    }
}

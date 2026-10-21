package com.kuoge.agentos.orchestration;

import com.kuoge.agentos.business.*;
import com.kuoge.agentos.business.tool.MarketplaceToolFactory;
import com.kuoge.agentos.orchestration.plan.SubTask;
import com.kuoge.agentos.core.agent.AgentContext;
import com.kuoge.agentos.core.agent.AgentResult;
import com.kuoge.agentos.core.agent.BusinessAgent;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.memory.InMemorySharedMemory;
import com.kuoge.agentos.core.memory.SharedMemory;
import com.kuoge.agentos.core.tool.ToolRegistry;
import com.kuoge.agentos.infrastructure.persistence.WorkflowPersistenceService;
import com.kuoge.agentos.orchestration.checkpoint.CheckpointEvaluator;
import com.kuoge.agentos.orchestration.kanban.KanbanBoard;
import com.kuoge.agentos.orchestration.kanban.KanbanCard;
import com.kuoge.agentos.orchestration.plan.ExecutionPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CrossBorder电商经营编排器 —— Hermes 风格分层多 Agent 编排入口。
 *
 * <p>主要职责：
 * <ul>
 *   <li>接收自然语言经营目标，调用 {@link com.kuoge.agentos.business.PlanningAgent} 识别意图并生成 {@link ExecutionPlan}；</li>
 *   <li>使用 {@link DynamicDagEngine} 将计划转换为可执行 DAG；</li>
 *   <li>为每个 workflow 创建 {@link SharedMemory} 与 {@link KanbanBoard}，实现 Agent 间上下文共享与状态可视化；</li>
 *   <li>在关键节点调用 {@link CheckpointEvaluator} 校验子 Agent 输出；</li>
 *   <li>保留原固定 DAG 入口 {@link #runProductLifecycle} 以兼容旧调用方。</li>
 * </ul>
 */
@Slf4j
@Service
public class MarketplaceOrchestrator {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final HumanApprovalGateway approvalGateway;
    private final EventBus eventBus;
    private final MarketplaceToolFactory toolFactory;
    private final WorkflowPersistenceService persistenceService;
    private final com.kuoge.agentos.business.PlanningAgent planningAgent;
    private final AgentProfileRegistry profileRegistry;
    private final DynamicDagEngine dagEngine;
    private final CheckpointEvaluator checkpointEvaluator;

    private final Map<String, KanbanBoard> kanbanBoards = new ConcurrentHashMap<>();
    private final Map<String, SharedMemory> sharedMemories = new ConcurrentHashMap<>();

    /**
     * 兼容旧构造器：仅支持固定商品生命周期工作流。
     */
    public MarketplaceOrchestrator(LlmClient llmClient, ToolRegistry toolRegistry,
                                    HumanApprovalGateway approvalGateway, EventBus eventBus) {
        this(llmClient, toolRegistry, approvalGateway, eventBus, null, null,
                null, null, null, null);
    }

    /**
     * 兼容构造器：支持固定工作流 + 工具工厂/持久化。
     */
    public MarketplaceOrchestrator(LlmClient llmClient, ToolRegistry toolRegistry,
                                    HumanApprovalGateway approvalGateway, EventBus eventBus,
                                    MarketplaceToolFactory toolFactory,
                                    WorkflowPersistenceService persistenceService) {
        this(llmClient, toolRegistry, approvalGateway, eventBus, toolFactory, persistenceService,
                null, null, null, null);
    }

    /**
     * 完整构造器：支持动态目标编排。
     */
    public MarketplaceOrchestrator(LlmClient llmClient, ToolRegistry toolRegistry,
                                    HumanApprovalGateway approvalGateway, EventBus eventBus,
                                    MarketplaceToolFactory toolFactory,
                                    WorkflowPersistenceService persistenceService,
                                    com.kuoge.agentos.business.PlanningAgent planningAgent,
                                    AgentProfileRegistry profileRegistry,
                                    DynamicDagEngine dagEngine,
                                    CheckpointEvaluator checkpointEvaluator) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.approvalGateway = approvalGateway;
        this.eventBus = eventBus;
        this.toolFactory = toolFactory;
        this.persistenceService = persistenceService;
        this.planningAgent = planningAgent;
        this.profileRegistry = profileRegistry;
        this.dagEngine = dagEngine;
        this.checkpointEvaluator = checkpointEvaluator;
    }

    /**
     * 核心动态入口：根据自然语言目标自动识别意图并 spawn 子 Agent。
     *
     * <p>典型目标：
     * <pre>本周给美国站自动选 20 个潜力 SKU 并上架</pre>
     */
    public DagScheduler.DagExecutionResult runGoal(String tenantId, String userGoal) {
        String workflowId = "wf-" + UUID.randomUUID().toString().substring(0, 8);
        ToolRegistry effectiveToolRegistry = toolFactory != null ? toolFactory.createToolRegistry() : toolRegistry;

        SharedMemory sharedMemory = new InMemorySharedMemory();
        KanbanBoard board = new KanbanBoard(workflowId);
        sharedMemories.put(workflowId, sharedMemory);
        kanbanBoards.put(workflowId, board);

        if (persistenceService != null) {
            persistenceService.saveWorkflowInstance(workflowId, tenantId, "system",
                    "", "", extractMarket(userGoal), "RUNNING");
        }

        try {
            // 1. 意图识别 + 计划生成
            ExecutionPlan plan = planningAgent != null
                    ? planningAgent.plan(tenantId, workflowId, userGoal, effectiveToolRegistry)
                    : fallbackPlan(workflowId, tenantId, userGoal);

            log.info("[runGoal] workflow={} plan tasks={}", workflowId, plan.subTasks().size());

            // 2. 动态构建 DAG
            ExecutionGraph graph = dagEngine != null
                    ? dagEngine.buildGraph(plan)
                    : new DynamicDagEngine(new AgentProfileRegistry()).buildGraph(plan);

            // 3. 注册所有需要的 Agent（包括新增领域 Agent）
            AgentRegistry registry = buildExtendedRegistry();

            // 4. 初始化 Kanban 看板
            for (AgentTask task : graph.allTasks()) {
                board.addCard(task.taskId(), task.agentType(), task.instruction());
            }

            // 5. 执行
            DagScheduler scheduler = new DagScheduler(registry, approvalGateway, 4, ReplanPolicy.stopOnFailure(),
                    checkpointEvaluator, board);
            AgentContext context = new AgentContext(
                    tenantId, "system", "session-" + workflowId, workflowId,
                    userGoal,
                    effectiveToolRegistry,
                    Map.of("workflowId", workflowId, "userGoal", userGoal),
                    sharedMemory
            );

            DagScheduler.DagExecutionResult result = scheduler.execute(graph, context);

            // 6. 校验与事件
            evaluateCheckpoints(graph, result, plan);
            eventBus.publish("goal.completed", new EventBus.AgentEvent(
                    "goal.completed", workflowId, "orchestrator", result));

            if (persistenceService != null) {
                persistenceService.persistResult(workflowId, tenantId, result);
            }
            return result;
        } catch (Exception e) {
            log.error("[runGoal] workflow={} failed", workflowId, e);
            if (persistenceService != null) {
                persistenceService.updateWorkflowStatus(workflowId, "FAILED", e.getMessage());
            }
            throw e;
        } finally {
            // sharedMemory / board 保留，供查询接口使用
        }
    }

    /**
     * 固定商品生命周期 DAG（兼容旧入口）。
     */
    public DagScheduler.DagExecutionResult runProductLifecycle(String tenantId, String productSku,
                                                                String productName, String targetMarket) {
        String workflowId = "wf-" + UUID.randomUUID().toString().substring(0, 8);
        ToolRegistry effectiveToolRegistry = toolFactory != null ? toolFactory.createToolRegistry() : toolRegistry;

        SharedMemory sharedMemory = new InMemorySharedMemory();
        KanbanBoard board = new KanbanBoard(workflowId);
        sharedMemories.put(workflowId, sharedMemory);
        kanbanBoards.put(workflowId, board);

        if (persistenceService != null) {
            persistenceService.saveWorkflowInstance(workflowId, tenantId, "system",
                    productSku, productName, targetMarket, "RUNNING");
        }

        AgentRegistry registry = buildExtendedRegistry();
        DagScheduler scheduler = new DagScheduler(registry, approvalGateway, 4, ReplanPolicy.stopOnFailure(),
                checkpointEvaluator, board);

        ExecutionGraph graph = new ExecutionGraph()
                .addTask(AgentTask.builder()
                        .taskId("t1-admission")
                        .taskType("product-admission")
                        .agentType("product-admission")
                        .instruction("Evaluate product admission for SKU=" + productSku + " name=" + productName)
                        .allowedTools(List.of("verify_certification", "check_category", "analyze_images"))
                        .build())
                .addTask(AgentTask.builder()
                        .taskId("t2-profit")
                        .taskType("profit-guard")
                        .agentType("profit-guard")
                        .instruction("Compute profit margin for SKU=" + productSku)
                        .dependsOn(Set.of("t1-admission"))
                        .allowedTools(List.of("query_cost", "query_logistics", "query_commission"))
                        .build())
                .addTask(AgentTask.builder()
                        .taskId("t3-inventory")
                        .taskType("inventory-risk")
                        .agentType("inventory-risk")
                        .instruction("Assess inventory risk for SKU=" + productSku)
                        .dependsOn(Set.of("t1-admission"))
                        .allowedTools(List.of("query_stock", "forecast_demand"))
                        .build())
                .addTask(AgentTask.builder()
                        .taskId("t4-growth")
                        .taskType("natural-growth")
                        .agentType("natural-growth")
                        .instruction("Create natural growth plan for SKU=" + productSku + " market=" + targetMarket)
                        .dependsOn(Set.of("t2-profit", "t3-inventory"))
                        .requiresApproval(true)
                        .allowedTools(List.of("generate_title", "generate_keywords", "generate_blog"))
                        .build())
                .addTask(AgentTask.builder()
                        .taskId("t5-seo")
                        .taskType("seo")
                        .agentType("seo")
                        .instruction("Optimize SEO for SKU=" + productSku)
                        .dependsOn(Set.of("t4-growth"))
                        .allowedTools(List.of("keyword_research", "optimize_meta"))
                        .build())
                .addTask(AgentTask.builder()
                        .taskId("t6-ugc")
                        .taskType("ugc")
                        .agentType("ugc")
                        .instruction("Generate UGC content for SKU=" + productSku)
                        .dependsOn(Set.of("t4-growth"))
                        .allowedTools(List.of("generate_review", "generate_social_post"))
                        .build())
                .addTask(AgentTask.builder()
                        .taskId("t7-private-domain")
                        .taskType("private-domain")
                        .agentType("private-domain")
                        .instruction("Build private domain strategy for SKU=" + productSku)
                        .dependsOn(Set.of("t4-growth"))
                        .allowedTools(List.of("build_email_sequence", "segment_users"))
                        .build())
                .addTask(AgentTask.builder()
                        .taskId("t8-review")
                        .taskType("data-review")
                        .agentType("data-review")
                        .instruction("Review 7-day performance for SKU=" + productSku)
                        .dependsOn(Set.of("t5-seo", "t6-ugc", "t7-private-domain"))
                        .allowedTools(List.of("query_gmv", "query_traffic", "query_conversion"))
                        .build());

        for (AgentTask task : graph.allTasks()) {
            board.addCard(task.taskId(), task.agentType(), task.instruction());
        }

        AgentContext context = new AgentContext(
                tenantId, "system", "session-" + productSku, workflowId,
                "Run product lifecycle",
                effectiveToolRegistry,
                Map.of("productSku", productSku, "productName", productName, "targetMarket", targetMarket),
                sharedMemory
        );

        try {
            DagScheduler.DagExecutionResult result = scheduler.execute(graph, context);
            eventBus.publish("lifecycle.completed", new EventBus.AgentEvent(
                    "lifecycle.completed", workflowId, "orchestrator", result));
            if (persistenceService != null) {
                persistenceService.persistResult(workflowId, tenantId, result);
            }
            return result;
        } catch (Exception e) {
            if (persistenceService != null) {
                persistenceService.updateWorkflowStatus(workflowId, "FAILED", e.getMessage());
            }
            throw e;
        } finally {
            scheduler.shutdown();
        }
    }

    public AgentRegistry buildExtendedRegistry() {
        AgentRegistry registry = new AgentRegistry();
        registry.register("planning", new com.kuoge.agentos.business.PlanningAgent(llmClient));
        registry.register("product-research", new ProductResearchAgent(llmClient));
        registry.register("product-admission", new ProductAdmissionAgent(llmClient));
        registry.register("profit-guard", new ProfitGuardAgent(llmClient));
        registry.register("natural-growth", new NaturalGrowthAgent(llmClient, approvalGateway));
        registry.register("inventory-risk", new InventoryRiskAgent(llmClient));
        registry.register("seo", new SeoAgent(llmClient));
        registry.register("ugc", new UgcAgent(llmClient));
        registry.register("private-domain", new PrivateDomainAgent(llmClient));
        registry.register("listing-publish", new ListingPublishAgent(llmClient));
        registry.register("data-review", new DataReviewAgent(llmClient));
        return registry;
    }

    public Optional<KanbanBoard> getKanbanBoard(String workflowId) {
        return Optional.ofNullable(kanbanBoards.get(workflowId));
    }

    public Optional<SharedMemory> getSharedMemory(String workflowId) {
        return Optional.ofNullable(sharedMemories.get(workflowId));
    }

    public Map<String, KanbanBoard> getKanbanBoardsSnapshot() {
        return Map.copyOf(kanbanBoards);
    }

    private ExecutionPlan fallbackPlan(String workflowId, String tenantId, String userGoal) {
        log.warn("PlanningAgent not available, using deterministic fallback plan");
        DynamicDagEngine engine = new DynamicDagEngine(new AgentProfileRegistry());
        return engine.buildStandardLifecyclePlan(workflowId, tenantId, extractMarket(userGoal),
                List.of("FALLBACK-SKU-001"));
    }

    private String extractMarket(String userGoal) {
        String lower = userGoal.toLowerCase();
        if (lower.contains("us") || lower.contains("美国")) return "US";
        if (lower.contains("eu") || lower.contains("欧洲")) return "EU";
        if (lower.contains("jp") || lower.contains("日本")) return "JP";
        return "US";
    }

    private void evaluateCheckpoints(ExecutionGraph graph, DagScheduler.DagExecutionResult result, ExecutionPlan plan) {
        if (checkpointEvaluator == null) return;

        for (AgentTask task : graph.allTasks()) {
            AgentResult agentResult = result.results().get(task.taskId());
            if (agentResult == null) continue;

            SubTask subTask = plan.subTasks().stream()
                    .filter(s -> s.taskId().equals(task.taskId()))
                    .findFirst()
                    .orElse(null);
            if (subTask == null || subTask.checkpointId().isBlank()) continue;

            // 简单规则：checkpointId 本身作为 mustContain 规则
            Map<String, Object> checkpoint = Map.of("type", "rule", "mustContain", subTask.checkpointId());
            var validation = checkpointEvaluator.evaluate(task, agentResult, checkpoint);
            log.info("[checkpoint] task={} passed={} action={}",
                    task.taskId(), validation.passed(), validation.action());
        }
    }
}

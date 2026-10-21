package com.kuoge.agentos.infrastructure.config;

import com.kuoge.agentos.business.PlanningAgent;
import com.kuoge.agentos.business.tool.MarketplaceToolFactory;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.core.tool.ToolRegistry;
import com.kuoge.agentos.infrastructure.feishu.FeishuApprovalGateway;
import com.kuoge.agentos.infrastructure.persistence.WorkflowPersistenceService;
import com.kuoge.agentos.orchestration.*;
import com.kuoge.agentos.orchestration.checkpoint.CheckpointEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent-OS 核心 Bean 装配配置。
 */
@Configuration
public class AgentOsConfig {

    @Bean
    public ToolRegistry toolRegistry(MarketplaceToolFactory toolFactory) {
        return toolFactory.createToolRegistry();
    }

    @Bean
    public MarketplaceOrchestrator marketplaceOrchestrator(
            LlmClient llmClient,
            ToolRegistry toolRegistry,
            FeishuApprovalGateway approvalGateway,
            EventBus eventBus,
            MarketplaceToolFactory toolFactory,
            WorkflowPersistenceService persistenceService,
            PlanningAgent planningAgent,
            AgentProfileRegistry profileRegistry,
            DynamicDagEngine dagEngine,
            CheckpointEvaluator checkpointEvaluator) {
        return new MarketplaceOrchestrator(llmClient, toolRegistry, approvalGateway, eventBus,
                toolFactory, persistenceService, planningAgent, profileRegistry, dagEngine, checkpointEvaluator);
    }
}

package com.kuoge.agentos.core.agent;

/**
 * 业务 Agent 接口。
 */
public interface BusinessAgent {

    String agentType();

    AgentResult execute(AgentContext context);
}

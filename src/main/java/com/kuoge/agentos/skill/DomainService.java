package com.kuoge.agentos.skill;

/**
 * 确定性服务 SPI（方案 3.1）：无 LLM 参与。
 * 写操作只受状态机 + 网关约束，不经由模型决策；框架无关，不 import 任何 Agent 框架的包。
 */
public interface DomainService<I, O> {

    O execute(SkillContext ctx, I input);
}

package com.kuoge.agentos.skill;

/**
 * AI Skill SPI（方案 3.1）：LLM 在环。
 * 输出一律为草稿 / 建议 / 假设，不直接写业务状态；
 * 所有生效动作经确认后由确定性服务执行（两条写权限铁律之一）。
 */
public interface AiSkill<I, O> {

    SkillResult<O> run(SkillContext ctx, I input);
}

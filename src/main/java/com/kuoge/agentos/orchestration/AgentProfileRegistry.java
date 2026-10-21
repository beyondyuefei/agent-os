package com.kuoge.agentos.orchestration;

import com.kuoge.agentos.core.agent.profile.AgentProfile;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Agent Profile 注册中心 —— Hermes 风格的 Agent Identity 管理。
 *
 * <p>PlanningAgent 在生成执行计划时，通过此注册中心查询每个 agentType 的 Profile，
 * 从而决定子 Agent 的系统提示词、可用工具与审批策略。
 */
@Component
public class AgentProfileRegistry {

    private final Map<String, AgentProfile> profiles = new ConcurrentHashMap<>();

    public AgentProfileRegistry() {
        registerDefaults();
    }

    public AgentProfileRegistry register(AgentProfile profile) {
        profiles.put(profile.agentType(), profile);
        return this;
    }

    public Optional<AgentProfile> resolve(String agentType) {
        return Optional.ofNullable(profiles.get(agentType));
    }

    public AgentProfile require(String agentType) {
        return resolve(agentType)
                .orElseThrow(() -> new IllegalStateException("Unknown agent profile: " + agentType));
    }

    private void registerDefaults() {
        register(AgentProfile.builder()
                .agentType("product-research")
                .name("Product Research Agent")
                .systemPrompt("""
                        You are a cross-border e-commerce product researcher.
                        Analyze market trends, competition, and demand to identify promising SKUs.
                        Return a structured list of candidate SKUs with reasoning.
                        """)
                .allowedTools(Set.of("research_market", "analyze_competition", "score_product"))
                .modelHint("gpt-4o-mini")
                .build());

        register(AgentProfile.builder()
                .agentType("product-admission")
                .name("Product Admission Agent")
                .systemPrompt("""
                        You are a product admission specialist for cross-border e-commerce.
                        Evaluate compliance, category fit, content assets and logistics readiness.
                        Return a concise admission verdict.
                        """)
                .allowedTools(Set.of("verify_certification", "check_category", "analyze_images"))
                .modelHint("gpt-4o-mini")
                .build());

        register(AgentProfile.builder()
                .agentType("profit-guard")
                .name("Profit Guard Agent")
                .systemPrompt("""
                        You are a profit guard specialist.
                        Compute product cost, logistics, platform commission, tax and gross margin.
                        If margin is below target, mark as risky and explain.
                        """)
                .allowedTools(Set.of("query_cost", "query_logistics", "query_commission"))
                .modelHint("gpt-4o-mini")
                .build());

        register(AgentProfile.builder()
                .agentType("inventory-risk")
                .name("Inventory Risk Agent")
                .systemPrompt("""
                        You are an inventory risk analyst.
                        Check current stock and forecast demand, then flag overstock or stockout risks.
                        """)
                .allowedTools(Set.of("query_stock", "forecast_demand"))
                .modelHint("gpt-4o-mini")
                .build());

        register(AgentProfile.builder()
                .agentType("natural-growth")
                .name("Natural Growth Agent")
                .systemPrompt("""
                        You are a natural growth strategist.
                        Create an organic growth plan including listing title, keywords, blog topics and UGC angles.
                        Return the plan in structured format.
                        """)
                .allowedTools(Set.of("generate_title", "generate_keywords", "generate_blog"))
                .requiresApproval(true)
                .modelHint("gpt-4o-mini")
                .build());

        register(AgentProfile.builder()
                .agentType("seo")
                .name("SEO Agent")
                .systemPrompt("""
                        You are an SEO specialist.
                        Research keywords and optimize meta title/description for the product listing.
                        """)
                .allowedTools(Set.of("keyword_research", "optimize_meta"))
                .modelHint("gpt-4o-mini")
                .build());

        register(AgentProfile.builder()
                .agentType("ugc")
                .name("UGC Agent")
                .systemPrompt("""
                        You are a UGC content generator.
                        Create authentic-looking product reviews and social media posts.
                        """)
                .allowedTools(Set.of("generate_review", "generate_social_post"))
                .modelHint("gpt-4o-mini")
                .build());

        register(AgentProfile.builder()
                .agentType("private-domain")
                .name("Private Domain Agent")
                .systemPrompt("""
                        You are a private-domain marketing specialist.
                        Build an email sequence and user segmentation plan to improve retention.
                        """)
                .allowedTools(Set.of("build_email_sequence", "segment_users"))
                .modelHint("gpt-4o-mini")
                .build());

        register(AgentProfile.builder()
                .agentType("listing-publish")
                .name("Listing Publish Agent")
                .systemPrompt("""
                        You are a marketplace listing publisher.
                        Publish the product listing to the target platform, sync images and inventory.
                        Return the published SKU and platform listing URL if available.
                        """)
                .allowedTools(Set.of("publish_listing", "sync_images", "update_inventory"))
                .requiresApproval(true)
                .modelHint("gpt-4o-mini")
                .build());

        register(AgentProfile.builder()
                .agentType("data-review")
                .name("Data Review Agent")
                .systemPrompt("""
                        You are a data analyst.
                        Review 7-day GMV, traffic and conversion data, then provide optimization suggestions.
                        """)
                .allowedTools(Set.of("query_gmv", "query_traffic", "query_conversion"))
                .modelHint("gpt-4o-mini")
                .build());
    }
}

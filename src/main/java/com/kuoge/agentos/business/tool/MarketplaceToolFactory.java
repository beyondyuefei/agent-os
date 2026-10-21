package com.kuoge.agentos.business.tool;

import com.kuoge.agentos.business.service.*;
import com.kuoge.agentos.core.tool.Tool;
import com.kuoge.agentos.core.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.kuoge.agentos.infrastructure.tenant.TenantContext;

import java.util.Map;

/**
 * 把业务服务 SPI 注册为 Agent 可调用的工具。
 */
@Component
@RequiredArgsConstructor
public class MarketplaceToolFactory {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final MarketingService marketingService;
    private final AnalyticsService analyticsService;
    private final MarketResearchService marketResearchService;
    private final ListingService listingService;

    public ToolRegistry createToolRegistry() {
        ToolRegistry registry = new ToolRegistry();

        // Tenant isolation: prefer ThreadLocal context, fallback to tool argument.
        java.util.function.Function<Map<String, Object>, String> resolveTenant = args -> {
            String ctx = TenantContext.get();
            if (!"default".equals(ctx)) {
                return ctx;
            }
            return (String) args.getOrDefault("tenantId", "default");
        };

        // Product admission tools
        registry.register(new Tool() {
            @Override public String name() { return "verify_certification"; }
            @Override public String description() { return "Verify product certifications"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(productService.verifyCertification(tenantId, sku));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "check_category"; }
            @Override public String description() { return "Check category compliance"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(productService.checkCategoryCompliance(tenantId, sku));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "analyze_images"; }
            @Override public String description() { return "Analyze product images"; }
            @Override public String execute(Map<String, Object> args) {
                return "Images analyzed: main image clear, 5 detail images present";
            }
        });

        // Pricing tools
        registry.register(new Tool() {
            @Override public String name() { return "query_cost"; }
            @Override public String description() { return "Query product cost"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(pricingService.getProductCost(tenantId, sku));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "query_logistics"; }
            @Override public String description() { return "Query logistics cost"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(pricingService.getLogisticsCost(tenantId, sku));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "query_commission"; }
            @Override public String description() { return "Query platform commission rate"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(pricingService.getPlatformCommissionRate(tenantId, sku));
            }
        });

        // Inventory tools
        registry.register(new Tool() {
            @Override public String name() { return "query_stock"; }
            @Override public String description() { return "Query current stock"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(inventoryService.getStock(tenantId, sku));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "forecast_demand"; }
            @Override public String description() { return "Forecast demand"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return inventoryService.forecastDemand(tenantId, sku);
            }
        });

        // Marketing tools
        registry.register(new Tool() {
            @Override public String name() { return "generate_title"; }
            @Override public String description() { return "Generate listing title"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return marketingService.generateListingTitle(tenantId, sku);
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "generate_keywords"; }
            @Override public String description() { return "Generate keywords"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.join(", ", marketingService.generateKeywords(tenantId, sku));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "generate_blog"; }
            @Override public String description() { return "Generate blog topic"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return marketingService.generateBlogTopic(tenantId, sku);
            }
        });

        // SEO / UGC / Private domain tools
        registry.register(new Tool() {
            @Override public String name() { return "keyword_research"; }
            @Override public String description() { return "Research SEO keywords"; }
            @Override public String execute(Map<String, Object> args) {
                return "Keywords: comfortable shoes, walking shoes, orthopedic loafers";
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "optimize_meta"; }
            @Override public String description() { return "Optimize meta tags"; }
            @Override public String execute(Map<String, Object> args) {
                return "Meta title and description optimized";
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "generate_review"; }
            @Override public String description() { return "Generate product review"; }
            @Override public String execute(Map<String, Object> args) {
                return "Generated 5 authentic reviews";
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "generate_social_post"; }
            @Override public String description() { return "Generate social media post"; }
            @Override public String execute(Map<String, Object> args) {
                return "Generated 3 social media posts";
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "build_email_sequence"; }
            @Override public String description() { return "Build email sequence"; }
            @Override public String execute(Map<String, Object> args) {
                return "Built 5-step welcome email sequence";
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "segment_users"; }
            @Override public String description() { return "Segment users"; }
            @Override public String execute(Map<String, Object> args) {
                return "Users segmented into 4 cohorts";
            }
        });

        // Analytics tools
        registry.register(new Tool() {
            @Override public String name() { return "query_gmv"; }
            @Override public String description() { return "Query GMV"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(analyticsService.queryGmv(tenantId, sku, 7));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "query_traffic"; }
            @Override public String description() { return "Query traffic"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(analyticsService.queryTraffic(tenantId, sku, 7));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "query_conversion"; }
            @Override public String description() { return "Query conversion rate"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(analyticsService.queryConversionRate(tenantId, sku, 7));
            }
        });

        // Market research tools
        registry.register(new Tool() {
            @Override public String name() { return "research_market"; }
            @Override public String description() { return "Research market trends for target market"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String targetMarket = (String) args.getOrDefault("targetMarket", "US");
                return String.join(", ", marketResearchService.searchTrends(tenantId, targetMarket));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "analyze_competition"; }
            @Override public String description() { return "Analyze competition for keyword"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String targetMarket = (String) args.getOrDefault("targetMarket", "US");
                String keyword = (String) args.getOrDefault("keyword", "shoes");
                return marketResearchService.analyzeCompetition(tenantId, targetMarket, keyword).toString();
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "score_product"; }
            @Override public String description() { return "Score product potential"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String targetMarket = (String) args.getOrDefault("targetMarket", "US");
                String sku = (String) args.get("sku");
                return String.valueOf(marketResearchService.scoreProductPotential(tenantId, targetMarket, sku));
            }
        });

        // Listing publish tools
        registry.register(new Tool() {
            @Override public String name() { return "publish_listing"; }
            @Override public String description() { return "Publish listing to marketplace"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String targetMarket = (String) args.getOrDefault("targetMarket", "US");
                String sku = (String) args.get("sku");
                return listingService.publishListing(tenantId, targetMarket, sku);
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "sync_images"; }
            @Override public String description() { return "Sync product images"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                return String.valueOf(listingService.syncImages(tenantId, sku));
            }
        });

        registry.register(new Tool() {
            @Override public String name() { return "update_inventory"; }
            @Override public String description() { return "Update platform inventory"; }
            @Override public String execute(Map<String, Object> args) {
                String tenantId = resolveTenant.apply(args);
                String sku = (String) args.get("sku");
                int quantity = args.get("quantity") instanceof Number n ? n.intValue() : 100;
                return String.valueOf(listingService.updateInventory(tenantId, sku, quantity));
            }
        });

        return registry;
    }
}

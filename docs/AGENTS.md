# Agent-OS Agent Profile 与技能定义

> 借鉴 Claude Code 的 `CLAUDE.md` / `.claude/agents/` 与 Hermes Agent 的 Profile / Skill 思想，
> 本文档集中定义 Agent-OS 中各子 Agent 的身份、系统提示词、可用工具与审批策略。

---

## 1. 设计原则

- **单一职责**：每个 Agent 只负责一个明确的业务或技术领域。
- **上下文隔离**：Agent 仅接收与其任务相关的工具和共享内存子集。
- **显式审批**：涉及真实平台写操作（刊登、库存变更、价格调整）的 Agent 默认需要人工审批。
- **可组合**：通过 `PlanningAgent` 根据用户目标动态选择并组合 Agent。

---

## 2. Agent Profile 总览

| Agent Type | 名称 | 核心职责 | 是否需审批 |
|---|---|---|---|
| `planning` | Planning Agent | 意图识别 + 执行计划生成 | 否 |
| `product-research` | Product Research Agent | 市场趋势、竞品分析、SKU 潜力评分 | 否 |
| `product-admission` | Product Admission Agent | 资质、类目、素材合规审查 | 否 |
| `profit-guard` | Profit Guard Agent | 成本、物流、佣金、毛利率核算 | 否 |
| `inventory-risk` | Inventory Risk Agent | 库存查询与需求预测 | 否 |
| `natural-growth` | Natural Growth Agent | 自然流增长计划（标题/关键词/博客） | 是 |
| `seo` | SEO Agent | 关键词研究与 Meta 优化 | 否 |
| `ugc` | UGC Agent | 评论与社媒内容生成 | 否 |
| `private-domain` | Private Domain Agent | 邮件序列与用户分层 | 否 |
| `listing-publish` | Listing Publish Agent | 刊登商品到目标市场 | 是 |
| `data-review` | Data Review Agent | GMV/流量/转化率复盘 | 否 |

---

## 3. 核心 Agent 详情

### 3.1 Planning Agent

- **类型**：编排型（Orchestrator）
- **职责**：
  - 解析自然语言经营目标；
  - 提取目标市场、SKU 数量等关键参数；
  - 输出结构化 `ExecutionPlan`；
  - 决定创建哪些子 Agent、它们的依赖关系与审批策略。
- **触发子 Agent 创建的条件**：
  1. 目标涉及批量 SKU 经营（选品 → 上架 N 个 SKU）；
  2. 目标涉及多阶段经营闭环；
  3. 某阶段输出需要独立校验；
  4. 任务需要人工审批。
- **系统提示词摘要**：
  > You are a planning specialist for a cross-border e-commerce multi-agent system.
  > Analyze the user goal and produce a structured execution plan in JSON format.

### 3.2 Product Research Agent

- **类型**：领域型
- **职责**：基于市场趋势、竞品强度、SKU 潜力评分，输出候选 SKU 列表。
- **可用工具**：
  - `research_market`
  - `analyze_competition`
  - `score_product`
- **共享内存写入**：
  - `candidateSkus`：候选 SKU 列表
  - `researchMarket`：目标市场

### 3.3 Listing Publish Agent

- **类型**：领域型
- **职责**：调用刊登服务，将商品发布到目标市场。
- **可用工具**：
  - `publish_listing`
  - `sync_images`
  - `update_inventory`
- **共享内存写入**：
  - `published-{sku}`：刊登结果
- **审批要求**：是（涉及真实平台写操作）

### 3.4 Natural Growth Agent

- **类型**：领域型
- **职责**：生成 Listing 标题、关键词、博客选题、UGC 角度。
- **可用工具**：
  - `generate_title`
  - `generate_keywords`
  - `generate_blog`
- **审批要求**：是

---

## 4. 工具技能清单

### 4.1 选品研究

| Tool | 服务 SPI | 说明 |
|---|---|---|
| `research_market` | `MarketResearchService.searchTrends` | 搜索目标市场趋势 |
| `analyze_competition` | `MarketResearchService.analyzeCompetition` | 分析关键词竞争强度 |
| `score_product` | `MarketResearchService.scoreProductPotential` | SKU 潜力评分 |

### 4.2 商品准入

| Tool | 服务 SPI | 说明 |
|---|---|---|
| `verify_certification` | `ProductService.verifyCertification` | 校验商品资质 |
| `check_category` | `ProductService.checkCategoryCompliance` | 校验类目合规 |
| `analyze_images` | - | 分析商品图片 |

### 4.3 利润与库存

| Tool | 服务 SPI | 说明 |
|---|---|---|
| `query_cost` | `PricingService.getProductCost` | 查询成本 |
| `query_logistics` | `PricingService.getLogisticsCost` | 查询物流成本 |
| `query_commission` | `PricingService.getPlatformCommissionRate` | 查询佣金率 |
| `query_stock` | `InventoryService.getStock` | 查询库存 |
| `forecast_demand` | `InventoryService.forecastDemand` | 预测需求 |

### 4.4 增长与内容

| Tool | 服务 SPI | 说明 |
|---|---|---|
| `generate_title` | `MarketingService.generateListingTitle` | 生成 Listing 标题 |
| `generate_keywords` | `MarketingService.generateKeywords` | 生成关键词 |
| `generate_blog` | `MarketingService.generateBlogTopic` | 生成博客选题 |
| `keyword_research` | - | SEO 关键词研究 |
| `optimize_meta` | - | 优化 Meta 信息 |
| `generate_review` | - | 生成评论 |
| `generate_social_post` | - | 生成社媒文案 |
| `build_email_sequence` | - | 构建邮件序列 |
| `segment_users` | - | 用户分层 |

### 4.5 刊登

| Tool | 服务 SPI | 说明 |
|---|---|---|
| `publish_listing` | `ListingService.publishListing` | 发布 Listing |
| `sync_images` | `ListingService.syncImages` | 同步图片 |
| `update_inventory` | `ListingService.updateInventory` | 更新库存 |

### 4.6 数据复盘

| Tool | 服务 SPI | 说明 |
|---|---|---|
| `query_gmv` | `AnalyticsService.queryGmv` | 查询 GMV |
| `query_traffic` | `AnalyticsService.queryTraffic` | 查询流量 |
| `query_conversion` | `AnalyticsService.queryConversionRate` | 查询转化率 |

---

## 5. 共享内存约定

Agent 在执行过程中可将关键结果写入共享内存，供下游 Agent 读取。

| Key | 写入者 | 含义 | 消费者 |
|---|---|---|---|
| `candidateSkus` | `product-research` | 候选 SKU 列表 | `product-admission`, `profit-guard`, `listing-publish` |
| `researchMarket` | `product-research` | 目标市场 | 下游所有 Agent |
| `published-{sku}` | `listing-publish` | 刊登结果 | `data-review` |

---

## 6. 验收检查点

每个 `SubTask` 可声明 `checkpointId`，`CheckpointEvaluator` 在任务完成后进行校验：

- `PASS`：继续执行；
- `RETRY`：触发 `ReplanPolicy` 重试；
- `SKIP`：标记为跳过；
- `STOP`：终止工作流。

---

## 7. 调用示例

### 7.1 启动自然语言目标工作流

```bash
curl -X POST "http://localhost:8080/api/v1/workflows/goal" \
  -H "X-Tenant-Id: tenant-1" \
  -d "tenantId=tenant-1" \
  -d "goal=本周给美国站自动选 20 个潜力 SKU 并上架"
```

### 7.2 查看 Kanban 看板

```bash
curl "http://localhost:8080/api/v1/workflows/wf-xxxx/kanban"
```

### 7.3 查看共享内存

```bash
curl "http://localhost:8080/api/v1/workflows/wf-xxxx/memory"
```

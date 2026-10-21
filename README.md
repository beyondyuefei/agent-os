# Agent-OS

> Multi-Agent Operating System for Cross-border E-commerce
> 面向跨境电商经营场景的多智能体操作系统

[![Java](https://img.shields.io/badge/Java-21-blue)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.12-green)]()
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.1-orange)]()

## 项目简介

Agent-OS 把围绕一款商品的**完整经营闭环**抽象为可编排、可执行、可观测的多 Agent 工作流。系统将选品研究、准入、利润核算、库存风控、自然增长、SEO、UGC、私域、刊登发布、数据复盘等环节交由多个业务 Agent 协同完成，并通过 DAG 调度、人工审批、权限策略和用量追踪保证整个过程**有序、可控、可计量**。

### 核心能力

| 能力 | 说明 |
|---|---|
| DAG 编排 | 声明依赖、并行执行、失败自动重规划（`DagScheduler` / `DynamicDagEngine`） |
| ReAct 运行时 | LLM 通过 Tool Registry 直接调用真实业务系统（ERP/OMS/BI） |
| 人在回路 | 高风险操作（调价、库存变更、投放）走飞书审批网关 |
| 用量追踪 | 统一计量每次 LLM / Tool 调用的 Token 与次数（`UsageTracker`） |
| 多租户隔离 | `TenantContext` + 请求拦截器，工具执行自动携带租户上下文 |
| 可测试性 | Mock LLM / Mock 业务服务 + Mock Mode 开关，支持分层测试 |

## 技术栈

- **Java 21** · Spring Boot 3.5.12 · Spring AI 2.0.1（OpenAI）
- Spring Data JPA + H2（默认嵌入式，可替换）
- Lombok · Maven

## 架构分层

```
├── api/              接入层：MarketplaceController、飞书审批回调、租户拦截器
├── orchestration/    编排层：DAG 调度器、动态 DAG 引擎、审批网关、事件总线、任务看板
├── business/         业务 Agent 层：10+ 经营 Agent + 业务服务 SPI（含 Psylos1 实现与 Mock）
├── core/             核心引擎：ReAct 运行时、会话、工具注册、权限、Hook、共享内存、用量追踪
├── domain/           领域模型：Product、Sku、GrowthPlan、ProfitReport 等
└── infrastructure/   基础设施：Spring AI 适配器、JPA 持久化、飞书客户端、租户上下文
```

详细设计见 [docs/architecture.md](docs/architecture.md)。

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+

### 构建与测试

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package
```

### 运行

```bash
mvn spring-boot:run
```

默认使用 Mock LLM 与 Mock 业务服务，无需外部依赖即可启动体验完整工作流；接入真实 OpenAI 与 Psylos1 业务系统时，在 `application.yml` 中配置对应密钥与 Mock Mode 开关。

## 业务 Agent 一览

| Agent | 职责 |
|---|---|
| PlanningAgent | 意图识别与任务计划 |
| ProductResearchAgent | 选品研究 |
| ProductAdmissionAgent | 商品准入 |
| ProfitGuardAgent | 利润核算与守护 |
| InventoryRiskAgent | 库存风控 |
| NaturalGrowthAgent | 自然增长 |
| SeoAgent | 搜索优化 |
| UgcAgent | UGC 内容 |
| PrivateDomainAgent | 私域运营 |
| ListingPublishAgent | 刊登发布 |
| DataReviewAgent | 数据复盘 |

## 文档

- [架构设计文档](docs/architecture.md)
- [AGENTS.md](docs/AGENTS.md)

## License

待定（暂未指定开源协议）

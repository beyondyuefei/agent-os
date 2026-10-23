# Skill 体系设计方案

> 适用范围：`com.kuoge.agentos.skill.md` 包、`resources/skills/` 契约目录、
> `skill_template` / `skill_template_version` 两张表、`/api/admin/skills` 运营后台。
> 本文档是该体系的唯一事实来源；改设计先改本文档。

## 1. 定位：与 Claude Code / AgentSkills 的异同

本工程借鉴 AgentSkills 规范（SKILL.md 目录约定、三级渐进披露），但解决的问题不同：

| 维度 | Claude Code / AgentSkills | 本工程 |
|---|---|---|
| 回答的问题 | 这份知识此刻该不该进模型的脑子（加载时机） | 这段话此刻该不该从系统嘴里说出去（变更治理） |
| 内容的消费者 | 模型（读完进上下文，自行理解） | 代码（渲染后发给模型或用户，是接口输出物） |
| 核心机制 | 三级渐进披露：metadata 常驻 → body 触发加载 → references 按需 | 同构借用：contract 常驻 → 内容按需（DB 优先）→ 渲染即用 |
| 触发方式 | 模型按 description 语义匹配（概率性） | 代码显式调用 + 能力网关判定矩阵（确定性） |
| 变更治理 | git 评审即可（作者即使用者，改错影响一次会话） | 占位符契约 + 版本快照 + 评审流 + 秒级回滚（改错可能烧广告预算） |
| 执行模型 | skill 可携带 scripts/ 由 Bash 执行（指令即程序） | 执行永远是 Java 类（DomainService / AiSkill），模板只是数据 |

一句话：**对下**借用 AgentSkills 的文件约定组织内容，**对上**用配置中心式的治理控制变更。

## 2. 总体架构

```
contract.yaml（jar 内）                    skill_template_version 表
契约定义 + 默认内容                         草稿 DRAFT / 发布 PUBLISHED 快照
        │ 首次启动种子导入                          │ 发布 / 回滚写入
        ▼                                          ▼
              skill_template 表（运行时唯一数据源）
                        ▲
                        │ render(name, templateKey, vars)
              SkillLibrary（契约常驻，内容按需）
                        ▲
        ReviewDraftSkill / EvolveSuggestSkill / ExperienceDistillSkill
                        ▲
              能力网关判定（套餐 / 撤权 / 预算 / 批准）
```

数据流向单向：`契约评审（PR）→ 种子入库 → 运营草稿 → 评审单号发布 → 快照 → 可回滚`。
没有回环、没有双写。

## 3. 契约规范（contract.yaml）

每个 skill 一份 `resources/skills/<name>/contract.yaml`：

```yaml
name: ad-evolve-suggest          # 必填，目录名一致
description: ...                 # 必填，做什么 + 何时用
version: all                     # 版本线：1.x / 2.x / all，对齐 plan_capability_binding.skill_version
form: ai-skill                   # domain-service | ai-skill
risk: READ                       # READ | WRITE_LOW_RISK | WRITE_HIGH_RISK
plans: all                       # 适用套餐档位
templates:
  RULE_CTR_LOW:                  # 模板键（代码中引用）
    variables:                   # 变量契约：代码与内容之间的接口声明
      ctrLow: { type: number, required: true, desc: CTR 阈值 }
    default: "CTR 低于阈值 {{ctrLow}}，建议更换素材或调整人群定向"
```

**契约边界（铁律）**：

- 措辞调整（不动变量集合）→ 运营端评审流闭环，不需要工程
- 新增 / 删除变量、新增 / 删除模板键 → 契约变更，必须改 `contract.yaml` + 代码传参，走 PR 评审
- 保存草稿时校验：内容引用的变量必须全部在契约中声明，未声明变量**保存时当场拒绝**
  （把"运行时渲染失败"前置为"保存时拦截"）
- 渲染时校验：`required` 变量调用方必须提供，缺失即 fail-fast（`PromptTemplate` 抛异常）

## 4. 数据库表

### skill_template —— 运行时唯一数据源

| 列 | 说明 |
|---|---|
| id | `skillName/templateKey` |
| skill_name / template_key | 模板定位 |
| content | 当前生效内容（纯模板文本，不含契约元数据） |
| updated_by | `seed:contract` = 种子；其他 = 运营操作人 |
| updated_at | 更新时间 |

### skill_template_version —— 版本快照（评审与回滚载体）

| 列 | 说明 |
|---|---|
| id | 自增 |
| skill_name / template_key | 模板定位 |
| version_no | 按模板维度单调递增 |
| content | 该版本内容快照 |
| status | `DRAFT` / `PUBLISHED` |
| updated_by | 操作人 |
| review_ref | 评审单号（发布 / 回滚必填；后续对接飞书审批自动化） |
| created_at | 创建时间 |

回滚 = 以历史 PUBLISHED 内容为内容产生**新的** PUBLISHED 版本——历史不被改写，审计链完整。

## 5. 加载机制

`SkillLibrary`（`skill.md` 包）：

1. **契约常驻**：构造时扫描 `classpath*:skills/*/contract.yaml`，元数据 + 变量声明常驻内存（`catalog()` / `contract(name)`）
2. **内容按需**：`content(name, templateKey)` 优先读 `skill_template` 表，无记录回退契约默认值
3. **渲染即用**：`render(name, templateKey, vars)` = 生效内容 + 变量注入，缺变量 fail-fast

三个 AI Skill 的统一约定：**逻辑留代码（阈值判断、规则命中、门禁），文案进模板，库缺失降级内置文案不失效**。

## 6. 版本与评审流

```
运营编辑 → 保存草稿（契约校验）→ 发布（必填评审单号）→ 生效 + PUBLISHED 快照
                │                                              │
             丢弃草稿                                     回滚（必填评审单号，
                                                          产生新版本）
```

后台接口：`/api/admin/skills`（`SkillAdminController`），管理页：`/skill-admin/index.html`。

评审单号当前为轻量形式（人工填写，随快照留存）；工程已有飞书审批通道 mock，
后续可将发布动作接入审批单自动化（审批通过回调触发 publish）。

## 7. 种子机制

`SkillTemplateBootstrap`（启动时执行一次）：

- 将每份 contract.yaml 的全部模板默认值写入 `skill_template`（updated_by = `seed:contract`）
- 同步写入版本快照 v1（PUBLISHED / review_ref = `seed`），历史表从第一天起完整
- 幂等：某 skill 已有记录（含运营发布）则整个跳过，绝不冲掉运营修改
- 新模板准入仍走代码评审（contract.yaml 的 PR），启动自动入库

## 8. 设计决策记录

| 决策 | 理由 |
|---|---|
| 运行时唯一数据源 = DB | 内容迭代不需要发版；契约默认值保留为无记录兜底 |
| contract.yaml 保留在 jar | 契约（变量接口）是代码的一部分，必须随版本评审；同时是种子源 |
| 内容存纯模板文本（不含 frontmatter） | 元数据属于契约不属于内容，避免两份事实来源 |
| 回滚产生新版本而非改写历史 | 审计链完整，"谁在什么时间把什么改成了什么"永远可查 |
| 评审用轻量单号而非完整审批流 | 数据层先闭环；审批自动化复用飞书通道，后置迭代 |

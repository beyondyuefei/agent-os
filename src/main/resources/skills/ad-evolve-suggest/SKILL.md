---
name: ad-evolve-suggest
description: 效果进化建议的诊断话术模板库。诊断判断逻辑（阈值计算、规则命中）保留在 Java 代码中；给人看的事实描述、建议话术、实验设计文案全部外部化于此，运营可直接调整措辞而不改代码、不发版。
version: all
form: ai-skill
risk: READ
plans: all
---

# 效果进化建议 · 诊断话术模板

本文档登记各诊断规则的适用条件；具体话术见 references/ 下同名单文件。

## 规则清单

| 规则 | 文件 | 适用 | 触发条件（逻辑在代码中） |
|---|---|---|---|
| RULE_CTR_LOW | RULE_CTR_LOW.md | 全档位 | CTR 低于 policy 阈值 |
| RULE_CLICK_NO_CONVERT | RULE_CLICK_NO_CONVERT.md | 全档位 | 有点击但无归因成交 |
| RULE_CROSS_PRODUCT | RULE_CROSS_PRODUCT.md | KA | 存在跨品成交 |
| FACT_CTR | FACT_CTR.md | 全档位 | 事实段 CTR 描述 |
| FACT_CROSS_PRODUCT | FACT_CROSS_PRODUCT.md | KA | 事实段跨品描述 |
| EXPERIMENT_DESIGN | EXPERIMENT_DESIGN.md | KA | 实验设计输出 |

## 边界约定

- 判断条件、阈值计算、动作路由留在 Java 代码，走代码评审
- 措辞、句式、格式规则在本目录调整，运营可直接修改
- 模板变量未提供时渲染失败（fail-fast），不得带 {{空洞}} 进输出

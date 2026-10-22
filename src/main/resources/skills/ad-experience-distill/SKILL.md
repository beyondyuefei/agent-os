---
name: ad-experience-distill
description: 经验沉淀文案模板库。经验候选的入库逻辑（候选级 / 审核入库级、审核生效门禁）保留在 Java 代码中；候选规则与适用条件的措辞外部化于此，运营可直接调整。
version: all
form: ai-skill
risk: WRITE_LOW_RISK
plans: all
---

# 经验沉淀 · 文案模板

| 用途 | 文件 | 说明 |
|---|---|---|
| 候选规则措辞 | RULE_CANDIDATE.md | 从复盘结论生成候选规则文本，变量 {{review}} |
| 适用条件占位 | CONDITION_PLACEHOLDER.md | 审核前适用条件的占位文案 |

## 铁律（逻辑在代码中，不可由模板改变）

- 未审核规则不可被任何 Skill 读为生效配置（approved=false 入库，approve() 后生效）
- PRO 档只生成候选不入库；KA 档候选自动入库但仍需审核

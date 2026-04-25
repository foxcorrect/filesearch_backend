---
name: code-review-expert
description: "Expert code review of current git changes with a senior engineer lens. Detects SOLID violations, security risks, and proposes actionable improvements."
---

# Code Review Expert

## 概述

对当前 git 变更进行结构化审查，重点关注 SOLID 原则、架构、可删除代码及安全风险。默认只输出审查意见，除非用户明确要求实施修改。

## 严重程度级别

| Level | Name | Description | Action |
|-------|------|-------------|--------|
| **P0** | Critical | 安全漏洞、数据丢失风险、正确性缺陷 | Must block merge |
| **P1** | High | 逻辑错误、重大 SOLID 违规、性能回退 | Should fix before merge |
| **P2** | Medium | Code smell、可维护性问题、轻微 SOLID 违规 | Fix in this PR or create follow-up |
| **P3** | Low |样式、命名、微小建议 | Optional improvement |

## 工作流程

### 1) 前置上下文检查

- Use `git status -sb`, `git diff --stat`, and `git diff` to scope changes.
- If needed, use `rg` or `grep` to find related modules, usages, and contracts.
- Identify entry points, ownership boundaries, and critical paths (auth, payments, data writes, network).

**边界情况：**
- **无变更**: If `git diff` is empty, inform user and ask if they want to review staged changes or a specific commit range.
- **变更较大（超过500行）**: Summarize by file first, then review in batches by module/feature area.
- **混合关注点**: Group findings by logical feature, not just file order.

### 2) SOLID + architecture smells

- Load `references/solid-checklist.md` for specific prompts.
- 查找：
  - **SRP**: 承担了不相关职责的过载模块。
  - **OCP**: 频繁修改代码以增加行为，而非使用扩展点。
  - **LSP**: 破坏预期或需要类型检查的子类。
  - **ISP**: 包含未使用方法的宽接口。
  - **DIP**: 高层逻辑与低层实现紧密耦合。
- 当提出重构建议时，解释*为什么*它能改善内聚/耦合，并概述一个最小、安全的拆分方案。
- 如果重构不简单，提出增量计划，而非大范围重写。

### 3) 可删除代码 + 迭代计划

- Load `references/removal-plan.md` for template.
- 识别未使用、冗余或被功能开关禁用的代码。
- Distinguish **safe delete now** vs **defer with plan**.
- 提供包含具体步骤和检查点（测试/指标）的后续计划。

### 4) 安全与可靠性扫描

- Load `references/security-checklist.md` for coverage.
- 检查：
  - XSS, injection (SQL/NoSQL/command), SSRF, path traversal
  - AuthZ/AuthN gaps, missing tenancy checks
  - Secret leakage or API keys in logs/env/files
  - Rate limits, unbounded loops, CPU/memory hotspots
  - Unsafe deserialization, weak crypto, insecure defaults
  - **Race conditions**: concurrent access, check-then-act, TOCTOU, missing locks
- Call out both **exploitability** and **impact**.

### 5) 代码质量扫描

- Load `references/code-quality-checklist.md` for coverage.
- Check for:
  - **Error handling**: 吞没异常、过宽的 catch 块、错误处理缺失、异步错误
  - **Performance**: N+1 查询、热路径中的 CPU 密集操作、缓存缺失、内存无上限增长
  - **Boundary conditions**: null/undefined handling, empty collections, numeric boundaries, off-by-one
- Flag issues that may cause silent failures or production incidents.

### 6) 输出格式

按以下结构组织审查意见：

```markdown
## 代码审查总结

**Files reviewed**: X files, Y lines changed
**总体评估**: [APPROVE / REQUEST_CHANGES / COMMENT]

---

## 发现的问题

### P0 - Critical
(none or list)

### P1 - High
1. **[file:line]** 简要标题
  - 问题描述
  - 建议修复方案

### P2 - Medium
2. (continue numbering across sections)
  - ...

### P3 - Low
...

---

## 删除/迭代计划
(如适用)

## 其他建议
(可选改进，不阻塞合并)
```

**行内注释**: 对特定文件的问题使用此格式：
```
::code-comment{file="path/to/file.ts" line="42" severity="P1"}
问题描述和建议修复方案.
::
```

**Clean review**: 如果没有发现问题，明确说明：
- 检查了哪些内容
- 未覆盖的领域（例如：“未验证数据库迁移”）
- 残留风险或推荐的后续测试

### 7) 后续步骤确认

在呈现审查结果后，询问用户如何继续：

```markdown
---

## 后续步骤

我发现 X 个问题（P0: _， P1: _， P2: _， P3: _）。

**您希望如何继续？**

1. **Fix all** - 我将实施所有建议的修复
2. **Fix P0/P1 only** - 处理严重和高优先级问题
3. **Fix specific items** - 告诉我需要修复哪些问题
4. **No changes** - 审查完成，无需实施

请选择一个选项或提供具体指令。
```

**Important**: 在用户明确确认之前，不要实施任何修改。这是一个“先审查后实施”的工作流程。

## Resources

### references/

| File | 用途 |
|------|---------|
| `solid-checklist.md` | SOLID 异味提示和重构启发规则 |
| `security-checklist.md` | Web/app 安全及运行时风险检查清单 |
| `code-quality-checklist.md` | 错误处理、性能、边界条件 |
| `removal-plan.md` | 待删除代码及后续计划模板 |
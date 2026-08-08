# 多场景召回测试集 v1

这套数据用于当前开发环境的“召回批量测试”页面，重点测试 RAG、长期记忆、用户偏好、混合召回，以及可选 ContextPlannerNode 的候选筛选质量。

## 数据规模

- RAG：60 条，ID `10001`–`10060`
- 长期记忆：60 条，ID `20001`–`20060`
- 用户偏好：60 条，ID `30001`–`30060`
- 测试问题：150 条，ID `40001`–`40150`
- 场景：跨国旅行、软件研发、内容创作、运动作息、居住消费、语言与技能学习

所有ID都以JSON字符串保存。问题包含单来源改写、跨来源多标签、同主题干扰和记忆/偏好混淆，不是简单的一问一原句。

## 导入顺序

1. 新建测试数据集。
2. 在“记忆数据 / RAG数据”导入 `rag.jsonl`。
3. 在“记忆数据 / 长期记忆”导入 `long-term-memory.jsonl`。
4. 在“记忆数据 / 用户偏好”导入 `user-preference.jsonl`。
5. 确认三张表全部进入 `READY`，并能展开查看真实向量行。
6. 在“测试问题”导入 `cases.jsonl`。
7. 在“参数与运行”先关闭 Context Planner 跑基线，再开启 Context Planner 跑筛选测试。

`corpus.jsonl` 是三类记忆数据的合并版本，主要用于脚本或API批量导入；界面操作建议使用三个分表文件。

## 推荐基线参数

- 范围：`MIXED`
- 模式：`HYBRID`
- Top K：`10`
- 最低相似度：`0.20`
- 问题上限：`150`
- 单问题超时：`30000ms`

## 重新生成

```powershell
node docs/dev-ops/recall-evaluation/datasets/multi-scenario-recall-v1/generate_dataset.mjs
```

生成脚本会覆盖同目录下的JSONL和manifest文件。

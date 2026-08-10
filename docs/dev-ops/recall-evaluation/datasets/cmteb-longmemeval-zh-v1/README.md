# 中文复杂召回测试集 v2（Chunk级）

这是一套面向当前项目召回评测控制台的**全合成**数据集。它没有复制 C-MTEB 或 LongMemEval 的原始样本，而是借鉴两类基准的任务设计：

- C-MTEB 风格：中文语义改写、不同词面表达、同领域难负例和条件消歧。
- LongMemEval 风格：跨会话事实、多次更新、时间推理、条件偏好和多来源综合。

## 文件与规模

| 文件 | 内容 | 数量 | ID范围 |
| --- | --- | ---: | --- |
| `rag.jsonl` | 独立RAG Chunk | 100 | `11001`–`11100` |
| `long-term-memory.jsonl` | 带旧值、更新值和时间的长期记忆 | 100 | `21001`–`21100` |
| `user-preference.jsonl` | 带触发条件和例外的用户偏好 | 100 | `31001`–`31100` |
| `cases.jsonl` | 标注预期命中ID的测试问题 | 200 | `41001`–`41200` |
| `corpus.jsonl` | 三类语料的合并版本 | 300 | — |

数据覆盖复杂旅行、Agent研发、学习研究、居住消费、内容发布、健康作息、项目协作、个人预算、摄影资产和客户支持十个场景。

## RAG评测单元

这套数据只测试召回与 Context Planner 筛选效果，不测试大文件解析和切分质量。因此每条RAG记录就是一个独立、可标注的召回单元：

- 类型固定为 `RAG_CHUNK`；
- 正文保持在260–512字，避免退化成关键词短句；
- 只包含一个自然段；
- 按项目算法必须恰好生成一个真实向量Chunk；
- 测试问题使用 `EXACT_SOURCE` 直接匹配该Chunk的数字ID。

这样 Top K、Precision、Recall 和 Planner 保留/剔除结果都直接对应Chunk，不再受父文件、多Chunk聚合和大文件上传耗时影响。

## 导入顺序

1. 在召回评测页面创建新的数据集。
2. 在“RAG Chunk”表导入 `rag.jsonl`。每条内容会通过RAG领域服务生成且仅生成一个Chunk，并直接绑定数字ID。
3. 在“长期记忆”表导入 `long-term-memory.jsonl`。
4. 在“用户偏好”表导入 `user-preference.jsonl`。
5. 等三张表均为 `READY` 后，在“测试问题”导入 `cases.jsonl`。
6. 先关闭 ContextPlannerNode 跑一轮基线，再用相同参数开启 Planner 跑第二轮。

建议首次先用20到40个问题验证环境；完整200题用于正式基线。导入前请确认后端、MySQL、PGVector和Embedding服务均可用。

## 问题构成

- RAG单源：70题，含语义改写、条件检索和同主题难负例。
- 记忆/偏好单源：80题，含最新值、时间顺序和隐式偏好。
- 混合召回：50题，每题同时标注RAG、长期记忆和两个条件偏好。
- 全部300条语料至少被一个问题引用，不存在只导入却不参与测试的记录。

## 重新生成与校验

```powershell
node docs/dev-ops/recall-evaluation/datasets/cmteb-longmemeval-zh-v1/generate_dataset.mjs
node docs/dev-ops/recall-evaluation/datasets/cmteb-longmemeval-zh-v1/validate_dataset.mjs
```

生成器固定输入、结果可复现；校验器会检查数量、数字ID、UTF-8乱码特征、引用完整性、全量覆盖、Chunk长度以及每条RAG恰好生成一个Chunk。

# RAG 与记忆召回评测工作台设计

## 1. 目标

建设一个仅面向当前 dev 环境的独立开发者模块，用于管理评测语料、批量执行 RAG/长期记忆召回、可选执行 Context Planner 候选判断，并以可量化指标比较召回参数、切分策略和查询处理策略。

评测必须复用真实生产链路，但在 Context Planner 后立即停止，不进入 MainNode、Action Runtime、Tool、子 Agent、Final Guard 或最终回答。

## 2. 核心原则

1. **生产路径复用**：RAG 使用现有文档切分、Payload、MySQL 资产和 pgvector 索引；长期记忆使用现有 Payload、Memory Repository 和 Memory Vector Indexing。
2. **数据集隔离**：每个评测数据集拥有独立 `datasetId`、合成 user/session scope 和向量 metadata 过滤，不混入普通 Agent 数据。
3. **检索与 Planner 分层**：纯召回模式不调用聊天模型；Planner 模式每个案例最多调用一次 Context Planner。
4. **结果可追溯**：保存运行参数快照、每个案例的原始排名、分数、候选解析、Planner 选择、错误阶段和耗时。
5. **批量优先**：案例级失败不终止整批运行，支持取消、重跑和 A/B 对比。
6. **结构化展示**：默认使用字段化卡片、表格和图表；原始 JSON 仅作为末级调试入口。

## 3. 非目标

- 不执行 MainNode 或生成最终答案。
- 不评测工具调用、子 Agent、最终交付质量。
- 不建设生产环境多租户权限、定时任务或大规模分布式调度。
- 不在第一版内集成“AI 自动生成数据集”；但导入格式必须适合外部 AI 生成。
- 不直接暴露任意 pgvector SQL 控制台。

## 4. 总体架构

```text
Recall Evaluation UI
  -> RecallEvaluationController
  -> RecallEvaluationFacade
       -> Dataset/Corpus/Case Management
       -> RAG Asset Ingestion + Vector Indexing
       -> Long-term Memory Ingestion + Vector Indexing
       -> Parameterized RAG/Memory Recall
       -> Optional ContextPlannerNodeService
       -> Metrics Calculator
       -> Evaluation Repository
```

DDD 边界：

- `api`：请求/响应 DTO。
- `trigger`：HTTP、Multipart、DTO 映射和统一错误返回。
- `domain`：数据集生命周期、语料入库编排、召回执行、Planner 截断、指标计算、运行状态机。
- `infrastructure`：评测表 DAO/Mapper/Repository，pgvector metadata 精确过滤。
- `app`：Facade、异步执行器和依赖装配。
- `docs/dev-ops/nginx/html`：独立开发者页面。

## 5. 数据隔离

每个数据集生成：

```text
evalUserId    = eval-user:{datasetId}
evalSessionId = eval-session:{datasetId}
metadata      = { "evalDatasetId": datasetId, "evalExternalId": externalId }
```

隔离同时使用两道边界：

1. MySQL 业务记录使用合成 user/session scope。
2. pgvector 查询使用 `metadata @> {"evalDatasetId":"..."}` 精确过滤。

`VectorRecallFilterVO.metadataFilters` 已存在但当前未落入 SQL。本功能补齐该能力，并保持普通生产查询不传 metadata 时行为不变。

删除数据集时，评测仓储根据已登记的 source refs 依次禁用向量记录、软删除/禁用业务源记录，再删除评测元数据。任何单项清理失败都记录在数据集错误状态中，不静默遗留。

## 6. 持久化模型

### 6.1 `agent_recall_eval_dataset`

- `dataset_id`
- `name`
- `description`
- `status`: `ACTIVE | INGESTING | ERROR | DELETING | DELETED`
- `eval_user_id`
- `eval_session_id`
- corpus/case/indexed counts
- timestamps

### 6.2 `agent_recall_eval_corpus_item`

- `corpus_item_id`
- `dataset_id`
- `external_id`（数据集内唯一，供标签引用）
- `item_type`: `RAG_DOCUMENT | LONG_TERM_MEMORY | USER_PREFERENCE`
- title/summary/content_ref/tags_json
- source_type/source_id/parent_source_id
- `status`: `PENDING | INDEXING | READY | FAILED | DISABLED`
- failure stage/code/message
- timestamps

一个上传文档可以登记一条父 corpus item，并把生成的 Chunk source IDs 保存在 `source_refs_json` 中。标签既可指向父文档，也可指向具体 Chunk。

### 6.3 `agent_recall_eval_case`

- `case_id`
- `dataset_id`
- `external_id`
- `query_text`
- `source_scope`: `RAG | MEMORY | MIXED`
- `expected_json`: `[{externalId, sourceId?, grade}]`
- tags/status/timestamps

`grade` 取 1、2、3，分别表示弱相关、相关、核心相关。

### 6.4 `agent_recall_eval_run`

- `evaluation_run_id`
- `dataset_id`
- name/status
- `config_json`（完整参数快照）
- `metrics_json`
- total/completed/failed case counts
- started/completed timestamps
- failure code/message

### 6.5 `agent_recall_eval_case_result`

- `case_result_id`
- run/case IDs
- status
- retrieval/planner latency
- hit/recall/precision/MRR/nDCG fields
- planner status/reason/selected IDs JSON
- failure stage/code/message

### 6.6 `agent_recall_eval_hit`

- run/case IDs
- rank
- retrieval channel: `VECTOR | LEXICAL`
- collection/source type/source ID/parent source ID
- score
- expected grade
- selected_by_planner
- candidate snapshot JSON

## 7. 导入契约

### 7.1 Corpus JSONL

```json
{"externalId":"memory-travel-pace","type":"LONG_TERM_MEMORY","title":"旅行节奏偏好","summary":"用户偏好慢节奏旅行","content":"用户希望每天最多安排两个主要景点，并保留午休时间。","tags":["travel","preference"]}
{"externalId":"rag-hangzhou-guide","type":"RAG_DOCUMENT","title":"杭州三日游指南","content":"第一天……","tags":["travel","hangzhou"]}
```

### 7.2 Case JSONL

```json
{"externalId":"case-travel-001","query":"我之前更喜欢什么样的旅行节奏？","sourceScope":"MEMORY","expected":[{"externalId":"memory-travel-pace","grade":3}]}
```

前端负责解析 JSONL/CSV 并调用结构化批量 API；普通 `.txt/.md` 文件通过 multipart 进入真实 RAG 文件入库服务。

批量 API 逐项返回 `accepted/failed`，不因一条坏数据回滚整批已成功项。

## 8. 参数化召回

新增通用 `RecallExecutionOptionsVO`：

- `topK`
- `minScore`
- `lexicalEnabled`
- `collectionTypes`
- `metadataFilters`
- `retrievalTimeoutMs`

现有 RAG 和 Memory Preselector 增加 detailed/parameterized 入口；原有 `recall(command)` 保持默认行为并委托新入口，避免生产路径回归。

Detailed Result 同时返回：

- 原始 vector/lexical hits
- 去重后的候选
- 被过滤原因和耗时诊断

RAG 默认可启用 Vector + Lexical；Memory 默认只启用 Vector。评测配置可以显式切换。

## 9. Context Planner 评测

当 `plannerEnabled=false` 时，运行到候选解析后结束，不调用聊天模型。

当 `plannerEnabled=true` 时：

1. 把本案例解析后的 RAG/Memory candidates 组装成 `ContextCandidateBundleVO`。
2. 调用 `ContextPlannerNodeService.plan(...)`，但不创建正常 Agent Run，也不继续 Runtime。
3. 保存 Planner 原始结构化输出、选中 candidate IDs、reason、clarification 和失败信息。
4. 计算正确候选误删率、无关候选保留率、Planner Precision/Recall。

Planner 每个案例最多一次正常调用及现有 Contract Repair 上限，不额外做 MainNode 思考。

## 10. 指标

### 10.1 召回指标

- Hit Rate@K
- Precision@K
- Recall@K
- MRR
- nDCG@K
- MAP@K
- no-hit rate
- case failure rate
- retrieval latency average/P50/P95

父文档标签可以通过 `PARENT_DOCUMENT` 匹配策略命中其任一 Chunk；具体 Chunk 标签使用 `EXACT_SOURCE`。

### 10.2 Planner 指标

- Planner Precision/Recall
- relevant candidate drop rate
- irrelevant candidate keep rate
- clarification rate
- contract/repair failure rate
- planner latency average/P50/P95
- planner invocation count

### 10.3 A/B 对比

选择同一数据集上的两个 Run，展示：

- 参数差异
- 每个聚合指标的绝对值和 delta
- 提升/下降案例数
- 仅 A 命中、仅 B 命中的案例
- 每案例排名变化

## 11. 异步执行与错误处理

- `POST /runs` 快速创建 Run 并提交到有界评测执行器。
- UI 轮询 Run 状态和进度。
- 支持 `POST /runs/{runId}/cancel`。
- 案例级异常记录为 `FAILED`，其余案例继续。
- 运行级异常只用于数据集不可用、执行器拒绝或持久化失败。
- 失败记录包含 stage、code、message；UI 可从汇总跳转到案例详情。
- 取消后不启动新案例，已经开始的单案例安全收尾。

## 12. API

基础路径：`/api/v1/dev/recall-evaluations`

- `GET/POST /datasets`
- `GET/PATCH/DELETE /datasets/{datasetId}`
- `GET /datasets/{datasetId}/corpus`
- `POST /datasets/{datasetId}/corpus/batch`
- `POST /datasets/{datasetId}/corpus/files`
- `POST /datasets/{datasetId}/corpus/{itemId}/reindex`
- `PATCH/DELETE /datasets/{datasetId}/corpus/{itemId}`
- `GET /datasets/{datasetId}/cases`
- `POST /datasets/{datasetId}/cases/batch`
- `PATCH/DELETE /datasets/{datasetId}/cases/{caseId}`
- `GET/POST /runs`
- `GET /runs/{runId}`
- `GET /runs/{runId}/results`
- `POST /runs/{runId}/cancel`
- `GET /compare?leftRunId=...&rightRunId=...`

## 13. UI

独立页面 `recall_evaluation.html`，采用与现有开发者看板一致的暗色半透明风格。

- 左侧窄栏：数据集列表、数量、状态和新建入口。
- 顶部：当前数据集统计、索引健康、最近 Run。
- 主区四个工作区：`语料库`、`测试问题`、`实验配置`、`结果分析`。
- 语料库：父卡片显示文档/记忆，展开查看 Chunk、Payload、索引状态和错误。
- 测试问题：问题、期望标签、相关性等级和批量导入。
- 实验配置：来源、topK、minScore、检索模式、集合、Planner 开关和模型参数。
- 结果分析：KPI 卡、阈值/排名趋势、小型分布图、逐案例表和 A/B 对比。
- 案例详情：期望项与实际排名正对展示，Planner 保留/淘汰状态直接叠加在同一命中卡片上。
- Raw JSON 放在详情末级按钮内，不作为默认内容。

## 14. 安全与容量

- 仅 dev profile 装配 Controller/Executor。
- 文本和上传大小设置显式上限；批量数量有上限。
- 不接受任意表名、集合名或 SQL。
- pgvector collection 仍由 enum 白名单控制。
- UI 对上传内容按不可信数据展示，使用 `textContent`/转义，不拼接可执行 HTML。

## 15. 最小验证

1. 指标计算器：验证分级标签下的 Recall/MRR/nDCG。
2. 批量评测主流程：验证数据集过滤、案例级失败隔离和 Planner 可关闭。
3. 前端纯函数：验证 JSONL/CSV 解析、结果聚合和 A/B delta。
4. dev 环境真实验收：一个 RAG 文档、两条 Memory、三条 Case，执行 Retrieval-only 与 Planner-enabled 各一次。

不运行整个仓库测试套件。

## 16. 验收标准

- 可以在 UI 中创建/切换/删除数据集。
- 可以批量导入、查看、禁用和重建 RAG/Memory 测试数据。
- 可以批量导入带人工标签的问题。
- 可以按不同 topK/minScore/模式执行真实召回。
- 可以选择是否调用 Context Planner，且不会进入 MainNode。
- 可以看到每案例完整命中列表、分数、排名、期望匹配和 Planner 决策。
- 可以看到主要召回与 Planner 指标，并对比两次 Run。
- 数据集之间以及与普通 Agent 数据之间无串召回。
- 最小目标测试与一次真实 dev 流程通过。

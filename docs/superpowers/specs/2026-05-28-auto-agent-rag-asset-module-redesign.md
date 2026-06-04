# AutoAgent RAG Asset Module Redesign

Date: 2026-05-28

## 1. Goal

Rebuild the current RAG module from a legacy Spring AI `PgVectorStore` upload/retrieve path into an AutoAgent-native private knowledge asset system.

The redesigned module must support two asset sources:

- User uploaded files.
- GitHub repository links.

The redesigned module must integrate with the current AutoAgent memory/context pipeline:

```text
User input
 -> ContextPreparationService
 -> MySQL memory candidates + vector memory candidates + RAG asset candidates
 -> ContextPlanner
 -> ContextMaterializer
 -> MainAgentStateView
 -> MainAgentNode
```

RAG should be used as part of context preparation, not primarily as a `MainAgentAction`. The existing action-based `RETRIEVE_RAG` path can remain temporarily for compatibility, but it is not the primary design target.

## 2. Current Code Facts

The current main context pipeline is already candidate based:

- `DefaultRuntimeComponentPorts.prepareContext(...)` calls `ContextPreparationService.prepare(...)`.
- `ContextPreparationService` runs MySQL candidate preparation and vector memory recall in parallel.
- `ContextPlannerNodeService` receives a planning view of `ContextCandidateBundleVO`.
- `ContextPlannerStatusHandler` validates planner output and calls `ContextMaterializer`.
- `ContextMaterializer` loads selected content and builds `MainAgentStateViewVO`.
- `MainAgentNode` only sees the materialized state view.

Current RAG implementation is legacy relative to the memory redesign:

- `RagRepository.ingestFiles(...)` parses uploaded files, token-splits, and writes chunks to Spring AI `PgVectorStore`.
- `RagRepository.ingestGitRepository(...)` clones a Git repo, scans files, token-splits, and writes chunks to `PgVectorStore`.
- `SpringAiRagRetrieverAdapter` retrieves from the configured `PgVectorStore`.
- `RagRuntime` persists `agent_rag_query`, `agent_rag_hit`, and evidence for the action-based `RETRIEVE_RAG` flow.

Current dedicated vector infrastructure already exists:

- `VectorCollectionTypeEnumVO.RAG_DOCUMENT -> vec_rag_document`
- `VectorCollectionTypeEnumVO.RAG_CHUNK -> vec_rag_chunk`
- `VectorSourceTypeEnumVO.RAG_DOCUMENT`
- `VectorSourceTypeEnumVO.RAG_CHUNK`
- `PgVectorMemoryRepository` can upsert and search these collections through `IVectorMemoryRepository`.

Current gaps:

- No MySQL truth tables for RAG assets, documents, chunks, code files, or code symbols.
- No `RagContextRecallPreselector`.
- No `ragCandidates` field in `ContextCandidateBundleVO`.
- No `ragPack` field in `MainAgentStateViewVO`.
- No RAG validation/materialization path in `ContextSelectionValidator` or `ContextMaterializer`.
- `ContextPlannerPromptBuilder` mentions chunk-capable sources, but does not define per-type RAG selection rules.

## 3. Design Principles

### 3.1 RAG Is Private Asset Context, Not Long-Term Memory

Long-term memory represents stable user/session facts and preferences. RAG represents uploaded private documents and code assets. They should participate in the same context preparation phase, but remain separate in data structures:

- Long-term memory -> `memoryCandidates` -> `memoryPack`
- RAG assets -> `ragCandidates` -> `ragPack`

This avoids mixing user facts with external file/code knowledge.

### 3.2 Recall Produces Candidates, Planner Decides Injection

RAG recall must not directly inject content into MainAgent. It only produces structured candidates.

The `ContextPlanner` decides:

- Whether each candidate is useful.
- Which candidate should be selected.
- What context level should be used.

The `Runtime` and `ContextMaterializer` only execute the planner's selection.

### 3.3 Code Candidates Are Summary Cards, Not Raw Code

For code RAG, `ContextPlanner` should not receive full source code by default. It receives structured code candidate cards:

- Repository name.
- File path.
- Language.
- Symbol name and type.
- Line range.
- LLM-generated code function summary.
- Match signals.
- Short code preview.

Full code is only loaded by `ContextMaterializer` when selected by `ContextPlanner`.

### 3.4 No Strong Bypass Channel

All RAG candidates, including exact code/path/symbol hits, go through `ContextPlanner`.

Exact match information is provided as candidate metadata and match reasons. It influences planner judgment, but does not bypass it.

### 3.5 Compatible With Future Notebook And Subagents

The module must be usable by future agent runtimes:

- MainAgent can use global memory + RAG context.
- Temporary subagents can opt into RAG capability.
- CodeAgent can use code workspace context plus code RAG.
- Future notebook state can store selected RAG references without knowing ingestion internals.

Therefore RAG should expose provider-style services and structured packs, not hidden prompt strings.

## 4. Target Architecture

```text
Upload API
 -> RagAssetIngestionService
 -> FileParser / GitRepositoryLoader
 -> Chunker
 -> LLM summary/enrichment nodes
 -> IRagAssetRepository
 -> RagVectorIndexingService
 -> IVectorMemoryRepository

Runtime context preparation
 -> ContextPreparationService
 -> ContextCandidatePreselector
 -> VectorContextRecallPreselector
 -> RagContextRecallPreselector
 -> ContextCandidateBundleVO.ragCandidates
 -> ContextPlanner
 -> ContextMaterializer
 -> MainAgentStateViewVO.ragPack
```

## 5. Module Boundaries

### 5.1 Domain Module

Package locations:

- Entities:
  - `domain/agent/model/entity/rag`
- Value objects:
  - `domain/agent/model/valobj/rag`
  - `domain/agent/model/valobj/context`
- Repository ports:
  - `domain/agent/adapter/repository`
- RAG services:
  - `domain/agent/service/rag`
- RAG context recall:
  - `domain/agent/service/rag/context`
- LLM node services:
  - `domain/agent/service/node/ragdocument`
  - `domain/agent/service/node/ragcode`
- Prompt builders:
  - `domain/agent/service/prompt`

Domain owns:

- Ingestion orchestration.
- Chunking policies.
- LLM node input/output contracts.
- Candidate creation and ranking policies.
- Vector indexing intent.
- Context preparation/materialization contracts.

Domain must not own:

- MyBatis details.
- JGit implementation details.
- PgVector SQL.
- HTTP DTO translation.

### 5.2 Infrastructure Module

Infrastructure owns:

- MyBatis DAO/PO/mapper for RAG asset tables.
- Repository adapters for `IRagAssetRepository`.
- Git clone implementation.
- File text extraction adapters.
- Keyword/path/symbol search SQL.
- Use of `PgVectorMemoryRepository` through the existing `IVectorMemoryRepository` port.

### 5.3 Trigger Module

Trigger owns:

- Upload file endpoint DTO conversion.
- GitHub ingest endpoint DTO conversion.
- Asset list/status/delete/reindex endpoints for frontend.

Controller must not perform parsing, chunking, LLM calls, or vector writes.

### 5.4 App Module

App owns:

- Spring bean assembly.
- Runtime configuration.
- LLM node binding registration SQL.
- Integration-style tests.

## 6. Data Model

### 6.1 `agent_rag_document`

Purpose:

- Truth table for an uploaded file or Git repository asset.
- Frontend listing.
- Delete/reindex status.
- Vector index repair source.

Suggested columns:

```sql
document_id          varchar(64) primary logical id
user_id              varchar(64)
session_id           varchar(64) nullable
source_type          varchar(32)  -- FILE, GITHUB_REPO
source_name          varchar(512) -- file name or repo name
source_uri           varchar(1024)
status               varchar(32)  -- INGESTING, READY, FAILED, DELETED
summary_ref          varchar(64)
summary              varchar(1024)
chunk_count          int
file_count           int
code_line_count      int
language_stats_json  json
metadata_json        json
failure_code         varchar(128)
failure_message      text
created_at           datetime
updated_at           datetime
ready_at             datetime
deleted_at           datetime
```

For uploaded files:

- `source_type = FILE`
- `source_name = original file name`
- `source_uri` can be null or an internal upload reference.

For GitHub repositories:

- `source_type = GITHUB_REPO`
- `source_name = repo name`
- `source_uri = repo url`
- `metadata_json` stores branch, clone commit if available, root package hints, ignored counts.

### 6.2 `agent_rag_chunk`

Purpose:

- Truth table for searchable and materializable RAG chunks.
- One row per text paragraph chunk, file summary chunk, code file chunk, code symbol chunk, or repo summary chunk.

Suggested columns:

```sql
chunk_id             varchar(64)
document_id          varchar(64)
user_id              varchar(64)
session_id           varchar(64) nullable
source_type          varchar(32) -- FILE, GITHUB_REPO
chunk_type           varchar(64) -- TEXT_PARAGRAPH, DOCUMENT_SUMMARY, REPO_SUMMARY, CODE_FILE, CODE_SYMBOL
chunk_no             int
title                varchar(512)
summary              varchar(1024)
content_ref          varchar(64)
retrieval_text_ref   varchar(64)
metadata_json        json
content_sha256       varchar(128)
status               varchar(32) -- ACTIVE, DISABLED, DELETED
created_at           datetime
updated_at           datetime
```

Important rule:

- The vector `sourceId` for `vec_rag_chunk` must be `chunk_id`.
- Do not use `document_id` as vector `sourceId` for chunk rows, because the vector table has unique `(source_type, source_id)`.

### 6.3 `agent_rag_code_file`

Purpose:

- Repository file-level metadata.
- Supports frontend repository browsing and keyword/path search.

Suggested columns:

```sql
file_id              varchar(64)
document_id          varchar(64)
repo_name            varchar(512)
file_path            varchar(1024)
language             varchar(64)
loc                  int
summary              varchar(1024)
content_ref          varchar(64)
content_sha256       varchar(128)
status               varchar(32)
created_at           datetime
updated_at           datetime
```

### 6.4 `agent_rag_code_symbol`

Purpose:

- Function/class/method-level index.
- Supports symbol-level recall and line-range materialization.

Suggested columns:

```sql
symbol_id            varchar(64)
document_id          varchar(64)
file_id              varchar(64)
chunk_id             varchar(64)
symbol_name          varchar(512)
symbol_type          varchar(64) -- CLASS, METHOD, FUNCTION, INTERFACE, ENUM, FIELD, UNKNOWN
language             varchar(64)
file_path            varchar(1024)
start_line           int
end_line             int
signature            varchar(1024)
summary              varchar(1024)
status               varchar(32)
created_at           datetime
updated_at           datetime
```

### 6.5 Existing Vector Tables

Use existing dedicated vector tables:

- `vec_rag_document`
- `vec_rag_chunk`

No new physical vector tables are required for the first version.

Distinguish file/code rows with metadata:

```json
{
  "assetType": "FILE",
  "chunkType": "TEXT_PARAGRAPH",
  "documentId": "...",
  "chunkId": "...",
  "sourceName": "..."
}
```

```json
{
  "assetType": "CODE_REPO",
  "chunkType": "CODE_SYMBOL",
  "documentId": "...",
  "chunkId": "...",
  "repoName": "...",
  "filePath": "...",
  "language": "Java",
  "symbolName": "...",
  "symbolType": "METHOD",
  "startLine": 10,
  "endLine": 42
}
```

## 7. Domain Entities And VOs

### 7.1 Entities

Add under `domain/agent/model/entity/rag`:

- `RagDocumentEntity`
- `RagChunkEntity`
- `RagCodeFileEntity`
- `RagCodeSymbolEntity`

These are domain persistence entities. Infrastructure PO classes must remain in infrastructure.

### 7.2 Ingestion Commands

Current commands can evolve:

- `RagFileIngestCommandEntity`
- `RagGitIngestCommandEntity`

Recommended final fields:

```java
class RagFileIngestCommandEntity {
    private String userId;
    private String sessionId;
    private List<RagFilePayloadEntity> files;
}
```

```java
class RagGitIngestCommandEntity {
    private String userId;
    private String sessionId;
    private String repoUrl;
    private String branch;
    private String userName;
    private String token;
}
```

`knowledgeTag` is deprecated for file upload. GitHub repository name can be stored as `sourceName`, not as a global knowledge tag.

### 7.3 Candidate VOs

Add under `domain/agent/model/valobj/context` or `domain/agent/model/valobj/rag`:

```java
class RagCandidateVO {
    private String candidateId;
    private String documentId;
    private String chunkId;
    private String assetType;   // FILE, CODE_REPO
    private String sourceType;  // RAG_DOCUMENT, RAG_CHUNK
    private String chunkType;
    private String title;
    private String summary;
    private String snippet;
    private Double sourceScore;
    private String sourceChannel;
    private List<String> sourceReasons;
    private Map<String, Object> metadata;
}
```

Add code-specific nested VO:

```java
class RagCodeCandidateMetaVO {
    private String repoName;
    private String filePath;
    private String language;
    private String symbolName;
    private String symbolType;
    private Integer startLine;
    private Integer endLine;
    private String signature;
    private String codeFunctionSummary;
    private String codePreview;
    private List<String> matchedSignals;
}
```

### 7.4 Materialized VOs

Add:

```java
class MaterializedRagVO {
    private String documentId;
    private String chunkId;
    private String assetType;
    private String sourceType;
    private String chunkType;
    private String title;
    private String summary;
    private String content;
    private Map<String, Object> metadata;
}
```

For code, content may be:

- Full selected symbol code for `CHUNKED_CONTEXT`.
- Full file only when selected as `FULL_TEXT`.
- Summary only for `SUMMARY_ONLY`.

## 8. Repository Ports

Add `IRagAssetRepository` under `domain/agent/adapter/repository`.

Responsibilities:

```java
interface IRagAssetRepository {

    String saveDocument(RagDocumentEntity document);

    void updateDocumentReady(String documentId, String summaryRef, String summary, int chunkCount, Map<String, Object> metadata);

    void updateDocumentFailed(String documentId, String failureCode, String failureMessage);

    Optional<RagDocumentEntity> findDocument(String documentId);

    List<RagDocumentEntity> listDocuments(String userId, String sourceType, int limit);

    void markDocumentDeleted(String documentId);

    void saveChunks(List<RagChunkEntity> chunks);

    Optional<RagChunkEntity> findChunk(String chunkId);

    List<RagChunkEntity> findChunksByIds(List<String> chunkIds);

    void saveCodeFiles(List<RagCodeFileEntity> files);

    void saveCodeSymbols(List<RagCodeSymbolEntity> symbols);

    List<RagCodeSymbolEntity> searchCodeSymbols(String userId, String query, int limit);

    List<RagChunkEntity> searchCodeChunksByKeyword(String userId, String query, int limit);
}
```

Infrastructure implements this with MyBatis.

## 9. Ingestion Services

### 9.1 `RagAssetIngestionService`

Package:

```text
domain/agent/service/rag/RagAssetIngestionService.java
```

Responsibilities:

- Validate input.
- Create `RagDocumentEntity` with `INGESTING`.
- Call source-specific ingestion flow.
- Persist chunks and metadata.
- Call LLM nodes for summaries/enrichment.
- Call `RagVectorIndexingService`.
- Mark document `READY` or `FAILED`.

Suggested public methods:

```java
class RagAssetIngestionService {
    RagIngestResultVO ingestFiles(RagFileIngestCommandEntity command);
    RagIngestResultVO ingestGitRepository(RagGitIngestCommandEntity command);
}
```

### 9.2 File Ingestion Flow

```text
upload files
 -> parse text
 -> paragraph chunk
 -> LLM document summary
 -> save document summary
 -> save chunks
 -> index document summary to vec_rag_document
 -> index chunks to vec_rag_chunk
 -> mark READY
```

Paragraph chunking policy:

- Split by blank lines first.
- Merge very short neighboring paragraphs.
- If a paragraph exceeds max chars, split by sentence or fixed char window.
- Preserve original paragraph order.
- Store `chunkNo`.

For vector indexing:

- Document vector text = LLM document summary + source name + optional keywords.
- Chunk vector text = original chunk content for normal files.

### 9.3 GitHub Repository Ingestion Flow

```text
git link
 -> clone repo to temp directory
 -> scan source files
 -> ignore binary/build/vendor/cache files
 -> collect repo statistics
 -> parse file-level and symbol-level chunks
 -> LLM repo summary
 -> LLM code chunk enrichment
 -> save document/file/symbol/chunk records
 -> index repo summary to vec_rag_document
 -> index enriched code retrieval text to vec_rag_chunk
 -> cleanup temp directory
 -> mark READY
```

Ignore policy first version:

- Directories: `.git`, `.idea`, `.vscode`, `target`, `build`, `dist`, `node_modules`, `.gradle`, `.mvn`, `out`, `coverage`.
- Files: binary files, images, archives, lock files, generated minified bundles.
- Allow source/config/doc extensions such as `.java`, `.py`, `.js`, `.ts`, `.vue`, `.go`, `.cpp`, `.c`, `.md`, `.txt`, `.yml`, `.yaml`, `.properties`, `.xml`, `.toml`.

Code chunk policy first version:

- Java: class/method/interface heuristic splitting first; later can add JavaParser.
- JavaScript/TypeScript/Python/Go/C/C++: heuristic function/class block splitting first.
- Always store file-level summary chunk.
- Store symbol-level chunks when parseable.
- If symbol parsing fails, fallback to file-level chunks by line window.

## 10. LLM Nodes

### 10.1 `RagDocumentSummaryNodeService`

Package:

```text
domain/agent/service/node/ragdocument/RagDocumentSummaryNodeService.java
```

Input VO:

```java
class RagDocumentSummaryInputVO {
    private String documentId;
    private String sourceType;
    private String sourceName;
    private List<String> sampledChunks;
    private Integer totalChunkCount;
}
```

Output VO:

```java
class RagDocumentSummaryOutputVO {
    private String summary;
    private List<String> topics;
    private List<String> keywords;
    private String suggestedTitle;
}
```

Purpose:

- Produce a useful asset summary for frontend display and `vec_rag_document`.
- Must not invent facts absent from the document.

### 10.2 `RagRepositorySummaryNodeService`

Input VO:

```java
class RagRepositorySummaryInputVO {
    private String documentId;
    private String repoName;
    private String repoUrl;
    private String structure;
    private Map<String, Integer> languageStats;
    private List<RagCodeFileSummaryInputVO> topFiles;
}
```

Output VO:

```java
class RagRepositorySummaryOutputVO {
    private String summary;
    private String projectPurpose;
    private List<String> mainModules;
    private List<String> importantEntryPoints;
    private List<String> keywords;
}
```

### 10.3 `RagCodeChunkEnrichmentNodeService`

Input VO:

```java
class RagCodeChunkEnrichmentInputVO {
    private String repoName;
    private String filePath;
    private String language;
    private String symbolName;
    private String symbolType;
    private String signature;
    private Integer startLine;
    private Integer endLine;
    private String code;
}
```

Output VO:

```java
class RagCodeChunkEnrichmentOutputVO {
    private String summary;
    private String retrievalText;
    private List<String> keywords;
    private List<String> dependencies;
    private List<String> responsibilities;
}
```

The `retrievalText` should combine natural language and original code:

```text
Repository: ...
Path: ...
Language: ...
Symbol: ...
Responsibilities: ...
Important dependencies/calls: ...
Code:
...
```

This lets natural-language user questions and code-fragment questions both hit the same chunk.

## 11. Vector Indexing Contract

Add `RagVectorIndexingService`.

Package:

```text
domain/agent/service/rag/RagVectorIndexingService.java
```

It uses:

- `IVectorMemoryRepository`
- `IVectorIndexRepository`

Document indexing:

```java
vectorMemoryRepository.upsert(VectorIndexRecordVO.builder()
    .collectionType(VectorCollectionTypeEnumVO.RAG_DOCUMENT)
    .sourceType(VectorSourceTypeEnumVO.RAG_DOCUMENT)
    .sourceId(document.getDocumentId())
    .userId(document.getUserId())
    .sessionId(document.getSessionId())
    .text(documentSummaryText)
    .summary(document.getSummary())
    .metadata(documentMetadata)
    .build());
```

Chunk indexing:

```java
vectorMemoryRepository.upsert(VectorIndexRecordVO.builder()
    .collectionType(VectorCollectionTypeEnumVO.RAG_CHUNK)
    .sourceType(VectorSourceTypeEnumVO.RAG_CHUNK)
    .sourceId(chunk.getChunkId())
    .userId(chunk.getUserId())
    .sessionId(chunk.getSessionId())
    .text(retrievalTextOrOriginalText)
    .summary(chunk.getSummary())
    .metadata(chunkMetadata)
    .build());
```

Also write `agent_vector_index` through `IVectorIndexRepository`, following the pattern of `MemoryVectorIndexingService`.

## 12. Context Recall Design

### 12.1 `RagContextRecallPreselector`

Package:

```text
domain/agent/service/rag/context/RagContextRecallPreselector.java
```

Public method:

```java
ContextCandidateBundleVO recall(ContextPreparationCommand command);
```

or preferably:

```java
List<RagCandidateVO> recall(ContextPreparationCommand command);
```

Recommended integration is the second option, with `ContextPreparationService` merging the list into the full candidate bundle.

Recall steps:

```text
1. Validate user input.
2. Search vec_rag_document.
3. Search vec_rag_chunk.
4. Run MySQL keyword/path/symbol search for code assets.
5. Resolve all vector hits back to MySQL document/chunk rows.
6. Build RagCandidateVO cards.
7. Merge duplicates by chunk/document id.
8. Return top N candidates.
```

Vector recall:

- Search `vec_rag_document`, topK small, for repository/file-level summaries.
- Search `vec_rag_chunk`, topK larger, for paragraph/code chunks.

Keyword/path/symbol recall:

- If the query contains likely file paths, class names, method names, stack trace fragments, package names, or extension patterns, query MySQL code symbol/file indexes.
- These results become normal candidates with `sourceChannel = KEYWORD_METADATA`.
- They still go through `ContextPlanner`.

Candidate merge ranking:

- Prefer exact path/symbol match over weak semantic hit.
- Prefer chunk-level hit for implementation details.
- Prefer document-level hit for project/file overview questions.
- Deduplicate identical chunk id.
- Keep both document summary and chunk candidates when they represent different useful context levels.

No candidate is auto-injected.

## 13. ContextCandidateBundle Changes

Add to `ContextCandidateBundleVO`:

```java
private List<RagCandidateVO> ragCandidates;
```

`ContextPreparationService.merge(...)` must merge RAG candidates from the RAG recall branch.

The future shape becomes:

```java
ContextCandidateBundleVO {
    runMeta
    userInput
    fixedRecentMessages
    recentMessages
    sessionTaskSummary
    sessionSummaries
    memoryCandidates
    ragCandidates
    evidenceCandidates
    userClarifications
    availableCapabilities
    pendingAction
    tokenBudget
}
```

Artifact candidates remain deprecated for the redesigned memory path unless future artifact behavior is reintroduced deliberately.

## 14. ContextPreparationService Integration

Current:

```text
mysqlFuture = contextCandidatePreselector.buildCandidates(...)
vectorFuture = vectorContextRecallPreselector.recall(...)
merge(mysqlBundle, vectorBundle)
```

Target:

```text
mysqlFuture = contextCandidatePreselector.buildCandidates(...)
vectorMemoryFuture = vectorContextRecallPreselector.recall(...)
ragFuture = ragContextRecallPreselector.recall(...)
merge(mysqlBundle, vectorMemoryBundle, ragCandidates)
```

RAG branch requirements:

- Run in parallel with bounded executor.
- Have its own timeout.
- Failure must degrade gracefully to empty RAG candidates.
- Failure must not block deterministic MySQL context.
- Log human-readable stage events.

Configuration:

```yaml
auto-agent:
  context:
    vector-recall-timeout-millis: 1500
    rag-recall-timeout-millis: 2000
  rag:
    recall-enabled: true
    document-top-k: 4
    chunk-top-k: 8
    min-score: 0.30
```

## 15. ContextPlanner Prompt Design

The planner prompt must be rewritten from a generic candidate selector into a typed candidate selector.

It should explicitly say:

```text
You need to judge whether each candidate type should be selected:
- long-term memories
- conversation summaries
- RAG internal file candidates
- RAG internal code candidates
For selected candidates, choose an injection level.
Do not answer the user.
Do not call tools.
Do not select by score alone.
Select only context that helps MainAgent answer the current user request.
```

### 15.1 Long-Term Memory Rules

Input field:

- `memoryCandidates`

Rules:

- Select memories related to the current user question, user preference, project background, or stable constraint.
- If duplicate memories exist, choose the newest, most complete, and most reliable one.
- Do not select stale or unrelated personal facts.

Default context level:

- `FULL_TEXT`

Reason:

- Long-term memory is already compact and curated.

### 15.2 Conversation Summary Rules

Input field:

- `sessionSummaries`

Rules:

- Use summaries to recover older conversation context, historical decisions, previous requirements, or previous generated content.
- If the user asks to continue, compare, revise, reuse, or inspect prior generated content, select the relevant summary or turn.
- If a recent full turn already covers the same content, avoid selecting duplicate summaries.

Context levels:

- `SUMMARY_ONLY`: only high-level reminder is needed.
- `SUMMARY_PLUS_SNIPPET`: some details are useful, but exact original wording is not needed.
- `FULL_TEXT`: exact prior wording or generated content is needed.

### 15.3 RAG File Candidate Rules

Input field:

- `ragCandidates` where `assetType = FILE`

Planner sees:

- File name.
- Document summary.
- Chunk summary.
- Bounded snippet.
- Match reasons.
- Score.

Rules:

- Select a RAG file candidate only when uploaded private file knowledge helps answer the current request.
- Do not select it merely because of semantic overlap.
- For broad questions about an uploaded document, prefer document summary.
- For concrete factual questions, select the relevant chunks.

Context levels:

- `SUMMARY_ONLY`: file/document summary is enough.
- `CHUNKED_CONTEXT`: specific paragraphs are needed.
- `FULL_TEXT`: only when the file is small or the user explicitly asks for the full file.

### 15.4 RAG Code Candidate Rules

Input field:

- `ragCandidates` where `assetType = CODE_REPO`

Planner sees code candidate cards:

- Repo name.
- File path.
- Language.
- Symbol name and type.
- Line range.
- Code function summary.
- Matched signals.
- Score.
- Short code preview.

Rules:

- Judge relevance using code function summary, file path, symbol metadata, matched signals, and user question.
- For architecture/module responsibility questions, prefer repository summary or file-level summaries.
- For implementation detail, bug, flow, dependency, or modification questions, prefer class/function/method chunks.
- Do not select large raw file content unless necessary.
- Exact path/symbol matches are strong evidence, but still require planner selection.

Context levels:

- `SUMMARY_ONLY`: only module/file responsibility is needed.
- `CHUNKED_CONTEXT`: concrete source code snippet is needed.
- `FULL_TEXT`: only for small files or explicit full-file requests.

## 16. ContextPlanner Output Contract

Current output supports:

```java
List<Map<String, Object>> selectedContext
```

The existing shape can be reused by adding recognized source types:

```json
{
  "sourceType": "RAG_CHUNK",
  "sourceId": "rag-chunk-...",
  "contextLevel": "CHUNKED_CONTEXT",
  "priority": 1,
  "confidence": 0.86,
  "reason": "This code chunk explains the runtime context preparation flow asked by the user."
}
```

Supported RAG source types:

- `RAG_DOCUMENT`
- `RAG_CHUNK`

No new planner output schema is required for first version, but validator/materializer must support these source types.

## 17. ContextSelectionValidator Changes

`ContextSelectionValidator` must include RAG candidate ids as valid ids.

Valid ids:

- `RagCandidateVO.documentId`
- `RagCandidateVO.chunkId`
- `RagCandidateVO.candidateId`

Source-type aware validation should be preferred long term:

```text
RAG_DOCUMENT selection must match documentId or candidateId.
RAG_CHUNK selection must match chunkId or candidateId.
```

First version can keep the existing valid-id set approach if all RAG ids are added.

## 18. ContextSelectionMergePolicy Changes

Current merge policy already has:

```java
if ("RAG".equals(sourceType) || "RAG_CHUNK".equals(sourceType) || "RAG_DOCUMENT".equals(sourceType)) {
    return "RAG:" + sourceId;
}
```

This is acceptable for first version.

Optional improvement:

- Deduplicate `RAG_CHUNK` under its parent document only when selecting document full text makes chunk redundant.
- Otherwise keep multiple selected chunks, because multiple chunks from the same document may answer different parts of a question.

Therefore first version should not over-merge all chunks by document id.

## 19. ContextMaterializer Changes

Add RAG materialization:

```text
selected RAG_DOCUMENT / RAG_CHUNK
 -> find matching RagCandidateVO
 -> load document/chunk from IRagAssetRepository
 -> apply ContextLevel
 -> build MaterializedRagVO
```

Rules:

- `SUMMARY_ONLY`:
  - include title, summary, source metadata.
  - do not load full payload.
- `SUMMARY_PLUS_SNIPPET`:
  - include summary and bounded snippet.
- `CHUNKED_CONTEXT`:
  - load chunk content by `contentRef`.
  - for code chunks, include file path, symbol, line range.
- `FULL_TEXT`:
  - for document candidate, load full document only if small enough or explicitly selected.
  - for code file candidate, load file content only if small enough.
  - otherwise degrade to `CHUNKED_CONTEXT` and set token budget warning.

Add to `MainAgentStateViewBuildCommand`:

```java
private List<MaterializedRagVO> ragPack;
```

Add to `MainAgentStateViewVO`:

```java
private List<MaterializedRagVO> ragPack;
```

Add to `MainAgentStateViewBuilder`:

```java
.ragPack(command.getRagPack() == null ? List.of() : command.getRagPack())
```

Add token shrink support to `ContextBudgetManager` if needed.

## 20. MainAgentStateView Contract

New state view section:

```json
{
  "ragPack": [
    {
      "documentId": "rag-doc-...",
      "chunkId": "rag-chunk-...",
      "assetType": "CODE_REPO",
      "sourceType": "RAG_CHUNK",
      "chunkType": "CODE_SYMBOL",
      "title": "ContextPreparationService.prepare",
      "summary": "Prepares context candidates for MainAgent by combining MySQL and vector recall.",
      "content": "... selected code or paragraph ...",
      "metadata": {
        "repoName": "ai-agent-station-study",
        "filePath": "...",
        "language": "Java",
        "symbolName": "prepare",
        "startLine": 36,
        "endLine": 76
      }
    }
  ]
}
```

MainAgent prompt should later mention:

```text
ragPack contains selected private file/code asset context chosen by ContextPlanner.
Treat it as retrieved source context, not as user preference or conversation memory.
Use it to answer questions about uploaded files or repositories.
Do not claim a RAG source exists unless it appears in ragPack.
```

## 21. Upload API Design

Current endpoints can remain:

- `POST /api/v1/rag/knowledge/files`
- `POST /api/v1/rag/knowledge/git`

Recommended DTO behavior:

### 21.1 File Upload

Request:

- Multipart `files`
- Optional `sessionId`
- No required `knowledgeTag`

Response:

```json
{
  "documents": [
    {
      "documentId": "...",
      "sourceName": "xxx.txt",
      "status": "INGESTING"
    }
  ]
}
```

First version can be synchronous and return `READY`, but the contract should allow async.

### 21.2 GitHub Upload

Request:

```json
{
  "repoUrl": "...",
  "branch": "main",
  "userName": "...",
  "token": "..."
}
```

Response:

```json
{
  "documentId": "...",
  "sourceName": "repo-name",
  "status": "INGESTING"
}
```

### 21.3 Asset List

Add:

```text
GET /api/v1/rag/assets?sourceType=FILE|GITHUB_REPO
```

Returns document cards for frontend management.

### 21.4 Asset Delete

Add:

```text
DELETE /api/v1/rag/assets/{documentId}
```

Behavior:

- Mark document/chunks deleted in MySQL.
- Disable vector records for document and chunks.
- Do not physically delete payloads in first version unless storage policy requires it.

### 21.5 Asset Reindex

Add later:

```text
POST /api/v1/rag/assets/{documentId}/reindex
```

Not required for MVP.

## 22. Existing RAG Runtime Compatibility

Keep existing action-based RAG for now:

- `MainAgentActionTypeEnumVO.RETRIEVE_RAG`
- `RetrieveRagActionHandler`
- `RagRuntime`
- `agent_rag_query`
- `agent_rag_hit`

But primary new path is automatic context preparation.

Compatibility options:

1. Keep old `SpringAiRagRetrieverAdapter` unchanged temporarily.
2. Later replace it with an adapter that queries the new `IRagAssetRepository` + `IVectorMemoryRepository`.
3. Eventually deprecate `RETRIEVE_RAG` if MainAgent no longer needs explicit RAG action.

Recommended first implementation:

- Do not remove action-based RAG.
- Add new context-preparation RAG in parallel.
- Once stable, decide whether explicit action RAG remains useful for manual second-pass retrieval.

## 23. Configuration

Add `AutoAgentRagAssetProperties`.

Suggested config:

```yaml
auto-agent:
  rag:
    enabled: true
    asset:
      enabled: true
      file:
        max-file-bytes: 5242880
        max-chunk-chars: 1800
        min-chunk-chars: 200
      git:
        max-files: 500
        max-file-bytes: 524288
        max-total-bytes: 52428800
        clone-timeout-seconds: 60
      recall:
        enabled: true
        document-top-k: 4
        chunk-top-k: 8
        code-keyword-top-k: 8
        min-score: 0.30
        timeout-millis: 2000
```

The old `auto-agent.rag.vector-name` remains only for legacy `PgVectorStore` until migration.

## 24. Error Handling

Ingestion:

- Document starts as `INGESTING`.
- Per-file parse errors should be recorded.
- Fatal ingestion error marks document `FAILED`.
- LLM summary/enrichment failures should mark the relevant chunk failed or use a bounded fallback only if the user later approves fallback behavior.
- Since the user wants LLM summaries, empty LLM output should be treated as failure for the affected summary/enrichment step.

Recall:

- RAG recall failure returns empty candidates.
- It must not fail the whole run.
- Log error and human-readable stage.

Materialization:

- Missing selected chunk should be skipped with trace.
- Oversized content should degrade according to token budget.
- Do not expose raw internal failure details to normal UI.

## 25. Security And Privacy

- Uploaded files and cloned repositories are private assets.
- RAG content should be scoped by `user_id`.
- `session_id` is optional; global user assets are visible across sessions for that user.
- Git tokens must never be stored in plaintext payloads or metadata.
- Temporary cloned repositories must be deleted after ingestion.
- Frontend/API must not expose internal payload refs, vector ids, prompt text, or trace details.

## 26. Testing Plan

### 26.1 Unit Tests

- Paragraph chunking.
- Code file ignore policy.
- Code symbol heuristic splitter.
- RAG candidate merge/dedupe.
- ContextPlanner prompt builder includes typed RAG rules.
- ContextSelectionValidator accepts RAG document/chunk ids.
- ContextMaterializer materializes selected RAG chunks.

### 26.2 Repository Tests

- Save/list/update `agent_rag_document`.
- Save/find chunks.
- Save code files and symbols.
- Keyword/path/symbol search.
- Vector index records written for documents and chunks.

### 26.3 Integration Tests

- Upload file -> document/chunks saved -> vectors indexed.
- Upload Git repo fixture -> repo/file/symbol/chunk records saved -> vectors indexed.
- User query -> RAG candidates appear in `ContextCandidateBundleVO`.
- Planner selects RAG chunk -> `MainAgentStateViewVO.ragPack` contains materialized content.
- RAG recall timeout degrades gracefully.

### 26.4 Regression Tests

- Existing memory recall still works.
- Existing `RETRIEVE_RAG` action tests remain passing until intentionally migrated.
- Existing frontend upload endpoints still return successful responses.

## 27. Implementation Phases

### Phase 1: Data And Repository Foundation

- Add MySQL DDL for RAG asset tables.
- Add domain entities and repository port.
- Add infrastructure PO/DAO/MyBatis/repository adapter.
- Add tests for repository behavior.

### Phase 2: File Asset Ingestion

- Add paragraph chunker.
- Add document summary LLM node.
- Save document/chunks.
- Index document/chunks to `vec_rag_document` and `vec_rag_chunk`.
- Wire file upload endpoint to new service.

### Phase 3: Code Repository Ingestion

- Add Git ingestion adapter around current JGit behavior.
- Add ignore policy.
- Add file/symbol chunking.
- Add repository summary and code enrichment LLM nodes.
- Save repo/file/symbol/chunk metadata.
- Index enriched retrieval text.

### Phase 4: Context Preparation Recall

- Add `RagContextRecallPreselector`.
- Add `ragCandidates` to `ContextCandidateBundleVO`.
- Extend `ContextPreparationService` to run RAG recall in parallel.
- Add timeout/fallback config.

### Phase 5: Planner And Materializer Integration

- Rewrite `ContextPlannerPromptBuilder` with typed candidate rules.
- Add RAG ids to `ContextSelectionValidator`.
- Add RAG materialization to `ContextMaterializer`.
- Add `ragPack` to `MainAgentStateViewVO`.
- Update budget shrinking if needed.

### Phase 6: Frontend Asset Management

- Keep current plus-menu upload entry.
- Show file and GitHub upload as separate modes.
- Add asset list/status display if needed.
- Add delete/reindex hooks after backend endpoints exist.

### Phase 7: Migration And Cleanup

- Decide whether to replace old `SpringAiRagRetrieverAdapter` with new asset recall.
- Decide whether `RETRIEVE_RAG` remains as a manual second-pass action.
- Remove or disable old `vector_store` upload path after the new path is verified.

## 28. Open Decisions

The following decisions should be confirmed before implementation:

1. Whether file/Git assets are scoped globally per user or per session by default.
   - Recommended: global per user, with optional session metadata.
2. Whether ingestion is synchronous for MVP.
   - Recommended: synchronous for files, asynchronous-capable contract; Git can become async when repo size grows.
3. Whether first code parser is heuristic or JavaParser/tree-sitter based.
   - Recommended: heuristic first to avoid heavy dependency/design delay, then upgrade parser later.
4. Whether `RETRIEVE_RAG` action should remain visible to MainAgent prompt.
   - Recommended: keep for compatibility during transition, but position automatic RAG context as the primary path.

## 29. Acceptance Criteria

The redesign is complete when:

- Uploaded files create `agent_rag_document` and `agent_rag_chunk` records.
- GitHub repositories create repo, file, symbol, and chunk records.
- LLM-generated summaries/enriched retrieval text are stored and indexed.
- `vec_rag_document` and `vec_rag_chunk` contain dedicated RAG vectors.
- User input triggers RAG asset recall during context preparation.
- RAG candidates are visible to `ContextPlanner`.
- `ContextPlanner` uses typed rules for long-term memory, summaries, RAG files, and RAG code.
- Selected RAG candidates are materialized into `MainAgentStateViewVO.ragPack`.
- Existing memory recall behavior and old RAG action compatibility are not broken.

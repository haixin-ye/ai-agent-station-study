# AutoAgent RAG Asset Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the AutoAgent-native RAG asset module so uploaded files and GitHub repositories become private knowledge assets recalled during context preparation and injected through `MainAgentStateViewVO.ragPack`.

**Architecture:** MySQL is the RAG asset source of truth; pgvector is the semantic index. Upload and Git ingestion create `agent_rag_document`, `agent_rag_chunk`, and code metadata rows, then index document summaries and chunk retrieval text into `vec_rag_document` and `vec_rag_chunk`. Runtime context preparation runs RAG recall in parallel with existing memory recall, gives structured RAG candidates to `ContextPlanner`, and materializes only selected candidates into `ragPack`.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module DDD, MyBatis XML mappers, MySQL, pgvector, Spring AI embedding via existing `IVectorMemoryRepository`, existing `NodeInvocationPipeline` for LLM nodes.

---

## Scope

This plan implements the backend foundation and runtime integration first. Frontend asset management is a follow-up after the backend contracts are stable.

The existing `RETRIEVE_RAG` action and legacy `RagRuntime` remain compatible during this plan. The new automatic RAG context path is added beside it.

## File Structure Map

### Domain Entities And Ports

- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/rag/RagDocumentEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/rag/RagChunkEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/rag/RagCodeFileEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/rag/RagCodeSymbolEntity.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/rag/RagFileIngestCommandEntity.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/rag/RagGitIngestCommandEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/IRagAssetRepository.java`

### Context VOs

- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/RagCandidateVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/RagCodeCandidateMetaVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/MaterializedRagVO.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/ContextCandidateBundleVO.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/MainAgentStateViewBuildCommand.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/MainAgentStateViewVO.java`

### RAG Domain Services

- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/RagAssetIngestionService.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/RagVectorIndexingService.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/chunk/RagParagraphChunker.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/code/RagCodeFilePolicy.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/code/RagCodeHeuristicSplitter.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/context/RagContextRecallPreselector.java`

### LLM Node Contracts

- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/rag/RagDocumentSummaryInputVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/rag/RagDocumentSummaryOutputVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/rag/RagRepositorySummaryInputVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/rag/RagRepositorySummaryOutputVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/rag/RagCodeChunkEnrichmentInputVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/rag/RagCodeChunkEnrichmentOutputVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/node/ragdocument/RagDocumentSummaryNodeService.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/node/ragrepo/RagRepositorySummaryNodeService.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/node/ragcode/RagCodeChunkEnrichmentNodeService.java`

### Context Integration

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/ContextPreparationService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/ContextSelectionValidator.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/ContextMaterializer.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/MainAgentStateViewBuilder.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/ContextPlannerPromptBuilder.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/DefaultRuntimeComponentPorts.java`

### Infrastructure

- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/AgentRagDocumentPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/AgentRagChunkPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/AgentRagCodeFilePO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/AgentRagCodeSymbolPO.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/IAgentRagDocumentDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/IAgentRagChunkDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/IAgentRagCodeFileDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/IAgentRagCodeSymbolDao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/repository/RagAssetRepository.java`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/agent_rag_document_mapper.xml`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/agent_rag_chunk_mapper.xml`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/agent_rag_code_file_mapper.xml`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/agent_rag_code_symbol_mapper.xml`

### SQL And App Wiring

- Modify: `docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql`
- Create: `docs/dev-ops/mysql/sql/auto-agent-rag-asset-migration.sql`
- Modify: `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRuntimeConfig.java`
- Modify: `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRagProperties.java`
- Modify: `ai-agent-station-study-app/src/main/resources/application-dev.yml`

### API

- Modify: `ai-agent-station-study-api/src/main/java/yhx/com/api/dto/RagGitAnalyzeRequestDTO.java`
- Create: `ai-agent-station-study-api/src/main/java/yhx/com/api/dto/RagAssetResponseDTO.java`
- Modify: `ai-agent-station-study-api/src/main/java/yhx/com/api/IRagApi.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/RAGController.java`

### Tests

- Create: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/rag/RagParagraphChunkerTest.java`
- Create: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/rag/RagCodeHeuristicSplitterTest.java`
- Create: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/rag/RagVectorIndexingServiceTest.java`
- Create: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/rag/RagContextRecallPreselectorTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/context/ContextPreparationServiceTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/context/ContextMaterializationTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/contract/ContextPlannerContractTest.java`

---

## Task 1: Add RAG Asset Schema And Domain Contracts

**Files:**
- Modify: `docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql`
- Create: `docs/dev-ops/mysql/sql/auto-agent-rag-asset-migration.sql`
- Create domain entities and `IRagAssetRepository` listed in the file map.

- [ ] **Step 1: Add focused DDL for asset truth tables**

Add `agent_rag_document`, `agent_rag_chunk`, `agent_rag_code_file`, and `agent_rag_code_symbol` to both the rebuild script and migration script.

Use this table shape:

```sql
CREATE TABLE `agent_rag_document` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `document_id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_name` varchar(512) NOT NULL,
  `source_uri` varchar(1024) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `summary_ref` varchar(64) DEFAULT NULL,
  `summary` varchar(1024) DEFAULT NULL,
  `chunk_count` int DEFAULT 0,
  `file_count` int DEFAULT 0,
  `code_line_count` int DEFAULT 0,
  `language_stats_json` json DEFAULT NULL,
  `metadata_json` json DEFAULT NULL,
  `failure_code` varchar(128) DEFAULT NULL,
  `failure_message` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `ready_at` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_rag_document_id` (`document_id`),
  KEY `idx_agent_rag_document_user` (`user_id`, `source_type`, `status`, `updated_at`),
  KEY `idx_agent_rag_document_session` (`session_id`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent RAG asset document';
```

Mirror this style for chunk/code tables with unique public ids and indexes on `document_id`, `user_id`, `status`, `file_path`, and `symbol_name`.

- [ ] **Step 2: Create domain entities**

Create Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor` entities:

```java
package yhx.com.domain.agent.model.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentEntity {
    private String documentId;
    private String userId;
    private String sessionId;
    private String sourceType;
    private String sourceName;
    private String sourceUri;
    private String status;
    private String summaryRef;
    private String summary;
    private Integer chunkCount;
    private Integer fileCount;
    private Integer codeLineCount;
    private String languageStatsJson;
    private String metadataJson;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime readyAt;
    private LocalDateTime deletedAt;
}
```

Repeat the same style for `RagChunkEntity`, `RagCodeFileEntity`, and `RagCodeSymbolEntity` with the fields from the spec.

- [ ] **Step 3: Add repository port**

Create `IRagAssetRepository` with this exact first-version contract:

```java
package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeFileEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeSymbolEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;

import java.util.List;
import java.util.Optional;

public interface IRagAssetRepository {

    String saveDocument(RagDocumentEntity document);

    void updateDocumentReady(String documentId, String summaryRef, String summary, int chunkCount, String languageStatsJson, String metadataJson);

    void updateDocumentFailed(String documentId, String failureCode, String failureMessage);

    Optional<RagDocumentEntity> findDocument(String documentId);

    List<RagDocumentEntity> listDocuments(String userId, String sourceType, int limit);

    void markDocumentDeleted(String documentId);

    void saveChunks(List<RagChunkEntity> chunks);

    Optional<RagChunkEntity> findChunk(String chunkId);

    List<RagChunkEntity> findChunksByIds(List<String> chunkIds);

    List<RagChunkEntity> findChunksByDocumentId(String documentId);

    void saveCodeFiles(List<RagCodeFileEntity> files);

    void saveCodeSymbols(List<RagCodeSymbolEntity> symbols);

    List<RagCodeSymbolEntity> searchCodeSymbols(String userId, String query, int limit);

    List<RagChunkEntity> searchCodeChunksByKeyword(String userId, String query, int limit);
}
```

- [ ] **Step 4: Run compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: compile passes after entity and port creation.

---

## Task 2: Implement MyBatis Persistence Adapter

**Files:**
- Create PO, DAO, XML mapper, and `RagAssetRepository` files listed in the file map.
- Test: add repository-focused tests if a DB slice is available; otherwise compile plus mapper XML inspection is the first checkpoint.

- [ ] **Step 1: Create PO classes matching table columns**

Follow the existing PO style in `AgentTurnSummaryPO` and `AgentLongTermMemoryPO`.

Each PO should use Lombok and Java time fields:

```java
@Data
public class AgentRagDocumentPO {
    private Long id;
    private String documentId;
    private String userId;
    private String sessionId;
    private String sourceType;
    private String sourceName;
    private String sourceUri;
    private String status;
    private String summaryRef;
    private String summary;
    private Integer chunkCount;
    private Integer fileCount;
    private Integer codeLineCount;
    private String languageStatsJson;
    private String metadataJson;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime readyAt;
    private LocalDateTime deletedAt;
}
```

- [ ] **Step 2: Create DAO interfaces**

Follow existing DAO patterns and use `@Mapper`.

Example:

```java
@Mapper
public interface IAgentRagDocumentDao {
    void insert(AgentRagDocumentPO document);
    int updateReady(@Param("documentId") String documentId,
                    @Param("summaryRef") String summaryRef,
                    @Param("summary") String summary,
                    @Param("chunkCount") int chunkCount,
                    @Param("languageStatsJson") String languageStatsJson,
                    @Param("metadataJson") String metadataJson);
    int updateFailed(@Param("documentId") String documentId,
                     @Param("failureCode") String failureCode,
                     @Param("failureMessage") String failureMessage);
    AgentRagDocumentPO selectByDocumentId(@Param("documentId") String documentId);
    List<AgentRagDocumentPO> listDocuments(@Param("userId") String userId,
                                           @Param("sourceType") String sourceType,
                                           @Param("limit") int limit);
    int markDeleted(@Param("documentId") String documentId);
}
```

- [ ] **Step 3: Create XML mappers**

Place XML files under `ai-agent-station-study-app/src/main/resources/mybatis/mapper/`.

Use explicit column lists and `resultMap`, mirroring existing mapper XML style.

For chunk lookup, include:

```sql
SELECT ... FROM agent_rag_chunk
WHERE chunk_id IN
<foreach collection="chunkIds" item="chunkId" open="(" separator="," close=")">
  #{chunkId}
</foreach>
AND status = 'ACTIVE'
```

- [ ] **Step 4: Implement `RagAssetRepository`**

Map PO <-> entity locally inside the adapter. Keep domain free of MyBatis classes.

Required behavior:

- `saveDocument` generates `documentId` when absent: `"rag-doc-" + UUID.randomUUID()`.
- `saveChunks` ignores null/empty list.
- `findChunksByIds` returns `List.of()` for null/empty ids.
- `searchCodeSymbols` and `searchCodeChunksByKeyword` return `List.of()` for blank query.

- [ ] **Step 5: Run mapper/compile checkpoint**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: compile passes and MyBatis XML resources are loadable by app tests in later tasks.

---

## Task 3: Add RAG Context And Materialization VOs

**Files:**
- Create: `RagCandidateVO.java`
- Create: `RagCodeCandidateMetaVO.java`
- Create: `MaterializedRagVO.java`
- Modify context bundle/build/state VOs.
- Tests: `ContextMaterializationTest`.

- [ ] **Step 1: Create candidate and materialized VOs**

Use Lombok data carriers under `domain/agent/model/valobj/context`.

`RagCandidateVO` fields:

```java
private String candidateId;
private String documentId;
private String chunkId;
private String assetType;
private String sourceType;
private String chunkType;
private String title;
private String summary;
private String snippet;
private Double sourceScore;
private String sourceChannel;
private List<String> sourceReasons;
private RagCodeCandidateMetaVO codeMeta;
private Map<String, Object> metadata;
```

`MaterializedRagVO` fields:

```java
private String documentId;
private String chunkId;
private String assetType;
private String sourceType;
private String chunkType;
private String title;
private String summary;
private String content;
private Map<String, Object> metadata;
```

- [ ] **Step 2: Add fields to existing context VOs**

Add:

```java
private List<RagCandidateVO> ragCandidates;
```

to `ContextCandidateBundleVO`.

Add:

```java
private List<MaterializedRagVO> ragPack;
```

to `MainAgentStateViewVO` and `MainAgentStateViewBuildCommand`.

- [ ] **Step 3: Update `MainAgentStateViewBuilder`**

Add:

```java
.ragPack(command.getRagPack() == null ? List.of() : command.getRagPack())
```

to the builder.

- [ ] **Step 4: Compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: compile passes with new VO fields.

---

## Task 4: Implement RAG Vector Indexing Service

**Files:**
- Create: `RagVectorIndexingService.java`
- Test: `RagVectorIndexingServiceTest.java`

- [ ] **Step 1: Write unit test with fake vector repositories**

Test cases:

- document indexes into `VectorCollectionTypeEnumVO.RAG_DOCUMENT`
- chunk indexes into `VectorCollectionTypeEnumVO.RAG_CHUNK`
- chunk `sourceId` equals `chunkId`
- `agent_vector_index` save is called after vector upsert

Use the fake style from `MemoryVectorIndexingServiceTest`.

- [ ] **Step 2: Implement service**

Constructor dependencies:

```java
private final IVectorMemoryRepository vectorMemoryRepository;
private final IVectorIndexRepository vectorIndexRepository;
private final IPayloadRepository payloadRepository;
```

Public methods:

```java
public void indexDocument(RagDocumentEntity document, String indexText)
public void indexChunk(RagChunkEntity chunk, String indexText)
```

Use `VectorIndexRecordVO` and `AgentVectorIndexEntity`, following `MemoryVectorIndexingService`.

- [ ] **Step 3: Run test**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RagVectorIndexingServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: test passes.

---

## Task 5: Implement File Chunking And File Asset Ingestion

**Files:**
- Create: `RagParagraphChunker.java`
- Create LLM document summary node files.
- Create/modify: `RagAssetIngestionService.java`
- Modify: `RagService.java` to delegate file ingest to the new service.
- Tests: `RagParagraphChunkerTest.java`, `RagServiceTest.java`.

- [ ] **Step 1: Test paragraph chunking**

Cases:

- blank-line paragraphs become stable chunks
- short paragraphs merge until `minChunkChars`
- long paragraphs split below `maxChunkChars`
- original order is preserved

- [ ] **Step 2: Implement `RagParagraphChunker`**

Constructor values:

```java
int minChunkChars
int maxChunkChars
```

Method:

```java
public List<String> chunk(String text)
```

Return `List.of()` for blank input.

- [ ] **Step 3: Add document summary node service**

Implement `RagDocumentSummaryNodeService` with `NodeInvocationPipeline`, using component code `RAG_DOCUMENT_SUMMARY`.

Add enum value to `AgentComponentCodeEnumVO`:

```java
RAG_DOCUMENT_SUMMARY
```

Add prompt builder text under `ContextPlannerPromptBuilder` only when planner rules are updated in Task 9; do not mix node prompt with planner prompt.

- [ ] **Step 4: Implement file ingestion**

`RagAssetIngestionService.ingestFiles`:

```text
for each uploaded file:
  save document INGESTING
  parse UTF-8 text for txt/md; use existing Tika path for other files only through infrastructure later
  chunk by paragraph
  summarize document with LLM node
  save summary payload
  save chunks with content payloads
  index document and chunks
  mark document READY
on failure:
  mark document FAILED
```

First version can support `.txt` and `.md` directly. Existing Tika parsing remains in legacy `RagRepository` until moved into an extraction adapter.

- [ ] **Step 5: Run targeted tests and compile**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RagParagraphChunkerTest,RagServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -q -DskipTests compile
```

Expected: tests and compile pass.

---

## Task 6: Implement Code Repository Parsing Foundation

**Files:**
- Create: `RagCodeFilePolicy.java`
- Create: `RagCodeHeuristicSplitter.java`
- Create code/repo LLM node services and VO contracts.
- Tests: `RagCodeHeuristicSplitterTest.java`

- [ ] **Step 1: Add file policy tests**

Cases:

- skip `.git`, `node_modules`, `target`, `build`, `dist`, `.idea`, `.vscode`
- allow `.java`, `.py`, `.js`, `.ts`, `.vue`, `.go`, `.cpp`, `.c`, `.md`, `.txt`, `.yml`, `.yaml`, `.properties`, `.xml`, `.toml`
- reject lock files and binary-like extensions

- [ ] **Step 2: Implement file policy**

Methods:

```java
public boolean shouldVisitDirectory(String directoryName)
public boolean shouldIndexFile(String fileName, long sizeBytes)
public String languageOf(String fileName)
```

- [ ] **Step 3: Add heuristic splitter tests**

Test Java class/method splitting:

```java
class Demo {
    void run() {
    }
}
```

Expected one class-level or method-level symbol with path metadata, line range, signature, and code.

- [ ] **Step 4: Implement heuristic splitter**

Use conservative parsing:

- Java: detect class/interface/enum declarations and method signatures with brace counting.
- JavaScript/TypeScript/Python/Go/C/C++: detect common function/class starts.
- Fallback: file-level chunk by line window.

Return a local VO such as `RagCodeChunkDraftVO` with:

```java
filePath, language, symbolName, symbolType, signature, startLine, endLine, code
```

- [ ] **Step 5: Add repo/code LLM node services**

Component codes:

```java
RAG_REPOSITORY_SUMMARY
RAG_CODE_CHUNK_ENRICHMENT
```

Node services mirror existing node services: build `NodeInvocationCommand`, call `NodeInvocationPipeline`, return typed output or safe fallback object with empty fields only when pipeline fails.

- [ ] **Step 6: Run tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RagCodeHeuristicSplitterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: test passes.

---

## Task 7: Implement GitHub Repository Ingestion

**Files:**
- Modify: `RagAssetIngestionService.java`
- Modify: `RagService.java`
- Modify: `RAGController.java` and DTO if branch/session/user fields are added.
- Tests: add service tests with a fake repository loader instead of cloning network repos.

- [ ] **Step 1: Introduce repository loading boundary**

Create domain port or service interface:

```java
public interface RagRepositorySourceLoader {
    RagRepositorySource load(RagGitIngestCommandEntity command) throws Exception;
}
```

`RagRepositorySource` contains:

```java
repoName, repoUrl, rootPath, files
```

Infrastructure can wrap current JGit behavior later; tests use in-memory fake source.

- [ ] **Step 2: Implement ingestion from loaded source**

Flow:

```text
save document INGESTING
scan accepted files
save file payloads and file metadata
split code symbols
call enrichment node per chunk
save chunks/symbols
call repository summary node
index repo document summary
index enriched code chunks
mark READY
```

- [ ] **Step 3: Preserve token security**

Ensure `RagGitIngestCommandEntity.token` is never stored in document metadata, payloads, or traces.

- [ ] **Step 4: Run targeted tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RagCodeHeuristicSplitterTest,RagServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: tests pass.

---

## Task 8: Add RAG Context Recall Preselector

**Files:**
- Create: `RagContextRecallPreselector.java`
- Modify: `ContextPreparationService.java`
- Modify: `DefaultRuntimeComponentPorts.java`
- Modify config properties.
- Test: `RagContextRecallPreselectorTest.java`, `ContextPreparationServiceTest.java`.

- [ ] **Step 1: Write recall tests**

Test cases:

- vector `RAG_CHUNK` hit resolves `chunkId` through `IRagAssetRepository`
- vector `RAG_DOCUMENT` hit resolves `documentId`
- missing/deleted rows are dropped
- keyword code symbol results become `RagCandidateVO`
- duplicate chunk candidates merge source reasons

- [ ] **Step 2: Implement `RagContextRecallPreselector`**

Constructor:

```java
IVectorMemoryRepository vectorMemoryRepository
IRagAssetRepository ragAssetRepository
```

Method:

```java
public List<RagCandidateVO> recall(ContextPreparationCommand command)
```

Use:

```java
VectorCollectionTypeEnumVO.RAG_DOCUMENT
VectorCollectionTypeEnumVO.RAG_CHUNK
```

Resolve vector source ids through MySQL before returning candidates.

- [ ] **Step 3: Extend `ContextPreparationService`**

Add optional `RagContextRecallPreselector`.

Run three futures:

```java
mysqlFuture
vectorFuture
ragFuture
```

RAG timeout failure returns `List.of()`.

Merge result into `ContextCandidateBundleVO.ragCandidates`.

- [ ] **Step 4: Update planning view**

In `DefaultRuntimeComponentPorts.planningView`, copy `ragCandidates` into the planning view.

- [ ] **Step 5: Run tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RagContextRecallPreselectorTest,ContextPreparationServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: tests pass.

---

## Task 9: Add Planner Rules And RAG Materialization

**Files:**
- Modify: `ContextPlannerPromptBuilder.java`
- Modify: `ContextSelectionValidator.java`
- Modify: `ContextMaterializer.java`
- Modify: `MainAgentStateViewBuilder.java`
- Test: `ContextPlannerContractTest.java`, `ContextMaterializationTest.java`.

- [ ] **Step 1: Update planner prompt with typed candidate rules**

Rewrite decision policy sections to explicitly cover:

- long-term memory
- conversation summaries
- RAG file candidates
- RAG code candidates

Include these source types:

```text
RAG_DOCUMENT
RAG_CHUNK
```

Include the rule that Planner sees code candidate cards, not raw source code.

- [ ] **Step 2: Validate RAG selections**

Extend `ContextSelectionValidator` to add valid ids from:

```java
candidates.getRagCandidates()
```

Add `candidateId`, `documentId`, and `chunkId` to the valid id set.

- [ ] **Step 3: Materialize selected RAG candidates**

In `ContextMaterializer`:

- Find selections with `sourceType` `RAG_DOCUMENT` or `RAG_CHUNK`.
- Match candidates by selected source id.
- For `SUMMARY_ONLY`, build `MaterializedRagVO` without loading payload.
- For `CHUNKED_CONTEXT` and `FULL_TEXT`, use `IRagAssetRepository.findChunk` and `IPayloadRepository.findPayload` to load selected content.
- Add resulting list to `MainAgentStateViewBuildCommand.ragPack`.

- [ ] **Step 4: Add tests**

Test:

- Planner prompt mentions RAG file and code rules.
- Validator accepts RAG chunk id.
- Materializer injects selected RAG chunk into `stateView.ragPack`.
- Unselected RAG candidate does not appear in `ragPack`.

- [ ] **Step 5: Run tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=ContextPlannerContractTest,ContextMaterializationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: tests pass.

---

## Task 10: Wire Spring Beans And API Contracts

**Files:**
- Modify: `AutoAgentRuntimeConfig.java`
- Modify: `AutoAgentRagProperties.java`
- Modify: `application-dev.yml`
- Modify: `IRagApi.java`
- Modify: `RAGController.java`
- Create: `RagAssetResponseDTO.java`

- [ ] **Step 1: Add properties**

Add nested properties:

```java
private Asset asset = new Asset();

public static class Asset {
    private boolean enabled = true;
    private Recall recall = new Recall();
    private File file = new File();
    private Git git = new Git();
}
```

Use concrete names:

- `maxFileBytes`
- `maxChunkChars`
- `minChunkChars`
- `documentTopK`
- `chunkTopK`
- `codeKeywordTopK`
- `minScore`
- `timeoutMillis`

- [ ] **Step 2: Register beans**

In `AutoAgentRuntimeConfig`, register:

- `RagVectorIndexingService`
- `RagParagraphChunker`
- `RagCodeFilePolicy`
- `RagCodeHeuristicSplitter`
- `RagContextRecallPreselector`
- `RagAssetIngestionService`
- new node services

Inject `RagContextRecallPreselector` into `ContextPreparationService`.

- [ ] **Step 3: Update upload endpoints**

Keep existing endpoint URLs:

- `POST /api/v1/rag/knowledge/files`
- `POST /api/v1/rag/knowledge/git`

Return document ids in response DTO instead of plain string when possible.

Add:

```text
GET /api/v1/rag/assets
DELETE /api/v1/rag/assets/{documentId}
```

- [ ] **Step 4: Run compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: compile passes.

---

## Task 11: Verification Sweep

**Files:**
- All touched files.

- [ ] **Step 1: Run targeted RAG and context tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RagParagraphChunkerTest,RagCodeHeuristicSplitterTest,RagVectorIndexingServiceTest,RagContextRecallPreselectorTest,ContextPreparationServiceTest,ContextMaterializationTest,ContextPlannerContractTest,RagServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all selected tests pass.

- [ ] **Step 2: Run compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: compile passes.

- [ ] **Step 3: Run diff hygiene**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors. Status shows only intended RAG/context/spec/plan changes and any pre-existing unrelated changes are left untouched.

- [ ] **Step 4: Update progress**

Append a concise summary to `progress.md`:

```markdown
## 2026-05-29 RAG Asset Module Implementation

- Implemented RAG asset schema, repository, ingestion, vector indexing, context recall, planner rules, and state view materialization.
- Verification:
  - targeted tests: PASS
  - compile: PASS
  - diff check: PASS
```

---

## Commit Strategy

Use focused commits when each task group passes:

```powershell
git add docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql docs/dev-ops/mysql/sql/auto-agent-rag-asset-migration.sql ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/rag ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/IRagAssetRepository.java
git commit -m "rag: add asset persistence contract"
```

Then commit service/context/API phases separately:

- `rag: add asset repository adapter`
- `rag: add file asset ingestion`
- `rag: add code repository ingestion`
- `rag: add context recall candidates`
- `rag: materialize selected asset context`

Do not commit unrelated dirty files.

## Plan Self-Review

- Spec coverage: covered data model, repository contracts, ingestion, LLM nodes, vector indexing, context recall, planner rules, materialization, API, config, tests, compatibility.
- Placeholder scan: this plan has no unresolved placeholder markers or unspecified implementation slots.
- Type consistency: RAG candidate, materialized pack, source types, vector collection names, and repository method names match the design spec.

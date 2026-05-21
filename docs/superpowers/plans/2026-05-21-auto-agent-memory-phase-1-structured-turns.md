# AutoAgent Memory Phase 1 Structured Turns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add structured conversation turns, asynchronous per-turn summaries, and deterministic short-term context injection for MainAgentNode.

**Architecture:** MySQL remains the source of truth. Final answer delivery persists an `agent_turn` record linking the user message and assistant message, then publishes a non-blocking turn-completed task that invokes `TURN_SUMMARY` and stores `agent_turn_summary`. Context preparation loads the latest 6 completed turns as full text and the previous 6 completed turns as summaries before ContextPlanner selects additional context.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module, MyBatis XML mappers, existing `NodeInvocationPipeline`, existing DDD module boundaries.

---

## Scope

This plan implements Phase 1 from `docs/superpowers/specs/2026-05-20-auto-agent-memory-lifecycle-design.md`.

Included:

- `agent_turn`
- `agent_turn_summary`
- lightweight `agent_memory_task` for async summary observability
- `TURN_SUMMARY` component contract and prompt
- asynchronous turn summary task
- latest 6 full turns + previous 6 summaries in `MainAgentStateView`

Excluded:

- vector collections
- long-term memory extraction
- memory merge
- rolling conversation summary
- Memory GC
- artifact chunk indexing
- RAG vector indexing

## File Structure

Schema and mappers:

- Modify `docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql`
  - Add `agent_turn`, `agent_turn_summary`, and `agent_memory_task`.
- Create `ai-agent-station-study-app/src/main/resources/mybatis/mapper/agent_turn_mapper.xml`
- Create `ai-agent-station-study-app/src/main/resources/mybatis/mapper/agent_turn_summary_mapper.xml`
- Create `ai-agent-station-study-app/src/main/resources/mybatis/mapper/agent_memory_task_mapper.xml`

Domain entities and repositories:

- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentTurnEntity.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentTurnSummaryEntity.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentMemoryTaskEntity.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/ITurnRepository.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/ITurnSummaryRepository.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/IMemoryTaskRepository.java`

Infrastructure:

- Create `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/IAgentTurnDao.java`
- Create `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/IAgentTurnSummaryDao.java`
- Create `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/IAgentMemoryTaskDao.java`
- Create `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/AgentTurnPO.java`
- Create `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/AgentTurnSummaryPO.java`
- Create `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/AgentMemoryTaskPO.java`
- Create `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/repository/TurnRepository.java`
- Create `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/repository/TurnSummaryRepository.java`
- Create `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/repository/MemoryTaskRepository.java`

Turn completion and summary:

- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/memory/TurnSummaryInputVO.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/memory/TurnSummaryOutputVO.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/TurnSummaryNodeService.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/TurnCompletionPublisher.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/NoopTurnCompletionPublisher.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/AsyncTurnSummaryProcessor.java`
- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/contract/AgentComponentCodeEnumVO.java`
- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/OutputContractPromptRenderer.java`
- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/PromptAssembler.java`
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/TurnSummaryPromptBuilder.java`
- Modify `docs/dev-ops/mysql/sql/auto-agent-model-runtime.sql`

Final delivery integration:

- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/finalresponse/FinalResponsePersistenceService.java`
- Modify `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRuntimeConfig.java`

Context injection:

- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/ContextCandidatePreselector.java`
- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/MainAgentStateViewBuilder.java` only if existing `ConversationViewVO` cannot carry the loaded summaries.

Tests:

- Create `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/memory/TurnSummaryNodeServiceTest.java`
- Create `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/memory/AsyncTurnSummaryProcessorTest.java`
- Create `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/finalresponse/FinalResponsePersistenceTurnTest.java`
- Modify `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/context/ContextCandidatePreselectorTest.java`
- Modify `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/invocation/PromptAssemblerTest.java`

---

### Task 1: Add Turn And Summary Persistence Model

**Files:**

- Modify: `docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentTurnEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentTurnSummaryEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentMemoryTaskEntity.java`

- [ ] **Step 1: Add schema tables**

In `docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql`, add these drops near existing memory and message drops:

```sql
DROP TABLE IF EXISTS `agent_memory_task`;
DROP TABLE IF EXISTS `agent_turn_summary`;
DROP TABLE IF EXISTS `agent_turn`;
```

Add tables after `agent_message` and before `agent_run`:

```sql
CREATE TABLE `agent_turn` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `turn_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `agent_id` varchar(64) DEFAULT NULL,
  `turn_no` bigint NOT NULL,
  `user_message_id` varchar(64) NOT NULL,
  `assistant_message_id` varchar(64) DEFAULT NULL,
  `user_payload_ref` varchar(64) DEFAULT NULL,
  `assistant_payload_ref` varchar(64) DEFAULT NULL,
  `status` varchar(64) NOT NULL DEFAULT 'COMPLETED',
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_turn_id` (`turn_id`),
  UNIQUE KEY `uk_agent_turn_run` (`run_id`),
  UNIQUE KEY `uk_agent_turn_session_no` (`session_id`, `turn_no`),
  KEY `idx_agent_turn_session_completed` (`session_id`, `completed_at`),
  KEY `idx_agent_turn_user_session` (`user_id`, `session_id`, `completed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent completed user-agent turn';

CREATE TABLE `agent_turn_summary` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `summary_id` varchar(64) NOT NULL,
  `turn_id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `run_id` varchar(64) NOT NULL,
  `user_id` varchar(64) DEFAULT NULL,
  `summary_ref` varchar(64) NOT NULL,
  `intent` varchar(512) DEFAULT NULL,
  `topics_json` json DEFAULT NULL,
  `entities_json` json DEFAULT NULL,
  `artifact_refs_json` json DEFAULT NULL,
  `importance_score` decimal(8,4) DEFAULT NULL,
  `requires_long_term_extraction` tinyint(1) NOT NULL DEFAULT 0,
  `status` varchar(64) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_turn_summary_id` (`summary_id`),
  UNIQUE KEY `uk_agent_turn_summary_turn` (`turn_id`),
  KEY `idx_agent_turn_summary_session` (`session_id`, `created_at`),
  KEY `idx_agent_turn_summary_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent per-turn summary';

CREATE TABLE `agent_memory_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `task_type` varchar(64) NOT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `run_id` varchar(64) DEFAULT NULL,
  `turn_id` varchar(64) DEFAULT NULL,
  `status` varchar(64) NOT NULL,
  `attempt_count` int NOT NULL DEFAULT 0,
  `failure_code` varchar(128) DEFAULT NULL,
  `failure_message` text,
  `input_ref` varchar(64) DEFAULT NULL,
  `output_ref` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_memory_task_id` (`task_id`),
  KEY `idx_agent_memory_task_status` (`status`, `created_at`),
  KEY `idx_agent_memory_task_turn` (`turn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AutoAgent async memory task';
```

- [ ] **Step 2: Add entity classes**

Create `AgentTurnEntity.java`:

```java
package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTurnEntity {
    private String turnId;
    private String sessionId;
    private String runId;
    private String userId;
    private String agentId;
    private Long turnNo;
    private String userMessageId;
    private String assistantMessageId;
    private String userPayloadRef;
    private String assistantPayloadRef;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

Create `AgentTurnSummaryEntity.java`:

```java
package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTurnSummaryEntity {
    private String summaryId;
    private String turnId;
    private String sessionId;
    private String runId;
    private String userId;
    private String summaryRef;
    private String intent;
    private String topicsJson;
    private String entitiesJson;
    private String artifactRefsJson;
    private BigDecimal importanceScore;
    private Boolean requiresLongTermExtraction;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

Create `AgentMemoryTaskEntity.java`:

```java
package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryTaskEntity {
    private String taskId;
    private String taskType;
    private String sessionId;
    private String runId;
    private String turnId;
    private String status;
    private Integer attemptCount;
    private String failureCode;
    private String failureMessage;
    private String inputRef;
    private String outputRef;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
```

- [ ] **Step 3: Run compile to expose missing repository pieces**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile may fail until repository interfaces are added in Task 2 if imports are already referenced; if Task 1 only creates isolated entity files, compile should pass.

- [ ] **Step 4: Commit Task 1**

```bash
git add docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentTurnEntity.java ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentTurnSummaryEntity.java ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentMemoryTaskEntity.java
git commit -m "agent: add structured turn persistence model"
```

---

### Task 2: Add Turn Repository Adapters

**Files:**

- Create domain repository interfaces listed in File Structure.
- Create infrastructure DAO interfaces, PO classes, repositories, and MyBatis mapper XML files.

- [ ] **Step 1: Add domain repository interfaces**

Create `ITurnRepository.java`:

```java
package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;

import java.util.List;
import java.util.Optional;

public interface ITurnRepository {
    String saveCompletedTurn(AgentTurnEntity turn);
    Optional<AgentTurnEntity> findByTurnId(String turnId);
    Optional<AgentTurnEntity> findByRunId(String runId);
    List<AgentTurnEntity> listRecentCompletedTurns(String sessionId, int limit);
    List<AgentTurnEntity> listCompletedTurnsBefore(String sessionId, Long beforeTurnNo, int limit);
}
```

Create `ITurnSummaryRepository.java`:

```java
package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;

import java.util.List;
import java.util.Optional;

public interface ITurnSummaryRepository {
    String saveSummary(AgentTurnSummaryEntity summary);
    Optional<AgentTurnSummaryEntity> findByTurnId(String turnId);
    List<AgentTurnSummaryEntity> listByTurnIds(List<String> turnIds);
}
```

Create `IMemoryTaskRepository.java`:

```java
package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;

public interface IMemoryTaskRepository {
    String createTask(AgentMemoryTaskEntity task);
    void markRunning(String taskId);
    void markSucceeded(String taskId, String outputRef);
    void markFailed(String taskId, String failureCode, String failureMessage);
}
```

- [ ] **Step 2: Add PO classes**

Create PO classes in `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/` mirroring the entity fields plus `Long id`. Use Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor`.

Example for `AgentTurnPO.java`:

```java
package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTurnPO {
    private Long id;
    private String turnId;
    private String sessionId;
    private String runId;
    private String userId;
    private String agentId;
    private Long turnNo;
    private String userMessageId;
    private String assistantMessageId;
    private String userPayloadRef;
    private String assistantPayloadRef;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Add DAO interfaces**

Create `IAgentTurnDao.java`:

```java
package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentTurnPO;

import java.util.List;

@Mapper
public interface IAgentTurnDao {
    int insert(AgentTurnPO turn);
    Long nextTurnNo(@Param("sessionId") String sessionId);
    AgentTurnPO queryByTurnId(@Param("turnId") String turnId);
    AgentTurnPO queryByRunId(@Param("runId") String runId);
    List<AgentTurnPO> listRecentCompleted(@Param("sessionId") String sessionId, @Param("limit") int limit);
    List<AgentTurnPO> listCompletedBefore(@Param("sessionId") String sessionId, @Param("beforeTurnNo") Long beforeTurnNo, @Param("limit") int limit);
}
```

Create `IAgentTurnSummaryDao.java` and `IAgentMemoryTaskDao.java` with methods matching their repository interfaces.

- [ ] **Step 4: Add MyBatis mapper XML**

Create `agent_turn_mapper.xml` with `nextTurnNo`:

```xml
<select id="nextTurnNo" resultType="java.lang.Long">
    SELECT COALESCE(MAX(turn_no), 0) + 1
    FROM agent_turn
    WHERE session_id = #{sessionId}
</select>
```

Create `listRecentCompleted` ordered descending in SQL and reverse in repository, matching `ConversationRepository` style:

```xml
<select id="listRecentCompleted" resultMap="AgentTurnMap">
    SELECT id, turn_id, session_id, run_id, user_id, agent_id, turn_no,
           user_message_id, assistant_message_id, user_payload_ref, assistant_payload_ref,
           status, started_at, completed_at, created_at, updated_at
    FROM agent_turn
    WHERE session_id = #{sessionId}
      AND status = 'COMPLETED'
    ORDER BY turn_no DESC
    LIMIT #{limit}
</select>
```

- [ ] **Step 5: Add repository implementations**

Create `TurnRepository.java`:

```java
package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.infrastructure.dao.IAgentTurnDao;
import yhx.com.infrastructure.dao.po.AgentTurnPO;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TurnRepository implements ITurnRepository {

    @Resource
    private IAgentTurnDao agentTurnDao;

    @Override
    public String saveCompletedTurn(AgentTurnEntity turn) {
        if (turn.getTurnId() == null || turn.getTurnId().isBlank()) {
            turn.setTurnId("turn-" + UUID.randomUUID());
        }
        if (turn.getTurnNo() == null) {
            turn.setTurnNo(agentTurnDao.nextTurnNo(turn.getSessionId()));
        }
        LocalDateTime now = LocalDateTime.now();
        if (turn.getStartedAt() == null) {
            turn.setStartedAt(now);
        }
        if (turn.getCompletedAt() == null) {
            turn.setCompletedAt(now);
        }
        if (turn.getCreatedAt() == null) {
            turn.setCreatedAt(now);
        }
        if (turn.getUpdatedAt() == null) {
            turn.setUpdatedAt(now);
        }
        if (turn.getStatus() == null || turn.getStatus().isBlank()) {
            turn.setStatus("COMPLETED");
        }
        agentTurnDao.insert(toPO(turn));
        return turn.getTurnId();
    }

    @Override
    public Optional<AgentTurnEntity> findByTurnId(String turnId) {
        return Optional.ofNullable(agentTurnDao.queryByTurnId(turnId)).map(this::toEntity);
    }

    @Override
    public Optional<AgentTurnEntity> findByRunId(String runId) {
        return Optional.ofNullable(agentTurnDao.queryByRunId(runId)).map(this::toEntity);
    }

    @Override
    public List<AgentTurnEntity> listRecentCompletedTurns(String sessionId, int limit) {
        List<AgentTurnPO> rows = agentTurnDao.listRecentCompleted(sessionId, limit);
        Collections.reverse(rows);
        return rows.stream().map(this::toEntity).toList();
    }

    @Override
    public List<AgentTurnEntity> listCompletedTurnsBefore(String sessionId, Long beforeTurnNo, int limit) {
        List<AgentTurnPO> rows = agentTurnDao.listCompletedBefore(sessionId, beforeTurnNo, limit);
        Collections.reverse(rows);
        return rows.stream().map(this::toEntity).toList();
    }

    private AgentTurnPO toPO(AgentTurnEntity entity) {
        return AgentTurnPO.builder()
                .turnId(entity.getTurnId())
                .sessionId(entity.getSessionId())
                .runId(entity.getRunId())
                .userId(entity.getUserId())
                .agentId(entity.getAgentId())
                .turnNo(entity.getTurnNo())
                .userMessageId(entity.getUserMessageId())
                .assistantMessageId(entity.getAssistantMessageId())
                .userPayloadRef(entity.getUserPayloadRef())
                .assistantPayloadRef(entity.getAssistantPayloadRef())
                .status(entity.getStatus())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentTurnEntity toEntity(AgentTurnPO po) {
        return AgentTurnEntity.builder()
                .turnId(po.getTurnId())
                .sessionId(po.getSessionId())
                .runId(po.getRunId())
                .userId(po.getUserId())
                .agentId(po.getAgentId())
                .turnNo(po.getTurnNo())
                .userMessageId(po.getUserMessageId())
                .assistantMessageId(po.getAssistantMessageId())
                .userPayloadRef(po.getUserPayloadRef())
                .assistantPayloadRef(po.getAssistantPayloadRef())
                .status(po.getStatus())
                .startedAt(po.getStartedAt())
                .completedAt(po.getCompletedAt())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 6: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: PASS.

- [ ] **Step 7: Commit Task 2**

```bash
git add ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/repository ai-agent-station-study-app/src/main/resources/mybatis/mapper
git commit -m "agent: add turn repository adapters"
```

---

### Task 3: Persist Completed Turns During Final Delivery

**Files:**

- Modify: `FinalResponsePersistenceService.java`
- Modify: `AutoAgentRuntimeConfig.java`
- Test: `FinalResponsePersistenceTurnTest.java`

- [ ] **Step 1: Write failing unit test**

Create `FinalResponsePersistenceTurnTest.java` using fake repositories. Test that `persistDelivered` saves an assistant message and one completed turn linking the known `userMessageId`.

Core assertion:

```java
Assert.assertEquals("msg-user-1", turn.userMessageId);
Assert.assertEquals(response.getMessageId(), turn.assistantMessageId);
Assert.assertEquals("payload-user-1", turn.userPayloadRef);
Assert.assertNotNull(turn.assistantPayloadRef);
Assert.assertEquals("COMPLETED", turn.status);
```

- [ ] **Step 2: Run failing test**

Run:

```bash
mvn -q -pl ai-agent-station-study-app -am '-Dtest=FinalResponsePersistenceTurnTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: FAIL because `FinalResponsePersistenceService` does not yet use `ITurnRepository`.

- [ ] **Step 3: Modify `FinalResponsePersistenceService` constructor**

Add dependencies:

```java
private final ITurnRepository turnRepository;
private final TurnCompletionPublisher turnCompletionPublisher;
```

Add overloaded constructor so existing tests can pass `null`:

```java
public FinalResponsePersistenceService(IPayloadRepository payloadRepository,
                                       IConversationRepository conversationRepository,
                                       IRunRepository runRepository,
                                       IEventTraceRepository eventTraceRepository,
                                       ITurnRepository turnRepository,
                                       TurnCompletionPublisher turnCompletionPublisher) {
    this.payloadRepository = payloadRepository;
    this.conversationRepository = conversationRepository;
    this.runRepository = runRepository;
    this.eventTraceRepository = eventTraceRepository;
    this.turnRepository = turnRepository;
    this.turnCompletionPublisher = turnCompletionPublisher == null ? new NoopTurnCompletionPublisher() : turnCompletionPublisher;
}
```

- [ ] **Step 4: Save turn after assistant message is appended**

Inside `persistDelivered`, after assistant message append and before run status completed:

```java
String turnId = null;
if (turnRepository != null && command.getUserMessageId() != null) {
    turnId = turnRepository.saveCompletedTurn(AgentTurnEntity.builder()
            .sessionId(command.getSessionId())
            .runId(command.getRunId())
            .userId(command.getUserId())
            .agentId(command.getAgentId())
            .userMessageId(command.getUserMessageId())
            .assistantMessageId(messageId)
            .userPayloadRef(findUserPayloadRef(command.getUserMessageId()))
            .assistantPayloadRef(contentRef)
            .status("COMPLETED")
            .completedAt(LocalDateTime.now())
            .build());
}
if (turnId != null) {
    turnCompletionPublisher.onTurnCompleted(turnId);
}
```

Add helper `findUserPayloadRef` by scanning recent visible messages for the user message id:

```java
private String findUserPayloadRef(String userMessageId) {
    if (conversationRepository == null || userMessageId == null) {
        return null;
    }
    return conversationRepository.listRecentVisibleMessages(null, 0).stream()
            .filter(message -> userMessageId.equals(message.getMessageId()))
            .map(AgentMessageEntity::getContentRef)
            .findFirst()
            .orElse(null);
}
```

Do not use the helper exactly as written if `IConversationRepository` cannot query globally. Prefer extending `IConversationRepository` with `findMessageById(String messageId)` and implement it in `ConversationRepository` and mapper. The final implementation must not call `listRecentVisibleMessages(null, 0)`.

- [ ] **Step 5: Add `findMessageById` to conversation repository**

Modify `IConversationRepository`:

```java
Optional<AgentMessageEntity> findMessageById(String messageId);
```

Modify `IAgentMessageDao`:

```java
AgentMessagePO queryByMessageId(@Param("messageId") String messageId);
```

Modify `agent_message_mapper.xml`:

```xml
<select id="queryByMessageId" parameterType="java.lang.String" resultMap="AgentMessageMap">
    SELECT id, message_id, session_id, run_id, role, content_ref, metadata_ref,
           visible_to_user, seq, created_at
    FROM agent_message
    WHERE message_id = #{messageId}
    LIMIT 1
</select>
```

Use it in `FinalResponsePersistenceService.findUserPayloadRef`.

- [ ] **Step 6: Wire beans**

In `AutoAgentRuntimeConfig`, construct `FinalResponsePersistenceService` with `ITurnRepository` and `TurnCompletionPublisher`.

- [ ] **Step 7: Run test**

```bash
mvn -q -pl ai-agent-station-study-app -am '-Dtest=FinalResponsePersistenceTurnTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: PASS.

- [ ] **Step 8: Compile**

```bash
mvn -q -DskipTests compile
```

Expected: PASS.

- [ ] **Step 9: Commit Task 3**

```bash
git add ai-agent-station-study-domain ai-agent-station-study-infrastructure ai-agent-station-study-app
git commit -m "agent: persist completed conversation turns"
```

---

### Task 4: Add TURN_SUMMARY Node Contract And Prompt

**Files:**

- Modify: `AgentComponentCodeEnumVO.java`
- Modify: `OutputContractPromptRenderer.java`
- Modify: `PromptAssembler.java`
- Create: `TurnSummaryPromptBuilder.java`
- Create: `TurnSummaryInputVO.java`
- Create: `TurnSummaryOutputVO.java`
- Create: `TurnSummaryNodeService.java`
- Modify: `docs/dev-ops/mysql/sql/auto-agent-model-runtime.sql`
- Test: `TurnSummaryNodeServiceTest.java`, `PromptAssemblerTest.java`

- [ ] **Step 1: Add component enum**

Add:

```java
TURN_SUMMARY,
```

to `AgentComponentCodeEnumVO`.

- [ ] **Step 2: Add input/output VOs**

Create `TurnSummaryInputVO`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnSummaryInputVO {
    private String runId;
    private String sessionId;
    private String turnId;
    private String userInput;
    private String finalAnswer;
    private List<String> evidenceIds;
    private List<String> artifactIds;
}
```

Create `TurnSummaryOutputVO`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnSummaryOutputVO {
    private String summary;
    private String intent;
    private List<String> topics;
    private List<Map<String, Object>> entities;
    private List<String> artifactRefs;
    private BigDecimal importanceScore;
    private Boolean requiresLongTermExtraction;
}
```

- [ ] **Step 3: Add prompt builder**

Create `TurnSummaryPromptBuilder`:

```java
public class TurnSummaryPromptBuilder {
    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You summarize one completed AutoAgent user-agent turn.
                        You do not answer the user and you do not create long-term memory directly.
                        Your output is used for future context recall and memory extraction.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Summarize the user's request and the final answer faithfully.
                        Extract topics, entities, artifact references, and whether this turn may contain durable memory.
                        Keep the summary concise but specific enough for future recall.
                        """),
                layer(PromptLayerTypeEnumVO.ANTI_EXAMPLES, "Anti Examples", """
                        Do not include hidden reasoning.
                        Do not invent facts that are not in the input turn.
                        Do not mark long-term extraction true for trivial greetings or one-off factual questions.
                        """)
        );
    }
}
```

- [ ] **Step 4: Add output contract**

In `OutputContractPromptRenderer.renderFor`, route `TURN_SUMMARY` to:

```java
private String renderTurnSummaryContract() {
    return """
            Required top-level fields:
            - summary: concise string
            - intent: concise string
            - topics: array of strings
            - entities: array of objects
            - artifactRefs: array of strings
            - importanceScore: number from 0.0 to 1.0
            - requiresLongTermExtraction: boolean

            Valid example:
            {"summary":"User asked for an RAG article and the agent drafted a structured explanation.","intent":"create article","topics":["RAG","article"],"entities":[],"artifactRefs":["artifact-1"],"importanceScore":0.7,"requiresLongTermExtraction":false}
            """;
}
```

- [ ] **Step 5: Add node service**

Create `TurnSummaryNodeService` calling `NodeInvocationPipeline` with `AgentComponentCodeEnumVO.TURN_SUMMARY.name()` and returning typed `TurnSummaryOutputVO`. Follow `ContextPlannerNodeService` style.

- [ ] **Step 6: Add DB seed prompt and model binding**

In `auto-agent-model-runtime.sql`, add model binding:

```sql
('amr-bind-turn-summary-001', 'TURN_SUMMARY', 'amr-model-main-001', 'v1', 'turn-summary-output-v1', 0.100, 1200, 1, 1)
```

Add prompt payload and `agent_node_prompt` row for `TURN_SUMMARY`.

- [ ] **Step 7: Add tests**

Add `PromptAssemblerTest` assertion:

```java
String prompt = assembler().assemble(command(AgentComponentCodeEnumVO.TURN_SUMMARY.name())).assembledPrompt();
Assert.assertTrue(prompt.contains("You summarize one completed AutoAgent user-agent turn"));
Assert.assertTrue(prompt.contains("requiresLongTermExtraction"));
```

Add `TurnSummaryNodeServiceTest` with fake `INodeClientPort` returning valid JSON and assert typed output fields.

- [ ] **Step 8: Run tests**

```bash
mvn -q -pl ai-agent-station-study-app -am '-Dtest=PromptAssemblerTest,TurnSummaryNodeServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: PASS.

- [ ] **Step 9: Commit Task 4**

```bash
git add ai-agent-station-study-domain ai-agent-station-study-app docs/dev-ops/mysql/sql/auto-agent-model-runtime.sql
git commit -m "agent: add turn summary node contract"
```

---

### Task 5: Add Async Turn Summary Processor

**Files:**

- Create: `TurnCompletionPublisher.java`
- Create: `NoopTurnCompletionPublisher.java`
- Create: `AsyncTurnSummaryProcessor.java`
- Modify: `AutoAgentRuntimeConfig.java`
- Test: `AsyncTurnSummaryProcessorTest.java`

- [ ] **Step 1: Add publisher interface**

```java
package yhx.com.domain.agent.service.memory;

public interface TurnCompletionPublisher {
    void onTurnCompleted(String turnId);
}
```

```java
package yhx.com.domain.agent.service.memory;

public class NoopTurnCompletionPublisher implements TurnCompletionPublisher {
    @Override
    public void onTurnCompleted(String turnId) {
        // no-op
    }
}
```

- [ ] **Step 2: Add async processor**

Create `AsyncTurnSummaryProcessor`:

```java
public class AsyncTurnSummaryProcessor implements TurnCompletionPublisher {

    private final Executor executor;
    private final ITurnRepository turnRepository;
    private final ITurnSummaryRepository summaryRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IPayloadRepository payloadRepository;
    private final TurnSummaryNodeService nodeService;

    @Override
    public void onTurnCompleted(String turnId) {
        if (turnId == null || turnId.isBlank()) {
            return;
        }
        String taskId = taskRepository.createTask(AgentMemoryTaskEntity.builder()
                .taskType("TURN_SUMMARY")
                .turnId(turnId)
                .status("PENDING")
                .attemptCount(0)
                .build());
        executor.execute(() -> runTask(taskId, turnId));
    }
}
```

`runTask` must:

1. mark task running
2. load turn
3. load user and assistant payload content
4. invoke `TurnSummaryNodeService`
5. save summary text as `PayloadTypeEnumVO.JSON` or `TEXT`
6. save `AgentTurnSummaryEntity`
7. mark task succeeded
8. catch exceptions and mark failed with truncated failure message

- [ ] **Step 3: Add test**

`AsyncTurnSummaryProcessorTest` should use in-memory fake repositories and direct executor `Runnable::run`.

Assert:

```java
Assert.assertEquals(1, summaries.size());
Assert.assertEquals("ACTIVE", summaries.get(0).getStatus());
Assert.assertEquals("SUCCEEDED", tasks.get(0).getStatus());
```

- [ ] **Step 4: Wire bean**

In `AutoAgentRuntimeConfig`, add:

```java
@Bean
public Executor memoryTaskExecutor() {
    return Executors.newFixedThreadPool(2);
}

@Bean
public TurnCompletionPublisher turnCompletionPublisher(Executor memoryTaskExecutor,
                                                       ITurnRepository turnRepository,
                                                       ITurnSummaryRepository turnSummaryRepository,
                                                       IMemoryTaskRepository memoryTaskRepository,
                                                       IPayloadRepository payloadRepository,
                                                       TurnSummaryNodeService turnSummaryNodeService) {
    return new AsyncTurnSummaryProcessor(memoryTaskExecutor,
            turnRepository,
            turnSummaryRepository,
            memoryTaskRepository,
            payloadRepository,
            turnSummaryNodeService);
}
```

- [ ] **Step 5: Run tests**

```bash
mvn -q -pl ai-agent-station-study-app -am '-Dtest=AsyncTurnSummaryProcessorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 5**

```bash
git add ai-agent-station-study-domain ai-agent-station-study-app
git commit -m "agent: summarize completed turns asynchronously"
```

---

### Task 6: Inject Latest 6 Full Turns And Previous 6 Summaries

**Files:**

- Modify: `ContextCandidatePreselector.java`
- Modify: `ContextCandidatePreselectorTest.java`

- [ ] **Step 1: Update `ContextCandidatePreselector` dependencies**

Add optional repositories:

```java
private final ITurnRepository turnRepository;
private final ITurnSummaryRepository turnSummaryRepository;
```

Keep existing constructors and add a new constructor with turn repositories so old tests compile.

- [ ] **Step 2: Build recent full-turn messages**

Add constants:

```java
private static final int DEFAULT_FULL_TURN_LIMIT = 6;
private static final int DEFAULT_SUMMARY_TURN_LIMIT = 6;
```

If `turnRepository` is available, replace recent message selection with:

```java
List<AgentTurnEntity> recentTurns = turnRepository.listRecentCompletedTurns(command.getSessionId(), DEFAULT_FULL_TURN_LIMIT);
List<MessageCandidateVO> messages = recentTurns.stream()
        .flatMap(turn -> Stream.of(
                toTurnMessageCandidate(turn.getUserMessageId(), "USER", turn.getUserPayloadRef(), turn.getCompletedAt()),
                toTurnMessageCandidate(turn.getAssistantMessageId(), "ASSISTANT", turn.getAssistantPayloadRef(), turn.getCompletedAt())))
        .filter(Objects::nonNull)
        .filter(message -> command.getUserMessageId() == null || !command.getUserMessageId().equals(message.getMessageId()))
        .toList();
```

Use existing payload loading and `compactVisibleMessage`.

- [ ] **Step 3: Build previous turn summaries**

Find the oldest loaded `turnNo`, then load previous 6 completed turns and map their summaries:

```java
Long beforeTurnNo = recentTurns.stream()
        .map(AgentTurnEntity::getTurnNo)
        .min(Long::compareTo)
        .orElse(null);
List<AgentTurnEntity> previousTurns = beforeTurnNo == null
        ? List.of()
        : turnRepository.listCompletedTurnsBefore(command.getSessionId(), beforeTurnNo, DEFAULT_SUMMARY_TURN_LIMIT);
List<String> previousTurnIds = previousTurns.stream().map(AgentTurnEntity::getTurnId).toList();
List<SummaryCandidateVO> summaries = turnSummaryRepository.listByTurnIds(previousTurnIds).stream()
        .map(this::toSummaryCandidate)
        .toList();
```

If turn repositories are unavailable, keep current fallback behavior: recent visible messages and empty summaries.

- [ ] **Step 4: Update test fixture**

Extend `FakeContextRepositories` or create a new fake repository that implements `ITurnRepository` and `ITurnSummaryRepository`.

Test:

```java
Assert.assertEquals(12, bundle.getRecentMessages().size());
Assert.assertEquals(6, bundle.getSessionSummaries().size());
```

Also assert the current user message is excluded.

- [ ] **Step 5: Run context tests**

```bash
mvn -q -pl ai-agent-station-study-app -am '-Dtest=ContextCandidatePreselectorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 6**

```bash
git add ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/ContextCandidatePreselector.java ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/context
git commit -m "agent: inject fixed turn context window"
```

---

### Task 7: Final Verification

**Files:**

- All touched files.

- [ ] **Step 1: Run targeted tests**

```bash
mvn -q -pl ai-agent-station-study-app -am '-Dtest=ContextCandidatePreselectorTest,PromptAssemblerTest,TurnSummaryNodeServiceTest,AsyncTurnSummaryProcessorTest,FinalResponsePersistenceTurnTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: PASS.

- [ ] **Step 2: Compile all modules**

```bash
mvn -q -DskipTests compile
```

Expected: PASS.

- [ ] **Step 3: Check whitespace**

```bash
git diff --check
```

Expected: no errors. CRLF warnings are acceptable on Windows.

- [ ] **Step 4: Verify git state**

```bash
git status --short
```

Expected: only unrelated pre-existing untracked files may remain, such as `AGENTS.md`, `design-md/`, or `docs/auto-agent-json-safety-explained.md`.

- [ ] **Step 5: Commit final verification note if any docs changed**

Only commit if verification required a documentation correction.

```bash
git add docs/superpowers/plans/2026-05-21-auto-agent-memory-phase-1-structured-turns.md
git commit -m "docs: plan memory phase one implementation"
```

---

## Self-Review

Spec coverage for Phase 1:

- Structured completed turns: Task 1, Task 2, Task 3.
- Per-turn summaries: Task 1, Task 4, Task 5.
- Async memory task observability: Task 1, Task 5.
- Latest 6 full turns and previous 6 summaries: Task 6.
- MainAgent receives deterministic short-term context: Task 6.
- Failed summary task does not break chat: Task 5.

Known gaps intentionally deferred:

- Vector indexes are Phase 2.
- Reference resolution and `resolvedReferences` are Phase 3.
- Long-term memory extraction and merge are Phase 4.
- Rolling summary and GC are Phase 5.
- Artifact chunk and RAG chunk indexes are Phase 6.

The plan defines the intended interfaces and behavior for Phase 1. If existing test helper names differ, rename only the helpers while preserving the specified repository contracts and runtime behavior.

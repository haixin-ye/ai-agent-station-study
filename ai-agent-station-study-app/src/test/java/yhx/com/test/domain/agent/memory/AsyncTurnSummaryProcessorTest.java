package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryOutputVO;
import yhx.com.domain.agent.service.memory.AsyncTurnSummaryProcessor;
import yhx.com.domain.agent.service.node.turnsummary.TurnSummaryNodeService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AsyncTurnSummaryProcessorTest {

    @Test
    public void completed_turn_is_summarized_and_task_succeeds_without_blocking_chat_path() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-1", AgentTurnEntity.builder()
                .turnId("turn-1")
                .runId("run-1")
                .sessionId("sess-1")
                .userId("user-1")
                .userPayloadRef("payload-user")
                .assistantPayloadRef("payload-assistant")
                .status("COMPLETED")
                .build());
        repositories.payloads.put("payload-user", AgentPayloadEntity.builder().payloadId("payload-user").content("user asks").build());
        repositories.payloads.put("payload-assistant", AgentPayloadEntity.builder().payloadId("payload-assistant").content("agent answers").build());
        TurnSummaryNodeService nodeService = new StubTurnSummaryNodeService();
        AsyncTurnSummaryProcessor processor = new AsyncTurnSummaryProcessor(Runnable::run,
                repositories,
                repositories,
                repositories,
                repositories,
                nodeService);

        processor.onTurnCompleted("turn-1");

        Assert.assertEquals(1, repositories.summaries.size());
        Assert.assertEquals("ACTIVE", repositories.summaries.get(0).getStatus());
        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
    }

    private static class StubTurnSummaryNodeService extends TurnSummaryNodeService {
        StubTurnSummaryNodeService() {
            super(null);
        }

        @Override
        public TurnSummaryOutputVO summarize(yhx.com.domain.agent.model.valobj.memory.TurnSummaryInputVO input,
                                             String agentId,
                                             yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO profile) {
            return TurnSummaryOutputVO.builder()
                    .summary("User asked and agent answered.")
                    .intent("answer question")
                    .topics(List.of("question"))
                    .entities(List.of())
                    .artifactRefs(List.of())
                    .importanceScore(new BigDecimal("0.50"))
                    .requiresLongTermExtraction(false)
                    .build();
        }
    }

    private static class FakeRepositories implements ITurnRepository, ITurnSummaryRepository, IMemoryTaskRepository, IPayloadRepository {
        private final Map<String, AgentTurnEntity> turns = new LinkedHashMap<>();
        private final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        private final List<AgentTurnSummaryEntity> summaries = new ArrayList<>();
        private final List<AgentMemoryTaskEntity> tasks = new ArrayList<>();

        @Override
        public String saveCompletedTurn(AgentTurnEntity turn) {
            turns.put(turn.getTurnId(), turn);
            return turn.getTurnId();
        }

        @Override
        public Optional<AgentTurnEntity> findByTurnId(String turnId) {
            return Optional.ofNullable(turns.get(turnId));
        }

        @Override
        public Optional<AgentTurnEntity> findByRunId(String runId) {
            return turns.values().stream().filter(turn -> runId.equals(turn.getRunId())).findFirst();
        }

        @Override
        public List<AgentTurnEntity> listRecentCompletedTurns(String sessionId, int limit) {
            return turns.values().stream().limit(limit).toList();
        }

        @Override
        public List<AgentTurnEntity> listCompletedTurnsBefore(String sessionId, Long beforeTurnNo, int limit) {
            return List.of();
        }

        @Override
        public String saveSummary(AgentTurnSummaryEntity summary) {
            summaries.add(summary);
            return summary.getSummaryId();
        }

        @Override
        public Optional<AgentTurnSummaryEntity> findSummaryById(String summaryId) {
            return summaries.stream().filter(summary -> summaryId.equals(summary.getSummaryId())).findFirst();
        }

        @Override
        public Optional<AgentTurnSummaryEntity> findSummaryByTurnId(String turnId) {
            return summaries.stream().filter(summary -> turnId.equals(summary.getTurnId())).findFirst();
        }

        @Override
        public List<AgentTurnSummaryEntity> listByTurnIds(List<String> turnIds) {
            return summaries.stream().filter(summary -> turnIds.contains(summary.getTurnId())).toList();
        }

        @Override
        public List<AgentTurnSummaryEntity> listRecentActiveSummaries(String sessionId, int limit) {
            return summaries.stream()
                    .filter(summary -> sessionId == null || sessionId.equals(summary.getSessionId()))
                    .filter(summary -> "ACTIVE".equals(summary.getStatus()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public String createTask(AgentMemoryTaskEntity task) {
            task.setTaskId("task-1");
            tasks.add(task);
            return task.getTaskId();
        }

        @Override
        public void markRunning(String taskId) {
            tasks.get(0).setStatus("RUNNING");
        }

        @Override
        public void markSucceeded(String taskId, String outputRef) {
            tasks.get(0).setStatus("SUCCEEDED");
            tasks.get(0).setOutputRef(outputRef);
        }

        @Override
        public void markFailed(String taskId, String failureCode, String failureMessage) {
            tasks.get(0).setStatus("FAILED");
            tasks.get(0).setFailureCode(failureCode);
            tasks.get(0).setFailureMessage(failureMessage);
        }

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            if (payload.getPayloadId() == null) {
                payload.setPayloadId("payload-" + (payloads.size() + 1));
            }
            payloads.put(payload.getPayloadId(), payload);
            return payload.getPayloadId();
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.ofNullable(payloads.get(payloadId));
        }
    }
}

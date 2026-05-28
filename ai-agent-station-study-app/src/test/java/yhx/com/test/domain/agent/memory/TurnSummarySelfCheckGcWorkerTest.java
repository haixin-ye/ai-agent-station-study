package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.service.memory.gc.MemoryGcFollowupScheduler;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskDispatcher;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGcTaskWorker;
import yhx.com.domain.agent.service.memory.gc.worker.TurnSummarySelfCheckGcWorker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TurnSummarySelfCheckGcWorkerTest {

    @Test
    public void self_check_dispatches_turn_summary_only_for_completed_turns_missing_summary() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-1", turn("turn-1"));
        repositories.turns.put("turn-2", turn("turn-2"));
        repositories.turns.put("turn-3", turn("turn-3"));
        repositories.summaries.add(AgentTurnSummaryEntity.builder()
                .summaryId("turn-summary-2")
                .turnId("turn-2")
                .status("ACTIVE")
                .build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY_SELF_CHECK.name())
                .turnId("turn-3")
                .status("PENDING")
                .build());
        RecordingWorker turnSummaryWorker = new RecordingWorker(MemoryTaskTypeEnumVO.TURN_SUMMARY.name());
        TurnSummarySelfCheckGcWorker worker = new TurnSummarySelfCheckGcWorker(repositories,
                repositories,
                repositories,
                new MemoryGcFollowupScheduler(repositories,
                        new MemoryGcTaskDispatcher(Runnable::run, List.of(turnSummaryWorker))),
                50);

        worker.handle("task-1");

        Assert.assertEquals("SUCCEEDED", repositories.tasks.get(0).getStatus());
        Assert.assertEquals(3, repositories.tasks.size());
        Assert.assertEquals(List.of("turn-1", "turn-3"), repositories.tasks.stream()
                .filter(task -> MemoryTaskTypeEnumVO.TURN_SUMMARY.name().equals(task.getTaskType()))
                .map(AgentMemoryTaskEntity::getTurnId)
                .toList());
        Assert.assertEquals(List.of("task-2", "task-3"), turnSummaryWorker.handledTaskIds);
    }

    @Test
    public void self_check_does_not_create_duplicate_turn_summary_task_for_same_turn() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-1", turn("turn-1"));
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY_SELF_CHECK.name())
                .turnId("turn-1")
                .status("PENDING")
                .build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-open-summary")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-1")
                .status("PENDING")
                .build());
        RecordingWorker turnSummaryWorker = new RecordingWorker(MemoryTaskTypeEnumVO.TURN_SUMMARY.name());
        TurnSummarySelfCheckGcWorker worker = new TurnSummarySelfCheckGcWorker(repositories,
                repositories,
                repositories,
                new MemoryGcFollowupScheduler(repositories,
                        new MemoryGcTaskDispatcher(Runnable::run, List.of(turnSummaryWorker))),
                50);

        worker.handle("task-1");

        Assert.assertEquals(2, repositories.tasks.size());
        Assert.assertTrue(turnSummaryWorker.handledTaskIds.isEmpty());
    }

    @Test
    public void self_check_creates_new_turn_summary_task_when_previous_task_failed_and_summary_is_missing() {
        FakeRepositories repositories = new FakeRepositories();
        repositories.turns.put("turn-1", turn("turn-1"));
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY_SELF_CHECK.name())
                .turnId("turn-1")
                .status("PENDING")
                .build());
        repositories.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-failed-summary")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .turnId("turn-1")
                .status("FAILED")
                .failureCode("TURN_SUMMARY_FAILED")
                .build());
        RecordingWorker turnSummaryWorker = new RecordingWorker(MemoryTaskTypeEnumVO.TURN_SUMMARY.name());
        TurnSummarySelfCheckGcWorker worker = new TurnSummarySelfCheckGcWorker(repositories,
                repositories,
                repositories,
                new MemoryGcFollowupScheduler(repositories,
                        new MemoryGcTaskDispatcher(Runnable::run, List.of(turnSummaryWorker))),
                50);

        worker.handle("task-1");

        Assert.assertEquals(3, repositories.tasks.size());
        Assert.assertEquals("turn-1", repositories.tasks.get(2).getTurnId());
        Assert.assertEquals(MemoryTaskTypeEnumVO.TURN_SUMMARY.name(), repositories.tasks.get(2).getTaskType());
        Assert.assertEquals(List.of("task-3"), turnSummaryWorker.handledTaskIds);
    }

    private AgentTurnEntity turn(String turnId) {
        return AgentTurnEntity.builder()
                .turnId(turnId)
                .runId("run-" + turnId)
                .sessionId("session-1")
                .userId("user-1")
                .status("COMPLETED")
                .build();
    }

    private static class RecordingWorker implements MemoryGcTaskWorker {
        private final String taskType;
        private final List<String> handledTaskIds = new ArrayList<>();

        private RecordingWorker(String taskType) {
            this.taskType = taskType;
        }

        @Override
        public String taskType() {
            return taskType;
        }

        @Override
        public void handle(String taskId) {
            handledTaskIds.add(taskId);
        }
    }

    private static class FakeRepositories implements ITurnRepository, ITurnSummaryRepository, IMemoryTaskRepository {
        private final Map<String, AgentTurnEntity> turns = new LinkedHashMap<>();
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
        public List<AgentTurnEntity> listRecentCompletedTurns(int limit) {
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
            return summaries.stream().limit(limit).toList();
        }

        @Override
        public void markSummariesRolledUp(List<String> summaryIds) {
        }

        @Override
        public String createTask(AgentMemoryTaskEntity task) {
            if (task.getTaskId() == null) {
                task.setTaskId("task-" + (tasks.size() + 1));
            }
            tasks.add(task);
            return task.getTaskId();
        }

        @Override
        public Optional<AgentMemoryTaskEntity> findByTaskId(String taskId) {
            return tasks.stream().filter(task -> taskId.equals(task.getTaskId())).findFirst();
        }

        @Override
        public boolean hasOpenTask(String taskType, String sessionId) {
            return false;
        }

        @Override
        public boolean hasOpenTaskType(String taskType) {
            return tasks.stream()
                    .anyMatch(task -> taskType.equals(task.getTaskType())
                            && ("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())));
        }

        @Override
        public boolean hasTaskForTurn(String taskType, String turnId) {
            return tasks.stream().anyMatch(task -> taskType.equals(task.getTaskType()) && turnId.equals(task.getTurnId()));
        }

        @Override
        public boolean hasOpenTaskForTurn(String taskType, String turnId) {
            return tasks.stream()
                    .anyMatch(task -> taskType.equals(task.getTaskType())
                            && turnId.equals(task.getTurnId())
                            && ("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())));
        }

        @Override
        public List<AgentMemoryTaskEntity> listRetryableFailedTasks(int maxAttempts, int limit) {
            return List.of();
        }

        @Override
        public List<AgentMemoryTaskEntity> listTasks(String status, int limit) {
            return tasks.stream()
                    .filter(task -> status == null || status.isBlank() || status.equals(task.getStatus()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void markRunning(String taskId) {
            findByTaskId(taskId).ifPresent(task -> task.setStatus("RUNNING"));
        }

        @Override
        public void markSucceeded(String taskId, String outputRef) {
            findByTaskId(taskId).ifPresent(task -> {
                task.setStatus("SUCCEEDED");
                task.setOutputRef(outputRef);
            });
        }

        @Override
        public void markFailed(String taskId, String failureCode, String failureMessage) {
            findByTaskId(taskId).ifPresent(task -> {
                task.setStatus("FAILED");
                task.setFailureCode(failureCode);
                task.setFailureMessage(failureMessage);
            });
        }
    }
}

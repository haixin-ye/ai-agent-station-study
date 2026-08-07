package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.service.runtime.RunOrphanRecoveryService;
import yhx.com.domain.agent.service.runtime.UnexpectedRuntimeFailureClassifier;

import java.time.LocalDateTime;
import java.util.Optional;

public class RunOrphanRecoveryServiceTest {

    @Test
    public void previous_process_running_run_is_recovered_as_terminal_failure() {
        LocalDateTime processStartedAt = LocalDateTime.of(2026, 8, 7, 10, 0);
        MemoryRunRepository repository = new MemoryRunRepository(run(RunStatusEnumVO.RUNNING,
                processStartedAt.minusMinutes(2)));

        AgentRunEntity recovered = new RunOrphanRecoveryService(repository, processStartedAt)
                .recoverIfOrphaned("run-stale")
                .orElseThrow();

        Assert.assertEquals(RunStatusEnumVO.FAILED, recovered.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.FAILED, recovered.getPhase());
        Assert.assertEquals(RuntimeFailureCodeEnumVO.BACKEND_PROCESS_TERMINATED.code(), recovered.getFailureCode());
    }

    @Test
    public void durable_wait_state_and_current_process_run_are_not_recovered() {
        LocalDateTime processStartedAt = LocalDateTime.of(2026, 8, 7, 10, 0);
        MemoryRunRepository waitingRepository = new MemoryRunRepository(run(RunStatusEnumVO.WAITING_USER,
                processStartedAt.minusMinutes(2)));
        MemoryRunRepository currentRepository = new MemoryRunRepository(run(RunStatusEnumVO.RUNNING,
                processStartedAt.plusSeconds(1)));

        new RunOrphanRecoveryService(waitingRepository, processStartedAt).recoverIfOrphaned("run-waiting");
        new RunOrphanRecoveryService(currentRepository, processStartedAt).recoverIfOrphaned("run-current");

        Assert.assertEquals(RunStatusEnumVO.WAITING_USER, waitingRepository.run.getStatus());
        Assert.assertEquals(RunStatusEnumVO.RUNNING, currentRepository.run.getStatus());
    }

    @Test
    public void out_of_memory_has_a_specific_debug_failure_code() {
        UnexpectedRuntimeFailureClassifier classifier = new UnexpectedRuntimeFailureClassifier();

        Assert.assertEquals(RuntimeFailureCodeEnumVO.BACKEND_OUT_OF_MEMORY,
                classifier.classify(new OutOfMemoryError("Java heap space")));
        Assert.assertEquals(RuntimeFailureCodeEnumVO.UNEXPECTED_RUNTIME_ERROR,
                classifier.classify(new IllegalStateException("boom")));
    }

    private AgentRunEntity run(RunStatusEnumVO status, LocalDateTime updatedAt) {
        return AgentRunEntity.builder()
                .runId("run-stale")
                .sessionId("sess-1")
                .status(status)
                .phase(RuntimePhaseEnumVO.CALLING_MAIN_NODE)
                .createdAt(updatedAt.minusMinutes(1))
                .updatedAt(updatedAt)
                .build();
    }

    private static class MemoryRunRepository implements IRunRepository {
        private final AgentRunEntity run;

        private MemoryRunRepository(AgentRunEntity run) {
            this.run = run;
        }

        @Override
        public String createRun(AgentRunEntity run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRunPhase(String runId, RuntimePhaseEnumVO phase) {
            run.setPhase(phase);
        }

        @Override
        public void updateRunStatus(String runId, RunStatusEnumVO status, String failureCode) {
            run.setStatus(status);
            run.setFailureCode(failureCode);
        }

        @Override
        public void updateFinalAnswerRef(String runId, String finalAnswerRef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markRagWasUsed(String runId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AgentRunEntity> findRun(String runId) {
            return Optional.of(run);
        }
    }
}

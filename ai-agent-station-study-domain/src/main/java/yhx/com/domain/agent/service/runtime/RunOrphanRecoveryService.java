package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reconciles an actively executing run left behind by a previous JVM process.
 * Durable pause states are intentionally excluded because they can be resumed
 * after a restart.
 */
public class RunOrphanRecoveryService {

    private final IRunRepository runRepository;
    private final LocalDateTime processStartedAt;

    public RunOrphanRecoveryService(IRunRepository runRepository, LocalDateTime processStartedAt) {
        this.runRepository = runRepository;
        this.processStartedAt = processStartedAt;
    }

    public Optional<AgentRunEntity> recoverIfOrphaned(String runId) {
        Optional<AgentRunEntity> existing = runRepository.findRun(runId);
        if (existing.isEmpty() || !isOrphaned(existing.get())) {
            return existing;
        }
        runRepository.updateRunStatus(runId, RunStatusEnumVO.FAILED,
                RuntimeFailureCodeEnumVO.BACKEND_PROCESS_TERMINATED.code());
        runRepository.updateRunPhase(runId, RuntimePhaseEnumVO.FAILED);
        return runRepository.findRun(runId);
    }

    private boolean isOrphaned(AgentRunEntity run) {
        if (run == null || (run.getStatus() != RunStatusEnumVO.RUNNING
                && run.getStatus() != RunStatusEnumVO.CREATED)) {
            return false;
        }
        LocalDateTime lastTouchedAt = run.getUpdatedAt() == null ? run.getCreatedAt() : run.getUpdatedAt();
        return lastTouchedAt != null && !lastTouchedAt.isAfter(processStartedAt);
    }
}

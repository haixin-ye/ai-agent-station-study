package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.infrastructure.dao.IAgentRunDao;
import yhx.com.infrastructure.dao.po.AgentRunPO;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RunRepository implements IRunRepository {

    @Resource
    private IAgentRunDao agentRunDao;

    @Override
    public String createRun(AgentRunEntity run) {
        if (run.getRunId() == null || run.getRunId().isBlank()) {
            run.setRunId("run-" + UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        if (run.getCreatedAt() == null) {
            run.setCreatedAt(now);
        }
        if (run.getUpdatedAt() == null) {
            run.setUpdatedAt(now);
        }
        agentRunDao.insert(toPO(run));
        return run.getRunId();
    }

    @Override
    public void updateRunPhase(String runId, RuntimePhaseEnumVO phase) {
        agentRunDao.updatePhase(runId, phase.code());
    }

    @Override
    public void updateRunStatus(String runId, RunStatusEnumVO status, String failureCode) {
        agentRunDao.updateStatus(runId, status.code(), failureCode);
    }

    @Override
    public void updateFinalAnswerRef(String runId, String finalAnswerRef) {
        agentRunDao.updateFinalAnswerRef(runId, finalAnswerRef);
    }

    @Override
    public void markRagWasUsed(String runId) {
        agentRunDao.markRagWasUsed(runId);
    }

    @Override
    public Optional<AgentRunEntity> findRun(String runId) {
        return Optional.ofNullable(agentRunDao.queryByRunId(runId)).map(this::toEntity);
    }

    private AgentRunPO toPO(AgentRunEntity entity) {
        return AgentRunPO.builder()
                .runId(entity.getRunId())
                .sessionId(entity.getSessionId())
                .userId(entity.getUserId())
                .agentId(entity.getAgentId())
                .status(entity.getStatus().code())
                .phase(entity.getPhase().code())
                .ragWasUsed(Boolean.TRUE.equals(entity.getRagWasUsed()) ? 1 : 0)
                .finalAnswerRef(entity.getFinalAnswerRef())
                .failureCode(entity.getFailureCode())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentRunEntity toEntity(AgentRunPO po) {
        return AgentRunEntity.builder()
                .runId(po.getRunId())
                .sessionId(po.getSessionId())
                .userId(po.getUserId())
                .agentId(po.getAgentId())
                .status(RunStatusEnumVO.ofCode(po.getStatus()).orElse(RunStatusEnumVO.CREATED))
                .phase(RuntimePhaseEnumVO.ofCode(po.getPhase()).orElse(RuntimePhaseEnumVO.CREATED))
                .ragWasUsed(po.getRagWasUsed() != null && po.getRagWasUsed() == 1)
                .finalAnswerRef(po.getFinalAnswerRef())
                .failureCode(po.getFailureCode())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}

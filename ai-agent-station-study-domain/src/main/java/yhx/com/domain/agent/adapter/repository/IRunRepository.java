package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

import java.util.Optional;

public interface IRunRepository {

    String createRun(AgentRunEntity run);

    void updateRunPhase(String runId, RuntimePhaseEnumVO phase);

    void updateRunStatus(String runId, RunStatusEnumVO status, String failureCode);

    void updateFinalAnswerRef(String runId, String finalAnswerRef);

    Optional<AgentRunEntity> findRun(String runId);
}

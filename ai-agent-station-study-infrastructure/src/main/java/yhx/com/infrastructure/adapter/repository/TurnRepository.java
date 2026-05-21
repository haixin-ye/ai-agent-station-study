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
        if (turn.getStatus() == null || turn.getStatus().isBlank()) {
            turn.setStatus("COMPLETED");
        }
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

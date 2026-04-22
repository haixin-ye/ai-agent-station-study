package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.agent.adapter.repository.ISessionMemoryRepository;
import cn.bugstack.ai.domain.agent.model.entity.SessionMemoryEntity;
import cn.bugstack.ai.infrastructure.dao.IAgentSessionMemoryDao;
import cn.bugstack.ai.infrastructure.dao.po.AgentSessionMemory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SessionMemoryRepository implements ISessionMemoryRepository {

    @Resource
    private IAgentSessionMemoryDao agentSessionMemoryDao;

    @Override
    public List<SessionMemoryEntity> queryLatestBySessionId(String sessionId, int limit) {
        List<AgentSessionMemory> records = agentSessionMemoryDao.queryLatestBySessionId(sessionId, limit);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public Integer queryMaxRoundNo(String sessionId) {
        return agentSessionMemoryDao.queryMaxRoundNo(sessionId);
    }

    @Override
    public void save(SessionMemoryEntity sessionMemoryEntity) {
        agentSessionMemoryDao.insert(toPo(sessionMemoryEntity));
    }

    private SessionMemoryEntity toEntity(AgentSessionMemory po) {
        return SessionMemoryEntity.builder()
                .id(po.getId())
                .sessionId(po.getSessionId())
                .roundNo(po.getRoundNo())
                .userMessage(po.getUserMessage())
                .finalAnswer(po.getFinalAnswer())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private AgentSessionMemory toPo(SessionMemoryEntity entity) {
        return AgentSessionMemory.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .roundNo(entity.getRoundNo())
                .userMessage(entity.getUserMessage())
                .finalAnswer(entity.getFinalAnswer())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}

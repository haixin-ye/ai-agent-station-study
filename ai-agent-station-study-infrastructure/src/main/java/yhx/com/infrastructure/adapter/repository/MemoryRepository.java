package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.infrastructure.dao.IAgentConversationSummaryDao;
import yhx.com.infrastructure.dao.IAgentLongTermMemoryDao;
import yhx.com.infrastructure.dao.IAgentMemoryEventDao;
import yhx.com.infrastructure.dao.po.AgentConversationSummaryPO;
import yhx.com.infrastructure.dao.po.AgentLongTermMemoryPO;
import yhx.com.infrastructure.dao.po.AgentMemoryEventPO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class MemoryRepository implements IMemoryRepository {

    @Resource
    private IAgentConversationSummaryDao agentConversationSummaryDao;

    @Resource
    private IAgentLongTermMemoryDao agentLongTermMemoryDao;

    @Resource
    private IAgentMemoryEventDao agentMemoryEventDao;

    @Override
    public List<AgentMemoryEntity> findMemoryCandidates(String userId, String sessionId, String query, int limit) {
        return agentLongTermMemoryDao.listCandidates(userId, sessionId, limit).stream().map(this::toEntity).toList();
    }

    @Override
    public String saveConversationSummary(AgentConversationSummaryEntity summary) {
        if (summary.getSummaryId() == null || summary.getSummaryId().isBlank()) {
            summary.setSummaryId("summary-" + UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        if (summary.getCreatedAt() == null) {
            summary.setCreatedAt(now);
        }
        if (summary.getUpdatedAt() == null) {
            summary.setUpdatedAt(now);
        }
        agentConversationSummaryDao.insert(toPO(summary));
        return summary.getSummaryId();
    }

    @Override
    public String saveLongTermMemory(AgentMemoryEntity memory) {
        if (memory.getMemoryId() == null || memory.getMemoryId().isBlank()) {
            memory.setMemoryId("memory-" + UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        if (memory.getCreatedAt() == null) {
            memory.setCreatedAt(now);
        }
        if (memory.getUpdatedAt() == null) {
            memory.setUpdatedAt(now);
        }
        agentLongTermMemoryDao.insert(toPO(memory));
        return memory.getMemoryId();
    }

    @Override
    public String recordMemoryEvent(AgentMemoryEventEntity event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId("memory-event-" + UUID.randomUUID());
        }
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(LocalDateTime.now());
        }
        agentMemoryEventDao.insert(toPO(event));
        return event.getEventId();
    }

    private AgentConversationSummaryPO toPO(AgentConversationSummaryEntity entity) {
        return AgentConversationSummaryPO.builder()
                .summaryId(entity.getSummaryId())
                .sessionId(entity.getSessionId())
                .userId(entity.getUserId())
                .summaryRef(entity.getSummaryRef())
                .messageStartSeq(entity.getMessageStartSeq())
                .messageEndSeq(entity.getMessageEndSeq())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentLongTermMemoryPO toPO(AgentMemoryEntity entity) {
        return AgentLongTermMemoryPO.builder()
                .memoryId(entity.getMemoryId())
                .userId(entity.getUserId())
                .sessionId(entity.getSessionId())
                .memoryType(entity.getMemoryType())
                .summary(entity.getSummary())
                .contentRef(entity.getContentRef())
                .score(entity.getScore())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentMemoryEventPO toPO(AgentMemoryEventEntity entity) {
        return AgentMemoryEventPO.builder()
                .eventId(entity.getEventId())
                .runId(entity.getRunId())
                .sessionId(entity.getSessionId())
                .memoryId(entity.getMemoryId())
                .eventType(entity.getEventType())
                .payloadRef(entity.getPayloadRef())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AgentMemoryEntity toEntity(AgentLongTermMemoryPO po) {
        return AgentMemoryEntity.builder()
                .memoryId(po.getMemoryId())
                .userId(po.getUserId())
                .sessionId(po.getSessionId())
                .memoryType(po.getMemoryType())
                .summary(po.getSummary())
                .contentRef(po.getContentRef())
                .score(po.getScore())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}

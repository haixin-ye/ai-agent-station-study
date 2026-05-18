package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunAuditEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.AuditTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.RunEventTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import yhx.com.infrastructure.dao.IAgentRunAuditDao;
import yhx.com.infrastructure.dao.IAgentRunEventDao;
import yhx.com.infrastructure.dao.IAgentRunTraceDao;
import yhx.com.infrastructure.dao.po.AgentRunAuditPO;
import yhx.com.infrastructure.dao.po.AgentRunEventPO;
import yhx.com.infrastructure.dao.po.AgentRunTracePO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class EventTraceRepository implements IEventTraceRepository {

    @Resource
    private IAgentRunEventDao agentRunEventDao;

    @Resource
    private IAgentRunTraceDao agentRunTraceDao;

    @Resource
    private IAgentRunAuditDao agentRunAuditDao;

    @Override
    public void appendUserVisibleEvent(AgentRunEventEntity event) {
        requireRunId(event.getRunId());
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId("event-" + UUID.randomUUID());
        }
        if (event.getSeq() == null) {
            event.setSeq(nextEventSeq(event.getRunId()));
        }
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(LocalDateTime.now());
        }
        agentRunEventDao.insert(toEventPO(event));
    }

    @Override
    public void appendTrace(AgentRunTraceEntity trace) {
        requireRunId(trace.getRunId());
        if (trace.getTraceId() == null || trace.getTraceId().isBlank()) {
            trace.setTraceId("trace-" + UUID.randomUUID());
        }
        if (trace.getSeq() == null) {
            trace.setSeq(nextTraceSeq(trace.getRunId()));
        }
        if (trace.getCreatedAt() == null) {
            trace.setCreatedAt(LocalDateTime.now());
        }
        agentRunTraceDao.insert(toTracePO(trace));
    }

    @Override
    public void appendAudit(AgentRunAuditEntity audit) {
        if (audit.getAuditId() == null || audit.getAuditId().isBlank()) {
            audit.setAuditId("audit-" + UUID.randomUUID());
        }
        if (audit.getCreatedAt() == null) {
            audit.setCreatedAt(LocalDateTime.now());
        }
        agentRunAuditDao.insert(toAuditPO(audit));
    }

    @Override
    public List<AgentRunEventEntity> listUserVisibleEvents(String runId, int limit) {
        return agentRunEventDao.listUserVisibleByRunId(runId, limit).stream()
                .map(this::toEventEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentRunTraceEntity> listDebugTrace(String runId, int limit) {
        return agentRunTraceDao.listByRunId(runId, limit).stream()
                .map(this::toTraceEntity)
                .collect(Collectors.toList());
    }

    private AgentRunEventPO toEventPO(AgentRunEventEntity entity) {
        return AgentRunEventPO.builder()
                .eventId(entity.getEventId())
                .runId(entity.getRunId())
                .seq(entity.getSeq())
                .eventType(entity.getEventType().code())
                .payloadRef(entity.getPayloadRef())
                .userVisible(Boolean.TRUE.equals(entity.getUserVisible()) ? 1 : 0)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AgentRunEventEntity toEventEntity(AgentRunEventPO po) {
        return AgentRunEventEntity.builder()
                .eventId(po.getEventId())
                .runId(po.getRunId())
                .seq(po.getSeq())
                .eventType(RunEventTypeEnumVO.ofCode(po.getEventType()).orElse(RunEventTypeEnumVO.STATUS_CHANGED))
                .payloadRef(po.getPayloadRef())
                .userVisible(po.getUserVisible() != null && po.getUserVisible() == 1)
                .createdAt(po.getCreatedAt())
                .build();
    }

    private AgentRunTracePO toTracePO(AgentRunTraceEntity entity) {
        return AgentRunTracePO.builder()
                .traceId(entity.getTraceId())
                .runId(entity.getRunId())
                .seq(entity.getSeq())
                .traceType(entity.getTraceType().code())
                .payloadRef(entity.getPayloadRef())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AgentRunTraceEntity toTraceEntity(AgentRunTracePO po) {
        return AgentRunTraceEntity.builder()
                .traceId(po.getTraceId())
                .runId(po.getRunId())
                .seq(po.getSeq())
                .traceType(TraceTypeEnumVO.ofCode(po.getTraceType()).orElse(TraceTypeEnumVO.RUNTIME_DECISION))
                .payloadRef(po.getPayloadRef())
                .createdAt(po.getCreatedAt())
                .build();
    }

    private AgentRunAuditPO toAuditPO(AgentRunAuditEntity entity) {
        return AgentRunAuditPO.builder()
                .auditId(entity.getAuditId())
                .runId(entity.getRunId())
                .auditType(entity.getAuditType().code())
                .payloadRef(entity.getPayloadRef())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private Long nextEventSeq(String runId) {
        Long maxSeq = agentRunEventDao.queryMaxSeqByRunId(runId);
        return (maxSeq == null ? 0L : maxSeq) + 1L;
    }

    private Long nextTraceSeq(String runId) {
        Long maxSeq = agentRunTraceDao.queryMaxSeqByRunId(runId);
        return (maxSeq == null ? 0L : maxSeq) + 1L;
    }

    private void requireRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required.");
        }
    }
}

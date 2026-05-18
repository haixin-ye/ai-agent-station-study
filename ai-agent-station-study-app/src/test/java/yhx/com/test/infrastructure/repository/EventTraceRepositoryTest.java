package yhx.com.test.infrastructure.repository;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.RunEventTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import yhx.com.infrastructure.adapter.repository.EventTraceRepository;
import yhx.com.infrastructure.dao.IAgentRunAuditDao;
import yhx.com.infrastructure.dao.IAgentRunEventDao;
import yhx.com.infrastructure.dao.IAgentRunTraceDao;
import yhx.com.infrastructure.dao.po.AgentRunEventPO;
import yhx.com.infrastructure.dao.po.AgentRunTracePO;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventTraceRepositoryTest {

    @Test
    public void test_appendUserVisibleEvent_emptySeq_shouldAssignNextSeq() {
        IAgentRunEventDao eventDao = mock(IAgentRunEventDao.class);
        IAgentRunTraceDao traceDao = mock(IAgentRunTraceDao.class);
        IAgentRunAuditDao auditDao = mock(IAgentRunAuditDao.class);
        when(eventDao.queryMaxSeqByRunId("run-1")).thenReturn(2L);

        EventTraceRepository repository = newRepository(eventDao, traceDao, auditDao);

        repository.appendUserVisibleEvent(AgentRunEventEntity.builder()
                .runId("run-1")
                .eventType(RunEventTypeEnumVO.STATUS_CHANGED)
                .payloadRef("payload-1")
                .userVisible(true)
                .build());

        ArgumentCaptor<AgentRunEventPO> captor = ArgumentCaptor.forClass(AgentRunEventPO.class);
        verify(eventDao).insert(captor.capture());
        Assert.assertEquals(Long.valueOf(3L), captor.getValue().getSeq());
        Assert.assertEquals("run-1", captor.getValue().getRunId());
        Assert.assertNotNull(captor.getValue().getEventId());
        Assert.assertNotNull(captor.getValue().getCreatedAt());
    }

    @Test
    public void test_appendTrace_emptySeq_shouldAssignNextSeq() {
        IAgentRunEventDao eventDao = mock(IAgentRunEventDao.class);
        IAgentRunTraceDao traceDao = mock(IAgentRunTraceDao.class);
        IAgentRunAuditDao auditDao = mock(IAgentRunAuditDao.class);
        when(traceDao.queryMaxSeqByRunId("run-1")).thenReturn(5L);

        EventTraceRepository repository = newRepository(eventDao, traceDao, auditDao);

        repository.appendTrace(AgentRunTraceEntity.builder()
                .runId("run-1")
                .traceType(TraceTypeEnumVO.RUNTIME_DECISION)
                .payloadRef("payload-2")
                .build());

        ArgumentCaptor<AgentRunTracePO> captor = ArgumentCaptor.forClass(AgentRunTracePO.class);
        verify(traceDao).insert(captor.capture());
        Assert.assertEquals(Long.valueOf(6L), captor.getValue().getSeq());
        Assert.assertEquals("run-1", captor.getValue().getRunId());
        Assert.assertNotNull(captor.getValue().getTraceId());
        Assert.assertNotNull(captor.getValue().getCreatedAt());
    }

    private EventTraceRepository newRepository(IAgentRunEventDao eventDao,
                                               IAgentRunTraceDao traceDao,
                                               IAgentRunAuditDao auditDao) {
        EventTraceRepository repository = new EventTraceRepository();
        ReflectionTestUtils.setField(repository, "agentRunEventDao", eventDao);
        ReflectionTestUtils.setField(repository, "agentRunTraceDao", traceDao);
        ReflectionTestUtils.setField(repository, "agentRunAuditDao", auditDao);
        return repository;
    }
}

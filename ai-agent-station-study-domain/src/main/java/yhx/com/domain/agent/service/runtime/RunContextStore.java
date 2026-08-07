package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunContextRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunContextEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunLoopEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.RunBaseContextVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.TaskLedgerVO;
import yhx.com.domain.agent.model.valobj.runtime.RunRuntimeControlVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RunContextStore {

    public static final int SCHEMA_VERSION = 2;

    private final IRunContextRepository contextRepository;
    private final IPayloadRepository payloadRepository;

    public RunContextStore(IRunContextRepository contextRepository, IPayloadRepository payloadRepository) {
        this.contextRepository = contextRepository;
        this.payloadRepository = payloadRepository;
    }

    public void initialize(RunContextStateVO state) {
        requireState(state);
        String runId = state.getBaseContext().getRunId();
        String baseRef = save(PayloadTypeEnumVO.RUN_BASE_CONTEXT, state.getBaseContext());
        String ledgerRef = save(PayloadTypeEnumVO.TASK_LEDGER, state.getTaskLedger());
        String controlRef = save(PayloadTypeEnumVO.RUN_RUNTIME_CONTROL, state.getRuntimeControl());
        state.setSchemaVersion(SCHEMA_VERSION);
        state.setContextVersion(1L);
        contextRepository.createContext(AgentRunContextEntity.builder()
                .runId(runId)
                .schemaVersion(SCHEMA_VERSION)
                .mainAgentStage(state.getMainAgentStage())
                .baseContextRef(baseRef)
                .taskLedgerRef(ledgerRef)
                .runtimeControlRef(controlRef)
                .contextVersion(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    public void saveContext(RunContextStateVO state) {
        requireState(state);
        AgentRunContextEntity existing = contextRepository.findContext(state.getBaseContext().getRunId())
                .orElseThrow(() -> new IllegalStateException("Run context is not initialized: " + state.getBaseContext().getRunId()));
        long expectedVersion = existing.getContextVersion();
        long nextVersion = expectedVersion + 1L;
        String baseRef = save(PayloadTypeEnumVO.RUN_BASE_CONTEXT, state.getBaseContext());
        String ledgerRef = save(PayloadTypeEnumVO.TASK_LEDGER, state.getTaskLedger());
        String controlRef = save(PayloadTypeEnumVO.RUN_RUNTIME_CONTROL, state.getRuntimeControl());
        boolean updated = contextRepository.updateContext(AgentRunContextEntity.builder()
                .runId(existing.getRunId())
                .schemaVersion(SCHEMA_VERSION)
                .mainAgentStage(state.getMainAgentStage())
                .baseContextRef(baseRef)
                .taskLedgerRef(ledgerRef)
                .runtimeControlRef(controlRef)
                .contextVersion(nextVersion)
                .createdAt(existing.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build(), expectedVersion);
        if (!updated) {
            throw new IllegalStateException("Run context version conflict: " + existing.getRunId());
        }
        state.setContextVersion(nextVersion);
    }

    public void saveLoop(RunLoopRecordVO record) {
        if (record == null || record.getRunId() == null || record.getLoopIndex() == null) {
            throw new IllegalArgumentException("Run loop identity is required.");
        }
        String recordRef = save(PayloadTypeEnumVO.RUN_LOOP_RECORD, record);
        contextRepository.saveLoop(AgentRunLoopEntity.builder()
                .runId(record.getRunId())
                .loopIndex(record.getLoopIndex())
                .mainAgentStage(record.getMainAgentStage())
                .status(record.getStatus())
                .recordRef(recordRef)
                .recordVersion(record.getRecordVersion())
                .startedAt(record.getStartedAt())
                .completedAt(record.getCompletedAt())
                .build());
    }

    public RunContextStateVO load(String runId) {
        AgentRunContextEntity context = contextRepository.findContext(runId)
                .orElseThrow(() -> new IllegalStateException("Run context not found: " + runId));
        if (!Integer.valueOf(SCHEMA_VERSION).equals(context.getSchemaVersion())) {
            throw new IllegalStateException("Unsupported run context schema: " + context.getSchemaVersion());
        }
        RunBaseContextVO baseContext = read(context.getBaseContextRef(), RunBaseContextVO.class);
        TaskLedgerVO taskLedger = read(context.getTaskLedgerRef(), TaskLedgerVO.class);
        RunRuntimeControlVO runtimeControl = read(context.getRuntimeControlRef(), RunRuntimeControlVO.class);
        List<RunLoopRecordVO> timeline = contextRepository.listLoops(runId).stream()
                .sorted(Comparator.comparing(AgentRunLoopEntity::getLoopIndex))
                .map(loop -> read(loop.getRecordRef(), RunLoopRecordVO.class))
                .toList();
        return RunContextStateVO.builder()
                .schemaVersion(context.getSchemaVersion())
                .contextVersion(context.getContextVersion())
                .mainAgentStage(context.getMainAgentStage())
                .baseContext(baseContext)
                .taskLedger(taskLedger)
                .runtimeControl(runtimeControl)
                .loopTimeline(new ArrayList<>(timeline))
                .build();
    }

    private String save(PayloadTypeEnumVO type, Object value) {
        String content = JSON.toJSONString(value, SerializerFeature.DisableCircularReferenceDetect);
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(type)
                .content(content)
                .preview(content.length() <= 200 ? content : content.substring(0, 200))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private <T> T read(String payloadRef, Class<T> type) {
        String content = payloadRepository.findContent(payloadRef)
                .orElseThrow(() -> new IllegalStateException("Run context payload not found: " + payloadRef));
        return JSON.parseObject(content, type);
    }

    private void requireState(RunContextStateVO state) {
        if (state == null || state.getBaseContext() == null || state.getTaskLedger() == null || state.getRuntimeControl() == null
                || state.getMainAgentStage() == null) {
            throw new IllegalArgumentException("Complete run context state is required.");
        }
    }
}

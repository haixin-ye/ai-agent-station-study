package yhx.com.domain.agent.service.interaction;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeContinuationRestoreResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class RuntimeContinuationSnapshotService {

    public static final int SNAPSHOT_VERSION = 2;

    private final ContinuationResumePhasePolicy phasePolicy;

    public RuntimeContinuationSnapshotService() {
        this(new ContinuationResumePhasePolicy());
    }

    public RuntimeContinuationSnapshotService(ContinuationResumePhasePolicy phasePolicy) {
        this.phasePolicy = phasePolicy == null ? new ContinuationResumePhasePolicy() : phasePolicy;
    }

    public ContinuationCheckpointVO createCheckpoint(RuntimeExecutionContext context,
                                                     String handler,
                                                     yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO resumePhase,
                                                     String sourceComponent,
                                                     String expectedAnswerValueType,
                                                     Map<String, Object> sourcePayload) {
        if (context == null || context.getRunId() == null || context.getRunId().isBlank()) {
            throw new IllegalArgumentException("Runtime context and runId are required for a continuation checkpoint.");
        }
        if (context.getRunContextState() == null) {
            throw new IllegalArgumentException("Canonical run context is required before pausing.");
        }
        if (context.getCurrentLoopRecord() == null && !preMainAgentCheckpoint(context, handler)) {
            throw new IllegalArgumentException("A current loop record is required after MainAgent execution begins.");
        }
        if (!phasePolicy.isAllowed(handler, resumePhase)) {
            throw new IllegalArgumentException("Resume phase " + resumePhase + " is not allowed for handler " + handler + ".");
        }
        ContinuationCheckpointVO checkpoint = ContinuationCheckpointVO.builder()
                .snapshotVersion(SNAPSHOT_VERSION)
                .handler(handler)
                .resumePhase(resumePhase)
                .sourceComponent(sourceComponent)
                .relatedRunId(context.getRunId())
                .relatedLoopIndex(context.getLoopIndex())
                .runContextVersion(context.getRunContextState().getContextVersion())
                .loopRecordVersion(context.getCurrentLoopRecord() == null
                        ? null : context.getCurrentLoopRecord().getRecordVersion())
                .expectedAnswerValueType(expectedAnswerValueType)
                .payload(copyMap(sourcePayload))
                .build();
        log.info("[AutoAgent][checkpoint-created] runId={}, version={}, handler={}, resumePhase={}, loopIndex={}",
                context.getRunId(), SNAPSHOT_VERSION, handler, resumePhase, context.getLoopIndex());
        return checkpoint;
    }

    public RuntimeContinuationRestoreResultVO restore(ContinuationCheckpointVO checkpoint,
                                                      RuntimeExecutionContext context) {
        String failure = validate(checkpoint, context);
        if (failure != null) {
            return failed(failure);
        }
        context.setLoopIndex(checkpoint.getRelatedLoopIndex());
        RunLoopRecordVO current = context.getCurrentLoopRecord();
        log.info("[AutoAgent][checkpoint-restored] runId={}, version={}, handler={}, resumePhase={}, loopIndex={}, loopRecordVersion={}",
                context.getRunId(), SNAPSHOT_VERSION, checkpoint.getHandler(), checkpoint.getResumePhase(),
                context.getLoopIndex(), current == null ? null : current.getRecordVersion());
        return RuntimeContinuationRestoreResultVO.builder()
                .restored(true)
                .legacyFallback(false)
                .message("Canonical run context checkpoint restored.")
                .build();
    }

    private String validate(ContinuationCheckpointVO checkpoint, RuntimeExecutionContext context) {
        if (checkpoint == null || context == null) return "Continuation checkpoint or Runtime context is missing.";
        if (!Integer.valueOf(SNAPSHOT_VERSION).equals(checkpoint.getSnapshotVersion())) {
            return "Unsupported continuation snapshot version: " + checkpoint.getSnapshotVersion() + ". Start a new run.";
        }
        if (!phasePolicy.isAllowed(checkpoint.getHandler(), checkpoint.getResumePhase())) {
            return "Resume phase " + checkpoint.getResumePhase() + " is not allowed for handler " + checkpoint.getHandler() + ".";
        }
        if (!context.getRunId().equals(checkpoint.getRelatedRunId())) return "Continuation checkpoint belongs to another Run.";
        if (context.getRunContextState() == null) {
            return "Canonical run context was not restored before continuation handling.";
        }
        if (checkpoint.getRunContextVersion() == null) {
            return "Continuation checkpoint does not contain a Run context version. Start a new run.";
        }
        if (!checkpoint.getRunContextVersion().equals(context.getRunContextState().getContextVersion())) {
            return "Run context version changed after the pending input was created.";
        }
        if (checkpoint.getLoopRecordVersion() == null && context.getCurrentLoopRecord() != null) {
            return "Continuation checkpoint was created before MainAgent, but a loop record is already active.";
        }
        if (checkpoint.getLoopRecordVersion() != null
                && (context.getCurrentLoopRecord() == null
                || !checkpoint.getLoopRecordVersion().equals(context.getCurrentLoopRecord().getRecordVersion()))) {
            return "Run loop record version changed after the pending input was created.";
        }
        return null;
    }

    private boolean preMainAgentCheckpoint(RuntimeExecutionContext context, String handler) {
        return ContextPlannerPendingInputHandler.HANDLER_CODE.equals(handler)
                && context.getRunContextState().getLoopTimeline() != null
                && context.getRunContextState().getLoopTimeline().isEmpty();
    }

    private Map<String, Object> copyMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return new LinkedHashMap<>();
        return JSON.parseObject(JSON.toJSONString(value), new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private RuntimeContinuationRestoreResultVO failed(String message) {
        log.warn("[AutoAgent][checkpoint-validation-failed] reason={}", message);
        return RuntimeContinuationRestoreResultVO.builder()
                .restored(false)
                .legacyFallback(false)
                .message(message)
                .build();
    }
}

package yhx.com.domain.agent.service.interaction;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.PreviousLoopOutcomeVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.runtime.RunWorkingStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeContinuationRestoreResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeContinuationSnapshotVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RuntimeContinuationSnapshotService {

    public static final int SNAPSHOT_VERSION = 1;

    private static final Set<String> RESUMABLE_RUNTIME_FACT_KEYS = Set.of(
            "userClarifications",
            "previousLoopOutcome",
            "resumeToolIntent",
            "toolApproval",
            "toolDenied",
            "resumeChildRunId",
            "resumeChildTaskId",
            "resumeParentRunId",
            "childAgentUserAnswer",
            "contextPlannerUserAnswer",
            "mainAgentUserAnswer",
            "ragClarification",
            "finalRepairClarification",
            "forceContextReplan",
            "nextActionHint");

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
        if (!phasePolicy.isAllowed(handler, resumePhase)) {
            throw new IllegalArgumentException("Resume phase " + resumePhase + " is not allowed for handler " + handler + ".");
        }
        RuntimeContinuationSnapshotVO snapshot = RuntimeContinuationSnapshotVO.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .loopIndex(context.getLoopIndex())
                .maxLoop(context.getMaxLoop())
                .recoveryCounters(copy(context.getRecoveryCounters(), yhx.com.domain.agent.model.valobj.runtime.RuntimeRecoveryCounters.class))
                .lastStateView(copy(context.getLastStateView(), yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO.class))
                .workingState(copy(context.getWorkingState(), RunWorkingStateVO.class))
                .lastContextSelections(copySelections(context.getLastContextSelections()))
                .lastAction(copy(context.getLastAction(), yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO.class))
                .resumableRuntimeFacts(copyRuntimeFacts(context.getRuntimeFacts()))
                .build();
        String snapshotFailure = validateExactSnapshot(snapshot);
        if (snapshotFailure != null) {
            throw new IllegalArgumentException(snapshotFailure);
        }
        ContinuationCheckpointVO checkpoint = ContinuationCheckpointVO.builder()
                .snapshotVersion(SNAPSHOT_VERSION)
                .handler(handler)
                .resumePhase(resumePhase)
                .sourceComponent(sourceComponent)
                .relatedRunId(context.getRunId())
                .relatedLoopIndex(context.getLoopIndex())
                .expectedAnswerValueType(expectedAnswerValueType)
                .runtimeSnapshot(snapshot)
                .payload(copyMap(sourcePayload))
                .build();
        log.info("[AutoAgent][checkpoint-created] runId={}, version={}, handler={}, resumePhase={}, loopIndex={}",
                context.getRunId(), SNAPSHOT_VERSION, handler, resumePhase, context.getLoopIndex());
        return checkpoint;
    }

    public RuntimeContinuationRestoreResultVO restore(ContinuationCheckpointVO checkpoint,
                                                      RuntimeExecutionContext context) {
        if (checkpoint == null || context == null) {
            return failed(false, "Continuation checkpoint or Runtime context is missing.");
        }
        if (checkpoint.getSnapshotVersion() == null) {
            return restoreLegacy(checkpoint, context);
        }
        if (!Integer.valueOf(SNAPSHOT_VERSION).equals(checkpoint.getSnapshotVersion())) {
            return failed(false, "Unsupported continuation snapshot version: " + checkpoint.getSnapshotVersion() + ".");
        }
        if (!phasePolicy.isAllowed(checkpoint.getHandler(), checkpoint.getResumePhase())) {
            return failed(false, "Resume phase " + checkpoint.getResumePhase()
                    + " is not allowed for handler " + checkpoint.getHandler() + ".");
        }
        RuntimeContinuationSnapshotVO snapshot = checkpoint.getRuntimeSnapshot();
        if (snapshot == null) {
            return failed(false, "Versioned continuation checkpoint is missing runtimeSnapshot.");
        }
        String runMismatch = runMismatch(checkpoint, snapshot, context);
        if (runMismatch != null) {
            return failed(false, runMismatch);
        }
        String snapshotFailure = validateExactSnapshot(snapshot);
        if (snapshotFailure != null) {
            return failed(false, snapshotFailure);
        }
        context.setLoopIndex(snapshot.getLoopIndex());
        context.setMaxLoop(snapshot.getMaxLoop());
        context.setRecoveryCounters(snapshot.getRecoveryCounters());
        context.setLastStateView(snapshot.getLastStateView());
        context.setWorkingState(snapshot.getWorkingState());
        context.setLastContextSelections(snapshot.getLastContextSelections());
        context.setLastAction(snapshot.getLastAction());
        context.setRuntimeFacts(normalizeRestoredFacts(snapshot.getResumableRuntimeFacts()));
        log.info("[AutoAgent][checkpoint-restored] runId={}, version={}, handler={}, resumePhase={}, loopIndex={}",
                context.getRunId(), SNAPSHOT_VERSION, checkpoint.getHandler(), checkpoint.getResumePhase(), context.getLoopIndex());
        return RuntimeContinuationRestoreResultVO.builder()
                .restored(true)
                .legacyFallback(false)
                .message("Runtime continuation snapshot restored.")
                .build();
    }

    private RuntimeContinuationRestoreResultVO restoreLegacy(ContinuationCheckpointVO checkpoint,
                                                             RuntimeExecutionContext context) {
        if (!phasePolicy.isAllowed(checkpoint.getHandler(), checkpoint.getResumePhase())) {
            return failed(true, "Legacy resume phase " + checkpoint.getResumePhase()
                    + " is not allowed for handler " + checkpoint.getHandler() + ".");
        }
        if (checkpoint.getRelatedRunId() != null && context.getRunId() != null
                && !checkpoint.getRelatedRunId().equals(context.getRunId())) {
            return failed(true, "Legacy checkpoint belongs to another Run.");
        }
        Map<String, Object> payload = checkpoint.getPayload();
        if (payload != null) {
            Object workingState = firstNonNull(payload.get("workingState"), payload.get("runWorkingState"));
            if (workingState != null) {
                context.setWorkingState(JSON.parseObject(serialize(workingState), RunWorkingStateVO.class));
            }
            Object selections = payload.get("contextSelections");
            if (selections != null) {
                context.setLastContextSelections(JSON.parseArray(serialize(selections), ContextSelectionVO.class));
            }
        }
        if (checkpoint.getRelatedLoopIndex() != null) {
            context.setLoopIndex(checkpoint.getRelatedLoopIndex());
        }
        if (context.getRuntimeFacts() == null) {
            context.setRuntimeFacts(new HashMap<>());
        }
        log.warn("[AutoAgent][checkpoint-legacy-fallback] runId={}, handler={}, resumePhase={}, loopIndex={}",
                context.getRunId(), checkpoint.getHandler(), checkpoint.getResumePhase(), context.getLoopIndex());
        return RuntimeContinuationRestoreResultVO.builder()
                .restored(true)
                .legacyFallback(true)
                .message("Legacy continuation checkpoint restored with bounded fallback.")
                .build();
    }

    private String runMismatch(ContinuationCheckpointVO checkpoint,
                               RuntimeContinuationSnapshotVO snapshot,
                               RuntimeExecutionContext context) {
        String runId = context.getRunId();
        if (runId != null && checkpoint.getRelatedRunId() != null && !runId.equals(checkpoint.getRelatedRunId())) {
            return "Continuation checkpoint belongs to another Run.";
        }
        if (runId != null && snapshot.getRunId() != null && !runId.equals(snapshot.getRunId())) {
            return "Runtime snapshot belongs to another Run.";
        }
        return null;
    }

    private String validateExactSnapshot(RuntimeContinuationSnapshotVO snapshot) {
        if (snapshot == null) {
            return "Versioned continuation checkpoint is missing runtimeSnapshot.";
        }
        if (snapshot.getRunId() == null || snapshot.getRunId().isBlank()) {
            return "Versioned runtimeSnapshot is missing runId.";
        }
        if (snapshot.getLoopIndex() == null || snapshot.getLoopIndex() < 0) {
            return "Versioned runtimeSnapshot is missing a valid loopIndex.";
        }
        if (snapshot.getMaxLoop() == null || snapshot.getMaxLoop() <= 0) {
            return "Versioned runtimeSnapshot is missing a valid maxLoop.";
        }
        if (snapshot.getRecoveryCounters() == null) {
            return "Versioned runtimeSnapshot is missing recoveryCounters.";
        }
        if (snapshot.getRecoveryCounters().getLoopCount() == null
                || snapshot.getRecoveryCounters().getContractRepairCount() == null
                || snapshot.getRecoveryCounters().getFinalRepairCount() == null
                || snapshot.getRecoveryCounters().getToolRetryCount() == null
                || snapshot.getRecoveryCounters().getRagRetryCount() == null
                || snapshot.getRecoveryCounters().getContextCompressionCount() == null) {
            return "Versioned runtimeSnapshot has incomplete recoveryCounters.";
        }
        return null;
    }

    private Map<String, Object> copyRuntimeFacts(Map<String, Object> runtimeFacts) {
        Map<String, Object> copied = new LinkedHashMap<>();
        if (runtimeFacts == null) {
            return copied;
        }
        for (String key : RESUMABLE_RUNTIME_FACT_KEYS) {
            Object value = runtimeFacts.get(key);
            if (value != null) {
                copied.put(key, JSON.parse(serialize(value)));
            }
        }
        return copied;
    }

    private Map<String, Object> normalizeRestoredFacts(Map<String, Object> facts) {
        Map<String, Object> restored = new HashMap<>();
        if (facts == null) {
            return restored;
        }
        facts.forEach((key, value) -> restored.put(key, normalizeFact(key, value)));
        return restored;
    }

    private Object normalizeFact(String key, Object value) {
        if (value == null) {
            return null;
        }
        if ("userClarifications".equals(key)) {
            return JSON.parseArray(serialize(value), UserClarificationVO.class);
        }
        if ("previousLoopOutcome".equals(key)) {
            return JSON.parseObject(serialize(value), PreviousLoopOutcomeVO.class);
        }
        return value;
    }

    private List<ContextSelectionVO> copySelections(List<ContextSelectionVO> selections) {
        if (selections == null) {
            return null;
        }
        return JSON.parseArray(serialize(selections), ContextSelectionVO.class);
    }

    private Map<String, Object> copyMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return JSON.parseObject(serialize(value), new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private <T> T copy(T value, Class<T> type) {
        if (value == null) {
            return null;
        }
        return JSON.parseObject(serialize(value), type);
    }

    private String serialize(Object value) {
        return JSON.toJSONString(value, SerializerFeature.DisableCircularReferenceDetect);
    }

    private Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    private RuntimeContinuationRestoreResultVO failed(boolean legacy, String message) {
        log.warn("[AutoAgent][checkpoint-validation-failed] legacyFallback={}, reason={}", legacy, message);
        return RuntimeContinuationRestoreResultVO.builder()
                .restored(false)
                .legacyFallback(legacy)
                .message(message)
                .build();
    }
}

package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.PreviousLoopOutcomeVO;
import yhx.com.domain.agent.model.valobj.context.RunMetaVO;
import yhx.com.domain.agent.model.valobj.context.UserInputVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionRequestSnapshotVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionResultSnapshotVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RunWorkingStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeWorklogItemVO;
import yhx.com.domain.agent.service.tool.ToolApprovalKeyGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunWorkingStateManager {

    private final PerUpdateMergeService perUpdateMergeService = new PerUpdateMergeService();
    private final ToolApprovalKeyGenerator toolApprovalKeyGenerator = new ToolApprovalKeyGenerator();

    public RunWorkingStateVO initialize(MainAgentStateViewVO stateView) {
        if (stateView == null) {
            return null;
        }
        return RunWorkingStateVO.builder()
                .baseStateView(stateView)
                .notebook(stateView.getNotebook())
                .worklog(new ArrayList<>(defaultList(stateView.getWorklog())))
                .actionHistory(new ArrayList<>())
                .evidencePack(new ArrayList<>(defaultList(stateView.getEvidencePack())))
                .userClarifications(new ArrayList<>(defaultList(stateView.getUserClarifications())))
                .previousLoopOutcome(stateView.getPreviousLoopOutcome())
                .nextSequence(1L)
                .build();
    }

    public void apply(RuntimeExecutionContext context, MainAgentActionVO action, MainActionHandlerResult result) {
        if (context == null || result == null) {
            return;
        }
        if (context.getWorkingState() == null) {
            context.setWorkingState(initialize(baseStateView(context)));
        }
        RunWorkingStateVO workingState = context.getWorkingState();
        if (workingState == null) {
            return;
        }
        mergePerUpdate(context, workingState, action);
        ActionEffectVO effect = enrichEffectWithAction(action,
                firstNonNull(result.getActionEffect(), defaultEffect(context, action, result)));
        appendWorklog(workingState, context, action, result, effect);
        mergeEffect(workingState, effect);
    }

    public MainAgentStateViewVO project(RunWorkingStateVO workingState) {
        if (workingState == null || workingState.getBaseStateView() == null) {
            return null;
        }
        MainAgentStateViewVO base = workingState.getBaseStateView();
        return MainAgentStateViewVO.builder()
                .runMeta(base.getRunMeta())
                .userInput(base.getUserInput())
                .conversation(base.getConversation())
                .memoryPack(defaultList(base.getMemoryPack()))
                .ragPack(defaultList(base.getRagPack()))
                .resolvedArtifacts(defaultList(base.getResolvedArtifacts()))
                .artifactContent(defaultList(base.getArtifactContent()))
                .evidencePack(mergedEvidence(base.getEvidencePack(), workingState.getEvidencePack()))
                .userClarifications(mergedClarifications(base.getUserClarifications(), workingState.getUserClarifications()))
                .actionHistory(new ArrayList<>(defaultList(workingState.getActionHistory())))
                .notebook(workingState.getNotebook())
                .worklog(new ArrayList<>(defaultList(workingState.getWorklog())))
                .availableCapabilities(defaultList(base.getAvailableCapabilities()))
                .pendingAction(base.getPendingAction())
                .previousLoopOutcome(firstNonNull(workingState.getPreviousLoopOutcome(), base.getPreviousLoopOutcome()))
                .currentPlan(base.getCurrentPlan())
                .lastVerifierFeedback(base.getLastVerifierFeedback())
                .outputContractVersion(base.getOutputContractVersion())
                .tokenBudget(base.getTokenBudget())
                .failure(base.getFailure())
                .build();
    }

    private void appendWorklog(RunWorkingStateVO workingState,
                               RuntimeExecutionContext context,
                               MainAgentActionVO action,
                               MainActionHandlerResult result,
                               ActionEffectVO effect) {
        if (effect == null) {
            return;
        }
        if (workingState.getWorklog() == null) {
            workingState.setWorklog(new ArrayList<>());
        }
        Long sequence = nextSequence(workingState);
        LocalDateTime now = LocalDateTime.now();
        String repeatGuardKey = repeatGuardKey(action, effect);
        workingState.getWorklog().add(RuntimeWorklogItemVO.builder()
                .workId(firstNonBlank(effect.getWorkId(), "work-" + sequence))
                .runId(context.getRunId())
                .loopIndex(firstNonNull(effect.getLoopIndex(), context.getLoopIndex()))
                .sequence(sequence)
                .actionType(firstNonBlank(effect.getAction(), action == null ? null : action.getAction()))
                .status(firstNonBlank(effect.getStatus(), result.getStatus() == null ? null : result.getStatus().name()))
                .sourceComponent("MAIN_AGENT")
                .request(buildRequestSnapshot(action, effect))
                .resultRef(effect.getResultRef())
                .result(buildResultSnapshot(result, effect))
                .resultEvidenceIds(defaultList(effect.getCreatedEvidenceIds()))
                .repeatGuardKey(repeatGuardKey)
                .startedAt(now)
                .completedAt(now)
                .metadata(new LinkedHashMap<>())
                .build());
    }

    private String repeatGuardKey(MainAgentActionVO action, ActionEffectVO effect) {
        if (effect == null) {
            return null;
        }
        if (effect.getRepeatGuardKey() != null && !effect.getRepeatGuardKey().isBlank()) {
            return effect.getRepeatGuardKey();
        }
        String actionType = firstNonBlank(effect.getAction(), action == null ? null : action.getAction());
        if (!MainAgentActionTypeEnumVO.CALL_TOOL.code().equals(actionType) || effect.getToolIntent() == null) {
            return null;
        }
        Map<String, Object> arguments = mapValue(effect.getToolIntent(), "arguments");
        String argumentsHash = toolApprovalKeyGenerator.argumentsHash(arguments);
        return "CALL_TOOL:"
                + safe(stringValue(effect.getToolIntent(), "capabilityCode"))
                + ":"
                + safe(stringValue(effect.getToolIntent(), "mcpServerCode"))
                + ":"
                + safe(stringValue(effect.getToolIntent(), "toolName"))
                + ":"
                + argumentsHash;
    }

    private ActionRequestSnapshotVO buildRequestSnapshot(MainAgentActionVO action, ActionEffectVO effect) {
        Map<String, Object> toolIntent = effect.getToolIntent();
        Map<String, Object> raw = effect.getRequestSnapshot() == null ? action == null ? null : action.getStateDelta() : effect.getRequestSnapshot();
        return ActionRequestSnapshotVO.builder()
                .actionType(firstNonBlank(effect.getAction(), action == null ? null : action.getAction()))
                .capabilityCode(stringValue(toolIntent, "capabilityCode"))
                .mcpServerCode(stringValue(toolIntent, "mcpServerCode"))
                .toolName(stringValue(toolIntent, "toolName"))
                .arguments(mapValue(toolIntent, "arguments"))
                .argumentsRef(stringValue(toolIntent, "argumentsRef"))
                .goal(stringValue(toolIntent, "goal"))
                .raw(raw)
                .build();
    }

    private ActionResultSnapshotVO buildResultSnapshot(MainActionHandlerResult result, ActionEffectVO effect) {
        return ActionResultSnapshotVO.builder()
                .status(effect.getStatus())
                .message(firstNonBlank(result.getMessage(), effect.getMessage()))
                .raw(effect.getResultSnapshot())
                .build();
    }

    private void mergePerUpdate(RuntimeExecutionContext context, RunWorkingStateVO workingState, MainAgentActionVO action) {
        if (action == null || action.getPerUpdate() == null || action.getPerUpdate().isEmpty()) {
            return;
        }
        Long sequence = nextSequence(workingState);
        workingState.setNotebook(perUpdateMergeService.merge(
                workingState.getNotebook(),
                action.getPerUpdate(),
                context.getLoopIndex(),
                sequence
        ));
    }

    private Long nextSequence(RunWorkingStateVO workingState) {
        Long sequence = workingState.getNextSequence() == null ? 1L : workingState.getNextSequence();
        workingState.setNextSequence(sequence + 1);
        return sequence;
    }

    private ActionEffectVO defaultEffect(RuntimeExecutionContext context, MainAgentActionVO action, MainActionHandlerResult result) {
        return ActionEffectVO.builder()
                .action(action == null ? null : action.getAction())
                .status(result.getStatus() == null ? null : result.getStatus().name())
                .message(result.getMessage())
                .loopIndex(context.getLoopIndex())
                .toolIntent(toolIntentFromAction(action))
                .createdEvidenceIds(defaultList(result.getCreatedEvidenceIds()))
                .createdEvidence(defaultList(result.getCreatedEvidence()))
                .createdArtifactIds(defaultList(result.getCreatedArtifactIds()))
                .build();
    }

    private MainAgentStateViewVO baseStateView(RuntimeExecutionContext context) {
        if (context == null) {
            return MainAgentStateViewVO.builder().build();
        }
        if (context.getLastStateView() != null) {
            return context.getLastStateView();
        }
        return MainAgentStateViewVO.builder()
                .runMeta(RunMetaVO.builder()
                        .runId(context.getRunId())
                        .sessionId(context.getSessionId())
                        .userId(context.getUserId())
                        .agentId(context.getAgentId())
                        .loopIndex(context.getLoopIndex())
                        .build())
                .userInput(UserInputVO.builder()
                        .messageId(context.getUserMessageId())
                        .content(context.getUserInput())
                        .build())
                .build();
    }

    private ActionEffectVO enrichEffectWithAction(MainAgentActionVO action, ActionEffectVO effect) {
        if (effect == null || effect.getToolIntent() != null) {
            return effect;
        }
        Map<String, Object> toolIntent = toolIntentFromAction(action);
        if (toolIntent == null) {
            return effect;
        }
        effect.setToolIntent(toolIntent);
        if (effect.getAction() == null || effect.getAction().isBlank()) {
            effect.setAction(MainAgentActionTypeEnumVO.CALL_TOOL.code());
        }
        return effect;
    }

    private void mergeEffect(RunWorkingStateVO workingState, ActionEffectVO effect) {
        if (effect == null) {
            return;
        }
        if (workingState.getActionHistory() == null) {
            workingState.setActionHistory(new ArrayList<>());
        }
        int duplicateIndex = duplicateActionEffectIndex(workingState.getActionHistory(), effect);
        if (duplicateIndex >= 0) {
            ActionEffectVO existing = workingState.getActionHistory().get(duplicateIndex);
            if (existing.getToolIntent() == null && effect.getToolIntent() != null) {
                workingState.getActionHistory().set(duplicateIndex, effect);
            }
            mergeEffectSideChannels(workingState, effect);
            return;
        }
        workingState.getActionHistory().add(effect);
        mergeEffectSideChannels(workingState, effect);
    }

    private void mergeEffectSideChannels(RunWorkingStateVO workingState, ActionEffectVO effect) {
        if (effect.getCreatedEvidence() != null && !effect.getCreatedEvidence().isEmpty()) {
            workingState.setEvidencePack(mergedEvidence(workingState.getEvidencePack(), effect.getCreatedEvidence()));
        }
        if (effect.getUserClarifications() != null && !effect.getUserClarifications().isEmpty()) {
            workingState.setUserClarifications(mergedClarifications(workingState.getUserClarifications(), effect.getUserClarifications()));
        }
        if (effect.getPreviousLoopOutcome() != null) {
            workingState.setPreviousLoopOutcome(effect.getPreviousLoopOutcome());
        }
    }

    private int duplicateActionEffectIndex(List<ActionEffectVO> history, ActionEffectVO incoming) {
        if (history == null || incoming == null) {
            return -1;
        }
        for (int index = 0; index < history.size(); index++) {
            ActionEffectVO existing = history.get(index);
            if (sameActionEffect(existing, incoming)) {
                return index;
            }
        }
        return -1;
    }

    private boolean sameActionEffect(ActionEffectVO left, ActionEffectVO right) {
        if (left == null || right == null) {
            return false;
        }
        return java.util.Objects.equals(left.getAction(), right.getAction())
                && java.util.Objects.equals(left.getStatus(), right.getStatus())
                && java.util.Objects.equals(left.getMessage(), right.getMessage())
                && java.util.Objects.equals(left.getCreatedEvidenceIds(), right.getCreatedEvidenceIds())
                && java.util.Objects.equals(left.getCreatedArtifactIds(), right.getCreatedArtifactIds());
    }

    private List<MaterializedEvidenceVO> mergedEvidence(List<MaterializedEvidenceVO> existing, List<MaterializedEvidenceVO> incoming) {
        Map<String, MaterializedEvidenceVO> merged = new LinkedHashMap<>();
        if (existing != null) {
            existing.stream()
                    .filter(item -> item != null && item.getEvidenceId() != null)
                    .forEach(item -> merged.put(item.getEvidenceId(), item));
        }
        if (incoming != null) {
            incoming.stream()
                    .filter(item -> item != null && item.getEvidenceId() != null)
                    .forEach(item -> merged.put(item.getEvidenceId(), item));
        }
        return new ArrayList<>(merged.values());
    }

    private List<UserClarificationVO> mergedClarifications(List<UserClarificationVO> existing, List<UserClarificationVO> incoming) {
        List<UserClarificationVO> merged = new ArrayList<>();
        if (existing != null) {
            merged.addAll(existing);
        }
        if (incoming != null) {
            merged.addAll(incoming);
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolIntentFromAction(MainAgentActionVO action) {
        if (action == null || action.getStateDelta() == null
                || !MainAgentActionTypeEnumVO.CALL_TOOL.code().equals(action.getAction())) {
            return null;
        }
        Object value = action.getStateDelta().get("toolIntent");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private <T> List<T> defaultList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String stringValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return null;
    }
}

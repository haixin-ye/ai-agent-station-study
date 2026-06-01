package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.PreviousLoopOutcomeVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RunWorkingStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunWorkingStateManager {

    public RunWorkingStateVO initialize(MainAgentStateViewVO stateView) {
        if (stateView == null) {
            return null;
        }
        return RunWorkingStateVO.builder()
                .baseStateView(stateView)
                .actionHistory(new ArrayList<>())
                .evidencePack(new ArrayList<>(defaultList(stateView.getEvidencePack())))
                .userClarifications(new ArrayList<>(defaultList(stateView.getUserClarifications())))
                .previousLoopOutcome(stateView.getPreviousLoopOutcome())
                .build();
    }

    public void apply(RuntimeExecutionContext context, MainAgentActionVO action, MainActionHandlerResult result) {
        if (context == null || result == null) {
            return;
        }
        if (context.getWorkingState() == null) {
            context.setWorkingState(initialize(context.getLastStateView()));
        }
        RunWorkingStateVO workingState = context.getWorkingState();
        if (workingState == null) {
            return;
        }
        ActionEffectVO effect = firstNonNull(result.getActionEffect(), defaultEffect(context, action, result));
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

    private ActionEffectVO defaultEffect(RuntimeExecutionContext context, MainAgentActionVO action, MainActionHandlerResult result) {
        return ActionEffectVO.builder()
                .action(action == null ? null : action.getAction())
                .status(result.getStatus() == null ? null : result.getStatus().name())
                .message(result.getMessage())
                .loopIndex(context.getLoopIndex())
                .createdEvidenceIds(defaultList(result.getCreatedEvidenceIds()))
                .createdEvidence(defaultList(result.getCreatedEvidence()))
                .createdArtifactIds(defaultList(result.getCreatedArtifactIds()))
                .build();
    }

    private void mergeEffect(RunWorkingStateVO workingState, ActionEffectVO effect) {
        if (effect == null) {
            return;
        }
        if (workingState.getActionHistory() == null) {
            workingState.setActionHistory(new ArrayList<>());
        }
        workingState.getActionHistory().add(effect);
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

    private <T> List<T> defaultList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
    }
}

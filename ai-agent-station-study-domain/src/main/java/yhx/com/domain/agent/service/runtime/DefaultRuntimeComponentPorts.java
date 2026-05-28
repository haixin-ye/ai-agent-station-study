package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.node.contextplanner.ContextPlannerNodeService;
import yhx.com.domain.agent.service.node.mainagent.MainAgentNodeService;
import yhx.com.domain.agent.service.context.ContextPlannerStatusHandler;
import yhx.com.domain.agent.service.context.ContextPreparationService;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;

import java.util.List;
import java.util.Map;

@Slf4j
public class DefaultRuntimeComponentPorts implements RuntimeComponentPorts {

    private static final String CONTEXT_PLANNER_CONTRACT_VERSION = "context-planner-output-v1";
    private static final String DEFAULT_PROMPT_VERSION = "v1";

    private final ContextPreparationService contextPreparationService;
    private final ContextPlannerNodeService contextPlannerNodeService;
    private final ContextPlannerStatusHandler contextPlannerStatusHandler;
    private final MainAgentNodeService mainAgentNodeService;
    private final Map<String, NodeInvocationProfileVO> profiles;
    private final List<CapabilityCandidateVO> availableCapabilities;
    private final TokenBudgetVO defaultTokenBudget;

    public DefaultRuntimeComponentPorts(ContextPreparationService contextPreparationService,
                                        ContextPlannerNodeService contextPlannerNodeService,
                                        ContextPlannerStatusHandler contextPlannerStatusHandler,
                                        MainAgentNodeService mainAgentNodeService,
                                        Map<String, NodeInvocationProfileVO> profiles,
                                        List<CapabilityCandidateVO> availableCapabilities,
                                        TokenBudgetVO defaultTokenBudget) {
        this.contextPreparationService = contextPreparationService;
        this.contextPlannerNodeService = contextPlannerNodeService;
        this.contextPlannerStatusHandler = contextPlannerStatusHandler;
        this.mainAgentNodeService = mainAgentNodeService;
        this.profiles = profiles == null ? Map.of() : profiles;
        this.availableCapabilities = availableCapabilities == null ? List.of() : availableCapabilities;
        this.defaultTokenBudget = defaultTokenBudget == null ? defaultTokenBudget() : defaultTokenBudget;
    }

    @Override
    public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
        log.info("[AutoAgent][context-prepare] runId={}, sessionId={}, loopIndex={}",
                context.getRunId(), context.getSessionId(), context.getLoopIndex());
        AutoAgentHumanLog.stage("上下文准备", context.getRunId(), "开始收集候选：sessionId="
                + context.getSessionId() + "，用户问题=" + preview(context.getUserInput(), 80));
        ContextCandidateBundleVO candidates = contextPreparationService.prepare(ContextPreparationCommand.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .agentId(context.getAgentId())
                .userMessageId(context.getUserMessageId())
                .userInput(context.getUserInput())
                .loopIndex(context.getLoopIndex())
                .availableCapabilities(availableCapabilities)
                .tokenBudget(defaultTokenBudget)
                .runtimeFacts(context.getRuntimeFacts())
                .build());
        AutoAgentHumanLog.contextCandidates(context.getRunId(), candidates);
        ContextPlannerOutputVO plannerOutput = contextPlannerNodeService.plan(planningView(candidates),
                context.getRunId(),
                context.getAgentId(),
                contextPlannerProfile());
        AutoAgentHumanLog.contextPlannerOutput(context.getRunId(), plannerOutput);
        ContextPlannerHandlingResult result = contextPlannerStatusHandler.handle(plannerOutput, candidates);
        AutoAgentHumanLog.contextPlannerResult(context.getRunId(), result, candidates);
        if (result != null && result.getStateView() != null) {
            AutoAgentHumanLog.stateView(context.getRunId(), result.getStateView());
        }
        log.info("[AutoAgent][context-ready] runId={}, loopIndex={}, hasStateView={}, askUser={}, failure={}",
                context.getRunId(), context.getLoopIndex(),
                result != null && result.getStateView() != null,
                result != null && result.getAskUserRequest() != null,
                result == null ? null : result.getFailure());
        return result;
    }

    @Override
    public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
        log.info("[AutoAgent][context-refresh] runId={}, sessionId={}, loopIndex={}",
                context.getRunId(), context.getSessionId(), context.getLoopIndex());
        AutoAgentHumanLog.stage("上下文刷新", context.getRunId(), "开始刷新状态视图：loop="
                + context.getLoopIndex() + "，用户问题=" + preview(context.getUserInput(), 80));
        ContextCandidateBundleVO candidates = contextPreparationService.prepare(ContextPreparationCommand.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .agentId(context.getAgentId())
                .userMessageId(context.getUserMessageId())
                .userInput(context.getUserInput())
                .loopIndex(context.getLoopIndex())
                .availableCapabilities(availableCapabilities)
                .tokenBudget(defaultTokenBudget)
                .runtimeFacts(context.getRuntimeFacts())
                .build());
        AutoAgentHumanLog.contextCandidates(context.getRunId(), candidates);
        ContextPlannerHandlingResult result = contextPlannerStatusHandler.refreshWithoutPlanner(candidates, resumeSelections(context));
        AutoAgentHumanLog.contextPlannerResult(context.getRunId(), result, candidates);
        if (result != null && result.getStateView() != null) {
            AutoAgentHumanLog.stateView(context.getRunId(), result.getStateView());
        }
        log.info("[AutoAgent][context-refreshed] runId={}, loopIndex={}, hasStateView={}, failure={}",
                context.getRunId(), context.getLoopIndex(),
                result != null && result.getStateView() != null,
                result == null ? null : result.getFailure());
        return result;
    }

    @Override
    public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
        return mainAgentNodeService.invoke(context);
    }

    public NodeInvocationProfileVO contextPlannerProfile() {
        NodeInvocationProfileVO profile = profile(AgentComponentCodeEnumVO.CONTEXT_PLANNER.name());
        if (profile.getContractVersion() == null || profile.getContractVersion().isBlank()) {
            profile.setContractVersion(CONTEXT_PLANNER_CONTRACT_VERSION);
        }
        if (profile.getPromptVersion() == null || profile.getPromptVersion().isBlank()) {
            profile.setPromptVersion(DEFAULT_PROMPT_VERSION);
        }
        return profile;
    }

    private NodeInvocationProfileVO profile(String componentCode) {
        NodeInvocationProfileVO configured = profiles.get(componentCode);
        if (configured == null) {
            return NodeInvocationProfileVO.builder()
                    .componentCode(componentCode)
                    .promptVersion(DEFAULT_PROMPT_VERSION)
                    .contractVersion(CONTEXT_PLANNER_CONTRACT_VERSION)
                    .maxRepairAttempts(1)
                    .build();
        }
        return NodeInvocationProfileVO.builder()
                .componentCode(firstNonBlank(configured.getComponentCode(), componentCode))
                .modelCode(configured.getModelCode())
                .promptVersion(firstNonBlank(configured.getPromptVersion(), DEFAULT_PROMPT_VERSION))
                .contractVersion(firstNonBlank(configured.getContractVersion(), CONTEXT_PLANNER_CONTRACT_VERSION))
                .temperature(configured.getTemperature())
                .maxOutputTokens(configured.getMaxOutputTokens())
                .maxRepairAttempts(configured.getMaxRepairAttempts())
                .build();
    }

    private TokenBudgetVO defaultTokenBudget() {
        return TokenBudgetVO.builder()
                .maxStateViewTokens(6000)
                .reservedOutputTokens(1000)
                .maxArtifactInlineChars(4000)
                .maxEvidenceSummaryChars(800)
                .overBudget(false)
                .build();
    }

    private ContextCandidateBundleVO planningView(ContextCandidateBundleVO candidates) {
        if (candidates == null) {
            return null;
        }
        return ContextCandidateBundleVO.builder()
                .runMeta(candidates.getRunMeta())
                .userInput(candidates.getUserInput())
                .fixedRecentMessages(List.of())
                .recentMessages(candidates.getRecentMessages() == null ? List.of() : candidates.getRecentMessages())
                .sessionTaskSummary(candidates.getSessionTaskSummary())
                .sessionSummaries(candidates.getSessionSummaries() == null ? List.of() : candidates.getSessionSummaries())
                .artifactCandidates(candidates.getArtifactCandidates() == null ? List.of() : candidates.getArtifactCandidates())
                .memoryCandidates(candidates.getMemoryCandidates() == null ? List.of() : candidates.getMemoryCandidates())
                .evidenceCandidates(candidates.getEvidenceCandidates() == null ? List.of() : candidates.getEvidenceCandidates())
                .userClarifications(candidates.getUserClarifications() == null ? List.of() : candidates.getUserClarifications())
                .availableCapabilities(candidates.getAvailableCapabilities() == null ? List.of() : candidates.getAvailableCapabilities())
                .pendingAction(candidates.getPendingAction())
                .tokenBudget(candidates.getTokenBudget())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<ContextSelectionVO> resumeSelections(RuntimeExecutionContext context) {
        if (context == null) {
            return List.of();
        }
        if (context.getLastContextSelections() != null && !context.getLastContextSelections().isEmpty()) {
            return context.getLastContextSelections();
        }
        Object checkpointValue = context.getRuntimeFacts() == null ? null : context.getRuntimeFacts().get("continuationCheckpoint");
        if (!(checkpointValue instanceof ContinuationCheckpointVO checkpoint) || checkpoint.getPayload() == null) {
            return List.of();
        }
        Object selections = checkpoint.getPayload().get("contextSelections");
        if (selections == null) {
            return List.of();
        }
        return JSON.parseArray(JSON.toJSONString(selections), ContextSelectionVO.class);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String preview(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }
}

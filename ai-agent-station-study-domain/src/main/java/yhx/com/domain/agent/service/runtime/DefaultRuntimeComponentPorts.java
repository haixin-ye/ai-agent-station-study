package yhx.com.domain.agent.service.runtime;

import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.FailureVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.context.ContextPlannerNodeService;
import yhx.com.domain.agent.service.context.ContextPlannerStatusHandler;
import yhx.com.domain.agent.service.context.ContextPreparationService;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DefaultRuntimeComponentPorts implements RuntimeComponentPorts {

    private static final String CONTEXT_PLANNER_CONTRACT_VERSION = "context-planner-output-v1";
    private static final String MAIN_AGENT_CONTRACT_VERSION = "main-agent-action-v1";
    private static final String DEFAULT_PROMPT_VERSION = "v1";

    private final ContextPreparationService contextPreparationService;
    private final ContextPlannerNodeService contextPlannerNodeService;
    private final ContextPlannerStatusHandler contextPlannerStatusHandler;
    private final NodeInvocationPipeline nodeInvocationPipeline;
    private final Map<String, NodeInvocationProfileVO> profiles;
    private final List<CapabilityCandidateVO> availableCapabilities;
    private final TokenBudgetVO defaultTokenBudget;

    public DefaultRuntimeComponentPorts(ContextPreparationService contextPreparationService,
                                        ContextPlannerNodeService contextPlannerNodeService,
                                        ContextPlannerStatusHandler contextPlannerStatusHandler,
                                        NodeInvocationPipeline nodeInvocationPipeline,
                                        Map<String, NodeInvocationProfileVO> profiles,
                                        List<CapabilityCandidateVO> availableCapabilities,
                                        TokenBudgetVO defaultTokenBudget) {
        this.contextPreparationService = contextPreparationService;
        this.contextPlannerNodeService = contextPlannerNodeService;
        this.contextPlannerStatusHandler = contextPlannerStatusHandler;
        this.nodeInvocationPipeline = nodeInvocationPipeline;
        this.profiles = profiles == null ? Map.of() : profiles;
        this.availableCapabilities = availableCapabilities == null ? List.of() : availableCapabilities;
        this.defaultTokenBudget = defaultTokenBudget == null ? defaultTokenBudget() : defaultTokenBudget;
    }

    @Override
    public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
        log.info("[AutoAgent][context-prepare] runId={}, sessionId={}, loopIndex={}",
                context.getRunId(), context.getSessionId(), context.getLoopIndex());
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
                .build());
        ContextPlannerOutputVO plannerOutput = contextPlannerNodeService.plan(candidates,
                context.getRunId(),
                context.getAgentId(),
                contextPlannerProfile());
        ContextPlannerHandlingResult result = contextPlannerStatusHandler.handle(plannerOutput, candidates);
        log.info("[AutoAgent][context-ready] runId={}, loopIndex={}, hasStateView={}, askUser={}, failure={}",
                context.getRunId(), context.getLoopIndex(),
                result != null && result.getStateView() != null,
                result != null && result.getAskUserRequest() != null,
                result == null ? null : result.getFailure());
        return result;
    }

    @Override
    public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
        log.info("[AutoAgent][main-agent-start] runId={}, loopIndex={}",
                context.getRunId(), context.getLoopIndex());
        NodeInvocationProfileVO profile = profile(AgentComponentCodeEnumVO.MAIN_AGENT.name());
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .runId(context.getRunId())
                .agentId(context.getAgentId())
                .componentCode(AgentComponentCodeEnumVO.MAIN_AGENT.name())
                .contractVersion(firstNonBlank(profile.getContractVersion(), MAIN_AGENT_CONTRACT_VERSION))
                .promptVersion(firstNonBlank(profile.getPromptVersion(), DEFAULT_PROMPT_VERSION))
                .modelCode(profile.getModelCode())
                .temperature(profile.getTemperature())
                .maxOutputTokens(profile.getMaxOutputTokens())
                .maxRepairAttempts(profile.getMaxRepairAttempts())
                .inputView(context.getLastStateView())
                .invocationMetadata(Map.of("loopIndex", context.getLoopIndex()))
                .build());
        if (result.getTypedOutput() instanceof MainAgentActionVO action) {
            log.info("[AutoAgent][main-agent-action] runId={}, loopIndex={}, status={}, action={}",
                    context.getRunId(), context.getLoopIndex(), result.getStatus(), action.getAction());
            return action;
        }
        log.warn("[AutoAgent][main-agent-fallback] runId={}, loopIndex={}, status={}, failureCode={}, failureMessage={}",
                context.getRunId(), context.getLoopIndex(), result.getStatus(), result.getFailureCode(), result.getFailureMessage());
        return safeFailAction("MAIN_AGENT_INVOCATION_FAILED", result.getFailureMessage());
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
                    .contractVersion(AgentComponentCodeEnumVO.CONTEXT_PLANNER.name().equals(componentCode)
                            ? CONTEXT_PLANNER_CONTRACT_VERSION
                            : MAIN_AGENT_CONTRACT_VERSION)
                    .maxRepairAttempts(1)
                    .build();
        }
        return NodeInvocationProfileVO.builder()
                .componentCode(firstNonBlank(configured.getComponentCode(), componentCode))
                .modelCode(configured.getModelCode())
                .promptVersion(firstNonBlank(configured.getPromptVersion(), DEFAULT_PROMPT_VERSION))
                .contractVersion(firstNonBlank(configured.getContractVersion(), AgentComponentCodeEnumVO.CONTEXT_PLANNER.name().equals(componentCode)
                        ? CONTEXT_PLANNER_CONTRACT_VERSION
                        : MAIN_AGENT_CONTRACT_VERSION))
                .temperature(configured.getTemperature())
                .maxOutputTokens(configured.getMaxOutputTokens())
                .maxRepairAttempts(configured.getMaxRepairAttempts())
                .build();
    }

    private MainAgentActionVO safeFailAction(String failureCode, String message) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failureCode", failureCode);
        failure.put("userMessage", "抱歉，这次任务没有被安全完成。");
        failure.put("developerMessage", firstNonBlank(message, "MainAgent invocation did not produce a valid action."));
        return MainAgentActionVO.builder()
                .action(MainAgentActionTypeEnumVO.FAIL.code())
                .stateDelta(Map.of("failure", failure))
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

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}

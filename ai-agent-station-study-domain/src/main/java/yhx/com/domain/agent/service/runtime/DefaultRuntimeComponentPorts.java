package yhx.com.domain.agent.service.runtime;

import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.node.contextplanner.ContextPlannerNodeService;
import yhx.com.domain.agent.service.node.mainagent.MainAgentNodeService;
import yhx.com.domain.agent.service.context.ContextPlannerStatusHandler;
import yhx.com.domain.agent.service.context.ContextPreparationService;

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
    public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
        log.info("[AutoAgent][context-refresh] runId={}, sessionId={}, loopIndex={}",
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
                .runtimeFacts(context.getRuntimeFacts())
                .build());
        ContextPlannerHandlingResult result = contextPlannerStatusHandler.refreshWithoutPlanner(candidates);
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

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}

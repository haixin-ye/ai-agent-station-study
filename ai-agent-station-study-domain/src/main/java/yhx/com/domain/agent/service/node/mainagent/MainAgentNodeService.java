package yhx.com.domain.agent.service.node.mainagent;

import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.invocation.NodeFunctionSpecRegistry;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;
import yhx.com.domain.agent.service.runtime.RunContextEnvelopeBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class MainAgentNodeService {

    private static final String MAIN_AGENT_CONTRACT_VERSION = "main-agent-action-v2";
    private static final String DEFAULT_PROMPT_VERSION = "v2";

    private final NodeInvocationPipeline nodeInvocationPipeline;
    private final NodeInvocationProfileVO invocationProfile;
    private final RunContextEnvelopeBuilder envelopeBuilder;
    private final NodeFunctionSpecRegistry functionSpecRegistry = NodeFunctionSpecRegistry.defaultRegistry();

    public MainAgentNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        this(nodeInvocationPipeline, null);
    }

    public MainAgentNodeService(NodeInvocationPipeline nodeInvocationPipeline, NodeInvocationProfileVO invocationProfile) {
        this(nodeInvocationPipeline, invocationProfile, new RunContextEnvelopeBuilder());
    }

    public MainAgentNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                NodeInvocationProfileVO invocationProfile,
                                RunContextEnvelopeBuilder envelopeBuilder) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
        this.invocationProfile = invocationProfile;
        this.envelopeBuilder = envelopeBuilder == null ? new RunContextEnvelopeBuilder() : envelopeBuilder;
    }

    public MainAgentActionVO invoke(RuntimeExecutionContext context) {
        if (context == null || context.getRunContextState() == null
                || context.getRunContextState().getMainAgentStage() == null) {
            throw new IllegalArgumentException("MainAgent invocation requires canonical RunContextState with a stage.");
        }
        log.info("[AutoAgent][main-agent-start] runId={}, loopIndex={}",
                context.getRunId(), context.getLoopIndex());
        AutoAgentHumanLog.stage("调用主Node", context.getRunId(), "开始调用 MainAgent：loop="
                + context.getLoopIndex());
        NodeInvocationProfileVO profile = normalizedProfile();
        MainAgentStageEnumVO stage = context.getRunContextState().getMainAgentStage();
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
                .invocationMode(profile.getInvocationMode())
                .functionSpecs(functionSpecs(profile, stage))
                .inputView(envelopeBuilder.build(context))
                .invocationMetadata(Map.of("loopIndex", context.getLoopIndex(),
                        "mainAgentStage", stage.name()))
                .build());
        if (result.getTypedOutput() instanceof MainAgentActionVO action) {
            log.info("[AutoAgent][main-agent-action] runId={}, loopIndex={}, status={}, action={}",
                    context.getRunId(), context.getLoopIndex(), result.getStatus(), action.getAction());
            AutoAgentHumanLog.mainAction(context.getRunId(), context.getLoopIndex(), action);
            return action;
        }
        log.warn("[AutoAgent][main-agent-fallback] runId={}, loopIndex={}, status={}, failureCode={}, failureMessage={}",
                context.getRunId(), context.getLoopIndex(), result.getStatus(), result.getFailureCode(), result.getFailureMessage());
        AutoAgentHumanLog.stage("调用主Node", context.getRunId(), "MainAgent 调用失败：status="
                + result.getStatus() + "，failureCode=" + result.getFailureCode()
                + "，原因=" + result.getFailureMessage());
        return safeFailAction("MAIN_AGENT_INVOCATION_FAILED", result.getFailureMessage());
    }

    private NodeInvocationProfileVO normalizedProfile() {
        if (invocationProfile == null) {
            return NodeInvocationProfileVO.builder()
                    .componentCode(AgentComponentCodeEnumVO.MAIN_AGENT.name())
                    .promptVersion(DEFAULT_PROMPT_VERSION)
                    .contractVersion(MAIN_AGENT_CONTRACT_VERSION)
                    .maxRepairAttempts(1)
                    .build();
        }
        return NodeInvocationProfileVO.builder()
                .componentCode(firstNonBlank(invocationProfile.getComponentCode(), AgentComponentCodeEnumVO.MAIN_AGENT.name()))
                .modelCode(invocationProfile.getModelCode())
                .promptVersion(DEFAULT_PROMPT_VERSION)
                .contractVersion(MAIN_AGENT_CONTRACT_VERSION)
                .temperature(invocationProfile.getTemperature())
                .maxOutputTokens(invocationProfile.getMaxOutputTokens())
                .maxRepairAttempts(invocationProfile.getMaxRepairAttempts())
                .invocationMode(invocationProfile.getInvocationMode())
                .functionSpecs(invocationProfile.getFunctionSpecs())
                .build();
    }

    private List<NodeFunctionSpecVO> functionSpecs(NodeInvocationProfileVO profile, MainAgentStageEnumVO stage) {
        List<NodeFunctionSpecVO> configured = profile == null ? null : profile.getFunctionSpecs();
        return configured == null || configured.isEmpty()
                ? functionSpecRegistry.resolveMainAgent(stage)
                : functionSpecRegistry.filterMainAgent(configured, stage);
    }

    private MainAgentActionVO safeFailAction(String failureCode, String message) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failureCode", failureCode);
        failure.put("userMessage", "抱歉，这次任务没有被安全完成。请稍后重试，或调整问题后再试。");
        failure.put("developerMessage", firstNonBlank(message, "MainAgent invocation did not produce a valid action."));
        return MainAgentActionVO.builder()
                .action(MainAgentActionTypeEnumVO.FAIL.code())
                .taskUpdate(Map.of("lastDecision", "MainAgent invocation failed before a valid decision was produced."))
                .stateDelta(Map.of("failure", failure))
                .build();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}

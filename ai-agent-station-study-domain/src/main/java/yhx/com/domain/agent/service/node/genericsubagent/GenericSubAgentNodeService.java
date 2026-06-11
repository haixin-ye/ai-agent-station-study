package yhx.com.domain.agent.service.node.genericsubagent;

import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextVO;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.service.agent.GenericSubAgentNodePort;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

import java.util.LinkedHashMap;
import java.util.Map;

public class GenericSubAgentNodeService implements GenericSubAgentNodePort {

    private static final String CONTRACT_VERSION = "generic-sub-agent-action-v1";
    private static final String DEFAULT_PROMPT_VERSION = "v1";

    private final NodeInvocationPipeline nodeInvocationPipeline;
    private final NodeInvocationProfileVO invocationProfile;

    public GenericSubAgentNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        this(nodeInvocationPipeline, null);
    }

    public GenericSubAgentNodeService(NodeInvocationPipeline nodeInvocationPipeline, NodeInvocationProfileVO invocationProfile) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
        this.invocationProfile = invocationProfile;
    }

    @Override
    public SubAgentActionVO invoke(SubAgentFullContextVO fullContext) {
        if (nodeInvocationPipeline == null) {
            return fail("GenericSubAgentNodeService is not configured with NodeInvocationPipeline.");
        }
        NodeInvocationProfileVO profile = normalizedProfile();
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .runId(fullContext == null ? null : fullContext.getChildRunId())
                .componentCode(AgentComponentCodeEnumVO.GENERIC_SUB_AGENT.name())
                .contractVersion(firstNonBlank(profile.getContractVersion(), CONTRACT_VERSION))
                .promptVersion(firstNonBlank(profile.getPromptVersion(), DEFAULT_PROMPT_VERSION))
                .modelCode(profile.getModelCode())
                .temperature(profile.getTemperature())
                .maxOutputTokens(profile.getMaxOutputTokens())
                .maxRepairAttempts(profile.getMaxRepairAttempts())
                .invocationMode(profile.getInvocationMode())
                .functionSpecs(profile.getFunctionSpecs())
                .inputView(fullContext)
                .invocationMetadata(invocationMetadata(fullContext))
                .build());
        if (result != null && result.getTypedOutput() instanceof SubAgentActionVO action) {
            return action;
        }
        return fail("Generic subagent invocation failed: " + failureMessage(result));
    }

    private NodeInvocationProfileVO normalizedProfile() {
        if (invocationProfile == null) {
            return NodeInvocationProfileVO.builder()
                    .componentCode(AgentComponentCodeEnumVO.GENERIC_SUB_AGENT.name())
                    .contractVersion(CONTRACT_VERSION)
                    .promptVersion(DEFAULT_PROMPT_VERSION)
                    .maxRepairAttempts(1)
                    .build();
        }
        return NodeInvocationProfileVO.builder()
                .componentCode(firstNonBlank(invocationProfile.getComponentCode(), AgentComponentCodeEnumVO.GENERIC_SUB_AGENT.name()))
                .modelCode(invocationProfile.getModelCode())
                .contractVersion(firstNonBlank(invocationProfile.getContractVersion(), CONTRACT_VERSION))
                .promptVersion(firstNonBlank(invocationProfile.getPromptVersion(), DEFAULT_PROMPT_VERSION))
                .temperature(invocationProfile.getTemperature())
                .maxOutputTokens(invocationProfile.getMaxOutputTokens())
                .maxRepairAttempts(invocationProfile.getMaxRepairAttempts())
                .invocationMode(invocationProfile.getInvocationMode())
                .functionSpecs(invocationProfile.getFunctionSpecs())
                .build();
    }

    private Map<String, Object> invocationMetadata(SubAgentFullContextVO fullContext) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (fullContext == null) {
            return metadata;
        }
        metadata.put("parentRunId", fullContext.getParentRunId());
        metadata.put("childRunId", fullContext.getChildRunId());
        metadata.put("taskId", fullContext.getTaskId());
        return metadata;
    }

    private SubAgentActionVO fail(String message) {
        return SubAgentActionVO.builder()
                .action("FAIL")
                .actionInput(Map.of("message", message))
                .build();
    }

    private String failureMessage(NodeInvocationResult result) {
        if (result == null) {
            return "NodeInvocationResult is null.";
        }
        if (result.getFailureMessage() != null && !result.getFailureMessage().isBlank()) {
            return result.getFailureMessage();
        }
        if (result.getFailureCode() != null && !result.getFailureCode().isBlank()) {
            return result.getFailureCode();
        }
        return String.valueOf(result.getStatus());
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

package yhx.com.domain.agent.service.rag.runtime;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerifierInputVO;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

import java.util.Map;

public class RagVerifierNodeService {

    private final NodeInvocationPipeline nodeInvocationPipeline;
    private final int maxRepairAttempts;
    private final NodeInvocationProfileVO invocationProfile;

    public RagVerifierNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        this(nodeInvocationPipeline, 1, null);
    }

    public RagVerifierNodeService(NodeInvocationPipeline nodeInvocationPipeline, int maxRepairAttempts) {
        this(nodeInvocationPipeline, maxRepairAttempts, null);
    }

    public RagVerifierNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                  int maxRepairAttempts,
                                  NodeInvocationProfileVO invocationProfile) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
        this.maxRepairAttempts = Math.max(maxRepairAttempts, 0);
        this.invocationProfile = invocationProfile;
    }

    public VerificationResultVO verify(String agentId, RagVerifierInputVO input) {
        if (nodeInvocationPipeline == null) {
            return failed("CONTRACT_INVALID", "RagVerifier node invocation pipeline is not configured.");
        }
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .runId(input.getRunMeta() == null ? null : input.getRunMeta().getRunId())
                .agentId(agentId)
                .componentCode(AgentComponentCodeEnumVO.RAG_VERIFIER.name())
                .contractVersion(firstNonBlank(invocationProfile == null ? null : invocationProfile.getContractVersion(), "verification-result-v1"))
                .promptVersion(firstNonBlank(invocationProfile == null ? null : invocationProfile.getPromptVersion(), "v1"))
                .modelCode(invocationProfile == null ? null : invocationProfile.getModelCode())
                .temperature(invocationProfile == null ? null : invocationProfile.getTemperature())
                .maxOutputTokens(invocationProfile == null ? null : invocationProfile.getMaxOutputTokens())
                .inputView(input)
                .maxRepairAttempts(maxRepairAttempts)
                .invocationMetadata(Map.of("verificationType", "RAG"))
                .build());
        if ((result.getStatus() == NodeInvocationStatusEnumVO.SUCCESS
                || result.getStatus() == NodeInvocationStatusEnumVO.REPAIR_SUCCEEDED)
                && result.getTypedOutput() instanceof VerificationResultVO verificationResult) {
            return verificationResult;
        }
        return failed("CONTRACT_INVALID", result.getFailureMessage());
    }

    private VerificationResultVO failed(String failureCode, String detail) {
        return VerificationResultVO.builder()
                .status("FAILED")
                .failureCode(failureCode)
                .detail(detail)
                .build();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}

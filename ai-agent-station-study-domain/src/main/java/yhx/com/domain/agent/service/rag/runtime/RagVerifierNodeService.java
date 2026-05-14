package yhx.com.domain.agent.service.rag.runtime;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerifierInputVO;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

import java.util.Map;

public class RagVerifierNodeService {

    private final NodeInvocationPipeline nodeInvocationPipeline;
    private final int maxRepairAttempts;

    public RagVerifierNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        this(nodeInvocationPipeline, 1);
    }

    public RagVerifierNodeService(NodeInvocationPipeline nodeInvocationPipeline, int maxRepairAttempts) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
        this.maxRepairAttempts = Math.max(maxRepairAttempts, 0);
    }

    public VerificationResultVO verify(String agentId, RagVerifierInputVO input) {
        if (nodeInvocationPipeline == null) {
            return failed("CONTRACT_INVALID", "RagVerifier node invocation pipeline is not configured.");
        }
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .runId(input.getRunMeta() == null ? null : input.getRunMeta().getRunId())
                .agentId(agentId)
                .componentCode(AgentComponentCodeEnumVO.RAG_VERIFIER.name())
                .contractVersion("verification-result-v1")
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
}

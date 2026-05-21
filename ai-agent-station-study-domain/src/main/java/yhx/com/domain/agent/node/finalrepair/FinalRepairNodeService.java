package yhx.com.domain.agent.node.finalrepair;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.finalresponse.FinalRepairPromptContextVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

import java.util.Map;

public class FinalRepairNodeService {

    private final NodeInvocationPipeline nodeInvocationPipeline;
    private final NodeInvocationProfileVO invocationProfile;

    public FinalRepairNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        this(nodeInvocationPipeline, null);
    }

    public FinalRepairNodeService(NodeInvocationPipeline nodeInvocationPipeline, NodeInvocationProfileVO invocationProfile) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
        this.invocationProfile = invocationProfile;
    }

    public FinalAnswerCandidateVO repair(FinalRepairPromptContextVO context) {
        if (nodeInvocationPipeline == null) {
            return null;
        }
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .runId(context.getRunId())
                .agentId(context.getAgentId())
                .componentCode(AgentComponentCodeEnumVO.FINAL_REPAIR.name())
                .contractVersion(firstNonBlank(invocationProfile == null ? null : invocationProfile.getContractVersion(), "main-agent-action-v1"))
                .promptVersion(firstNonBlank(invocationProfile == null ? null : invocationProfile.getPromptVersion(), "v1"))
                .modelCode(invocationProfile == null ? null : invocationProfile.getModelCode())
                .temperature(invocationProfile == null ? null : invocationProfile.getTemperature())
                .maxOutputTokens(invocationProfile == null ? null : invocationProfile.getMaxOutputTokens())
                .inputView(safeInput(context))
                .maxRepairAttempts(0)
                .build());
        if (result == null || !isSuccess(result.getStatus()) || !(result.getTypedOutput() instanceof MainAgentActionVO action)) {
            return null;
        }
        if (!MainAgentActionTypeEnumVO.REPAIR_FINAL.code().equals(action.getAction())) {
            return null;
        }
        Object candidate = action.getStateDelta() == null ? null : action.getStateDelta().get("finalAnswerCandidate");
        if (candidate instanceof FinalAnswerCandidateVO finalAnswerCandidate) {
            return finalAnswerCandidate;
        }
        if (candidate instanceof Map<?, ?> map) {
            return FinalAnswerCandidateVO.builder()
                    .content(map.get("content") == null ? null : String.valueOf(map.get("content")))
                    .contentRef(map.get("contentRef") == null ? null : String.valueOf(map.get("contentRef")))
                    .format(map.get("format") == null ? null : String.valueOf(map.get("format")))
                    .build();
        }
        return null;
    }

    private Map<String, Object> safeInput(FinalRepairPromptContextVO context) {
        return Map.of(
                "runId", value(context.getRunId()),
                "loopIndex", context.getLoopIndex() == null ? 0 : context.getLoopIndex(),
                "userInput", value(context.getUserInput()),
                "failedCandidate", value(context.getFailedCandidate() == null ? null : context.getFailedCandidate().getContent()),
                "failureCode", value(context.getFailureCode()),
                "guardSummary", value(context.getGuardSummary()),
                "repairInstruction", value(context.getRepairInstruction())
        );
    }

    private boolean isSuccess(NodeInvocationStatusEnumVO status) {
        return status == NodeInvocationStatusEnumVO.SUCCESS || status == NodeInvocationStatusEnumVO.REPAIR_SUCCEEDED;
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}

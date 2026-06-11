package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionEffectStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolEvidenceCreationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationBuildResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;
import yhx.com.domain.agent.service.runtime.port.ToolActionOrchestratorPort;

public class ToolActionOrchestrator implements ToolActionOrchestratorPort {

    private final ToolInvocationRequestBuilder requestBuilder;
    private final ToolRuntime toolRuntime;
    private final ToolVerifier toolVerifier;
    private final ToolEvidenceConverter evidenceConverter;
    private final ToolTranscriptRecorder transcriptRecorder;

    public ToolActionOrchestrator(ToolInvocationRequestBuilder requestBuilder,
                                  ToolRuntime toolRuntime,
                                  ToolVerifier toolVerifier,
                                  ToolEvidenceConverter evidenceConverter,
                                  ToolTranscriptRecorder transcriptRecorder) {
        this.requestBuilder = requestBuilder;
        this.toolRuntime = toolRuntime;
        this.toolVerifier = toolVerifier;
        this.evidenceConverter = evidenceConverter;
        this.transcriptRecorder = transcriptRecorder;
    }

    @Override
    public ToolActionResultVO handleToolAction(ToolActionCommandVO command) {
        ToolInvocationBuildResultVO buildResult = requestBuilder.build(command);
        if (ToolInvocationRequestBuilder.WAITING_USER.equals(buildResult.getStatus())) {
            return ToolActionResultVO.builder()
                    .status(ToolActionStatusEnumVO.WAITING_USER)
                    .pendingInputId(buildResult.getPendingInputId())
                    .askUserRequest(buildResult.getAskUserRequest())
                    .message(buildResult.getFailureMessage())
                    .build();
        }
        if (ToolInvocationRequestBuilder.DENIED.equals(buildResult.getStatus())
                || ToolInvocationRequestBuilder.FAILED.equals(buildResult.getStatus())) {
            ToolEvidenceCreationResultVO evidence = evidenceConverter.createDenialEvidencePack(command == null ? null : command.getRunId(), buildResult);
            return ToolActionResultVO.builder()
                    .status(ToolActionStatusEnumVO.CONTINUE_LOOP)
                    .actionEffectStatus(ToolInvocationRequestBuilder.DENIED.equals(buildResult.getStatus())
                            ? ToolActionEffectStatusEnumVO.TOOL_APPROVAL_REJECTED
                            : ToolActionEffectStatusEnumVO.TOOL_NOT_RUN)
                    .evidenceIds(evidence.getEvidenceIds())
                    .evidence(evidence.getEvidence())
                    .message(buildResult.getFailureMessage())
                    .build();
        }
        transcriptRecorder.appendToolRequest(buildResult.getRequest());
        ToolInvocationResultVO invocationResult = toolRuntime.invoke(buildResult.getRequest());
        VerificationResultVO verification = toolVerifier.verify(buildResult.getRequest(), invocationResult);
        ToolEvidenceCreationResultVO evidence = evidenceConverter.createInvocationEvidencePack(command == null ? null : command.getRunId(), invocationResult);
        transcriptRecorder.appendToolResult(command == null ? null : command.getRunId(), invocationResult);
        return ToolActionResultVO.builder()
                .status(ToolActionStatusEnumVO.CONTINUE_LOOP)
                .actionEffectStatus(toolEffectStatus(invocationResult))
                .evidenceIds(evidence.getEvidenceIds())
                .evidence(evidence.getEvidence())
                .message(verification == null ? "Tool flow completed." : verification.getDetail())
                .build();
    }

    private ToolActionEffectStatusEnumVO toolEffectStatus(ToolInvocationResultVO result) {
        if (result == null || result.getStatus() == null) {
            return ToolActionEffectStatusEnumVO.TOOL_FAILED;
        }
        return result.getStatus() == ToolInvocationStatusEnumVO.SUCCESS
                ? ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED
                : ToolActionEffectStatusEnumVO.TOOL_FAILED;
    }
}

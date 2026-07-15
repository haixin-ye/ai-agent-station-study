package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.model.valobj.enums.persistence.VerificationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionEffectStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
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
        transcriptRecorder.appendToolResult(command == null ? null : command.getRunId(), invocationResult);
        if (!invocationSucceeded(invocationResult) || !verificationPassed(verification)) {
            ToolEvidenceCreationResultVO failureEvidence = evidenceConverter.createVerificationFailureEvidencePack(
                    command == null ? null : command.getRunId(), invocationResult, verification);
            return ToolActionResultVO.builder()
                    .status(ToolActionStatusEnumVO.CONTINUE_LOOP)
                    .actionEffectStatus(ToolActionEffectStatusEnumVO.TOOL_FAILED)
                    .evidenceIds(failureEvidence.getEvidenceIds())
                    .evidence(failureEvidence.getEvidence())
                    .message(verificationFailureMessage(verification))
                    .build();
        }
        ToolEvidenceCreationResultVO evidence = evidenceConverter.createVerifiedInvocationEvidencePack(
                command == null ? null : command.getRunId(), invocationResult, verification);
        return ToolActionResultVO.builder()
                .status(ToolActionStatusEnumVO.CONTINUE_LOOP)
                .actionEffectStatus(toolEffectStatus(invocationResult, verification))
                .evidenceIds(evidence.getEvidenceIds())
                .evidence(evidence.getEvidence())
                .message(verification == null ? "Tool flow completed." : verification.getDetail())
                .build();
    }

    private ToolActionEffectStatusEnumVO toolEffectStatus(ToolInvocationResultVO result,
                                                           VerificationResultVO verification) {
        if (!verificationPassed(verification) || result == null || result.getStatus() == null) {
            return ToolActionEffectStatusEnumVO.TOOL_FAILED;
        }
        return result.getStatus() == ToolInvocationStatusEnumVO.SUCCESS
                ? ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED
                : ToolActionEffectStatusEnumVO.TOOL_FAILED;
    }

    private boolean verificationPassed(VerificationResultVO verification) {
        return verification != null
                && VerificationStatusEnumVO.PASSED.code().equalsIgnoreCase(verification.getStatus());
    }

    private boolean invocationSucceeded(ToolInvocationResultVO result) {
        return result != null && result.getStatus() == ToolInvocationStatusEnumVO.SUCCESS;
    }

    private String verificationFailureMessage(VerificationResultVO verification) {
        if (verification == null) {
            return "Tool execution could not be verified because the verification result is missing.";
        }
        if (verification.getDetail() != null && !verification.getDetail().isBlank()) {
            return verification.getDetail();
        }
        return "Tool execution could not be verified: " + (verification.getFailureCode() == null
                ? "TOOL_VERIFICATION_FAILED" : verification.getFailureCode());
    }
}

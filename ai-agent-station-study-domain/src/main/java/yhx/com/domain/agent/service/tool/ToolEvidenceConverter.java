package yhx.com.domain.agent.service.tool;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.VerificationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolEvidenceCreationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationBuildResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolSchemaViolationVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ToolEvidenceConverter {

    private static final int EVIDENCE_SUMMARY_LIMIT = 500;
    private static final int SCHEMA_DIAGNOSTIC_LIMIT = 4000;
    private static final int VERIFICATION_DETAIL_LIMIT = 1000;

    private final IEvidenceRepository evidenceRepository;
    private final IPayloadRepository payloadRepository;

    public ToolEvidenceConverter(IEvidenceRepository evidenceRepository) {
        this(evidenceRepository, null);
    }

    public ToolEvidenceConverter(IEvidenceRepository evidenceRepository,
                                 IPayloadRepository payloadRepository) {
        this.evidenceRepository = evidenceRepository;
        this.payloadRepository = payloadRepository;
    }

    public List<String> createDenialEvidence(String runId, ToolInvocationBuildResultVO buildResult) {
        return createDenialEvidencePack(runId, buildResult).getEvidenceIds();
    }

    public ToolEvidenceCreationResultVO createDenialEvidencePack(String runId, ToolInvocationBuildResultVO buildResult) {
        if (buildResult == null) {
            return emptyResult();
        }
        String summary = "Tool action did not run: " + safe(firstNonBlank(buildResult.getFailureMessage(), buildResult.getFailureCode(), "permission denied"));
        String boundedSummary = bounded(summary);
        String failureCode = firstNonBlank(buildResult.getFailureCode(), "TOOL_ACTION_NOT_RUN");
        return fromSavedEvidence(save(runId, buildResult.getToolCallId(), boundedSummary,
                        null, null, VerificationStatusEnumVO.SKIPPED.code(), failureCode), "TOOL",
                buildResult.getToolCallId(), boundedSummary, null, null, null, null, null,
                VerificationStatusEnumVO.SKIPPED.code(), failureCode);
    }

    public ToolEvidenceCreationResultVO createVerifiedInvocationEvidencePack(String runId,
                                                                              ToolInvocationResultVO result,
                                                                              VerificationResultVO verification) {
        if (verification == null
                || !VerificationStatusEnumVO.PASSED.code().equalsIgnoreCase(verification.getStatus())) {
            throw new IllegalArgumentException("Verified invocation evidence requires PASSED verification.");
        }
        if (result == null || result.getStatus() != ToolInvocationStatusEnumVO.SUCCESS) {
            throw new IllegalArgumentException("Verified invocation evidence requires a successful invocation.");
        }
        return createInvocationEvidencePack(runId, result, verification.getStatus(), verification.getFailureCode());
    }

    private ToolEvidenceCreationResultVO createInvocationEvidencePack(String runId,
                                                                       ToolInvocationResultVO result,
                                                                       String verificationStatus,
                                                                       String verificationFailureCode) {
        if (result == null) {
            return emptyResult();
        }
        String summary;
        if (result.getStatus() != null && "SUCCESS".equals(result.getStatus().name())) {
            summary = "Tool action succeeded: " + safe(firstNonBlank(result.getResultSummary(), "receiptRef=" + result.getReceiptRef()));
        } else {
            summary = "Tool action failed: " + safe(firstNonBlank(result.getFailureMessage(), result.getFailureCode(), "unknown tool failure"));
        }
        String boundedSummary = bounded(summary);
        String schemaDiagnostics = schemaDiagnostics(result);
        String evidenceContent = firstNonBlank(schemaDiagnostics, result.getResultContent());
        String evidenceFormat = schemaDiagnostics == null ? result.getResultContentFormat() : "JSON";
        Integer totalChars;
        Long totalBytes;
        if (schemaDiagnostics == null) {
            totalChars = result.getResultTotalChars();
            totalBytes = result.getResultTotalBytes();
        } else {
            totalChars = schemaDiagnostics.length();
            totalBytes = (long) schemaDiagnostics.getBytes(StandardCharsets.UTF_8).length;
        }
        String contentRef = result.getResultContentRef();
        if (contentRef == null || contentRef.isBlank()) {
            contentRef = savePayload(evidenceContent, evidenceFormat);
        }
        String failureCode = firstNonBlank(verificationFailureCode, result.getFailureCode());
        return fromSavedEvidence(save(runId, result.getToolCallId(), boundedSummary,
                        contentRef, evidenceFormat, verificationStatus, failureCode),
                "TOOL", result.getToolCallId(), boundedSummary,
                evidenceContent, contentRef, evidenceFormat, totalChars, totalBytes,
                verificationStatus, failureCode);
    }

    public ToolEvidenceCreationResultVO createVerificationFailureEvidencePack(String runId,
                                                                               ToolInvocationResultVO invocationResult,
                                                                               VerificationResultVO verification) {
        String toolCallId = invocationResult == null ? null : invocationResult.getToolCallId();
        String verificationStatus = verification == null
                ? "MISSING" : firstNonBlank(verification.getStatus(), VerificationStatusEnumVO.FAILED.code());
        String failureCode = firstNonBlank(
                verification == null ? null : verification.getFailureCode(),
                invocationResult == null ? null : invocationResult.getFailureCode(),
                verification == null ? "TOOL_VERIFICATION_MISSING" : "TOOL_VERIFICATION_FAILED");
        String summary = bounded("Tool execution could not be verified: " + failureCode);
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("verificationStatus", verificationStatus);
        diagnostics.put("failureCode", failureCode);
        diagnostics.put("detail", boundedDiagnostic(verification == null
                ? "Tool verification result is missing."
                : verification.getDetail()));
        diagnostics.put("invocationStatus", invocationResult == null || invocationResult.getStatus() == null
                ? null : invocationResult.getStatus().name());
        diagnostics.put("invocationFailureCode", invocationResult == null ? null : invocationResult.getFailureCode());
        appendBoundedSchemaDiagnostics(diagnostics, invocationResult);
        String content = JSON.toJSONString(diagnostics);
        String contentRef = savePayload(content, "JSON");
        return fromSavedEvidence(save(runId, toolCallId, summary, contentRef, "JSON", verificationStatus, failureCode),
                "TOOL", toolCallId, summary, content, contentRef, "JSON", content.length(),
                (long) content.getBytes(StandardCharsets.UTF_8).length, verificationStatus, failureCode);
    }

    private void appendBoundedSchemaDiagnostics(Map<String, Object> diagnostics,
                                                ToolInvocationResultVO result) {
        if (result == null || result.getSchemaViolations() == null || result.getSchemaViolations().isEmpty()) {
            return;
        }
        diagnostics.put("schemaHash", result.getSchemaHash());
        diagnostics.put("schemaViolationCount", result.getSchemaViolations().size());
        List<ToolSchemaViolationVO> boundedViolations = new ArrayList<>();
        for (ToolSchemaViolationVO violation : result.getSchemaViolations()) {
            boundedViolations.add(violation);
            diagnostics.put("schemaViolations", boundedViolations);
            diagnostics.put("schemaDiagnosticsTruncated",
                    boundedViolations.size() < result.getSchemaViolations().size());
            if (JSON.toJSONString(diagnostics).length() > SCHEMA_DIAGNOSTIC_LIMIT) {
                boundedViolations.remove(boundedViolations.size() - 1);
                break;
            }
        }
        diagnostics.put("schemaViolations", boundedViolations);
        diagnostics.put("schemaDiagnosticsTruncated",
                boundedViolations.size() < result.getSchemaViolations().size());
    }

    private String schemaDiagnostics(ToolInvocationResultVO result) {
        if (result == null || result.getSchemaViolations() == null || result.getSchemaViolations().isEmpty()) {
            return null;
        }
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("failureCode", result.getFailureCode());
        diagnostics.put("schemaHash", result.getSchemaHash());
        diagnostics.put("violationCount", result.getSchemaViolations().size());
        List<ToolSchemaViolationVO> boundedViolations = new ArrayList<>();
        for (ToolSchemaViolationVO violation : result.getSchemaViolations()) {
            boundedViolations.add(violation);
            diagnostics.put("violations", boundedViolations);
            diagnostics.put("truncated", boundedViolations.size() < result.getSchemaViolations().size());
            if (JSON.toJSONString(diagnostics).length() > SCHEMA_DIAGNOSTIC_LIMIT) {
                boundedViolations.remove(boundedViolations.size() - 1);
                break;
            }
        }
        diagnostics.put("violations", boundedViolations);
        diagnostics.put("truncated", boundedViolations.size() < result.getSchemaViolations().size());
        return JSON.toJSONString(diagnostics);
    }

    private String save(String runId,
                        String toolCallId,
                        String summary,
                        String contentRef,
                        String contentFormat,
                        String verificationStatus,
                        String failureCode) {
        return evidenceRepository.saveEvidence(AgentEvidenceEntity.builder()
                .runId(runId)
                .evidenceType("TOOL")
                .sourceRef(toolCallId)
                .summary(bounded(summary))
                .contentRef(contentRef)
                .contentFormat(contentFormat)
                .verificationStatus(verificationStatus)
                .failureCode(failureCode)
                .confidence(BigDecimal.ONE)
                .usedByFinal(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String savePayload(String content, String contentFormat) {
        if (payloadRepository == null || content == null || content.isBlank()) {
            return null;
        }
        PayloadTypeEnumVO payloadType = "JSON".equalsIgnoreCase(contentFormat)
                ? PayloadTypeEnumVO.JSON : PayloadTypeEnumVO.TEXT;
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(payloadType)
                .content(content)
                .build());
    }

    private String bounded(String value) {
        if (value == null || value.length() <= EVIDENCE_SUMMARY_LIMIT) {
            return value;
        }
        String suffix = "... (" + value.length() + " chars)";
        if (suffix.length() >= EVIDENCE_SUMMARY_LIMIT) {
            return value.substring(0, EVIDENCE_SUMMARY_LIMIT);
        }
        return value.substring(0, EVIDENCE_SUMMARY_LIMIT - suffix.length()) + suffix;
    }

    private String boundedDiagnostic(String value) {
        if (value == null || value.length() <= VERIFICATION_DETAIL_LIMIT) {
            return value;
        }
        return value.substring(0, VERIFICATION_DETAIL_LIMIT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ToolEvidenceCreationResultVO fromSavedEvidence(String evidenceId,
                                                           String evidenceType,
                                                           String sourceRef,
                                                           String summary,
                                                           String content,
                                                           String contentRef,
                                                           String contentFormat,
                                                           Integer totalChars,
                                                           Long totalBytes,
                                                           String verificationStatus,
                                                           String failureCode) {
        if (evidenceId == null || evidenceId.isBlank()) {
            return emptyResult();
        }
        MaterializedEvidenceVO evidence = MaterializedEvidenceVO.builder()
                .evidenceId(evidenceId)
                .evidenceType(evidenceType)
                .sourceRef(sourceRef)
                .summary(summary)
                .boundedSnippet(firstNonBlank(content, summary))
                .content(content)
                .contentRef(contentRef)
                .contentFormat(firstNonBlank(contentFormat, content == null ? null : "TEXT"))
                .truncated(false)
                .totalChars(totalChars)
                .totalBytes(totalBytes)
                .metadata(evidenceMetadata(verificationStatus, failureCode))
                .build();
        return ToolEvidenceCreationResultVO.builder()
                .evidenceIds(List.of(evidenceId))
                .evidence(List.of(evidence))
                .build();
    }

    private Map<String, Object> evidenceMetadata(String verificationStatus, String failureCode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (verificationStatus != null && !verificationStatus.isBlank()) {
            metadata.put("verificationStatus", verificationStatus);
        }
        if (failureCode != null && !failureCode.isBlank()) {
            metadata.put("failureCode", failureCode);
        }
        return metadata.isEmpty() ? null : metadata;
    }

    private ToolEvidenceCreationResultVO emptyResult() {
        return ToolEvidenceCreationResultVO.builder()
                .evidenceIds(List.of())
                .evidence(List.of())
                .build();
    }
}

package yhx.com.domain.agent.service.tool;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
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

    private final IEvidenceRepository evidenceRepository;

    public ToolEvidenceConverter(IEvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
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
        return fromSavedEvidence(save(runId, buildResult.getToolCallId(), boundedSummary), "TOOL",
                buildResult.getToolCallId(), boundedSummary, null, null, null, null, null);
    }

    public List<String> createInvocationEvidence(String runId, ToolInvocationResultVO result) {
        return createInvocationEvidencePack(runId, result).getEvidenceIds();
    }

    public ToolEvidenceCreationResultVO createInvocationEvidencePack(String runId, ToolInvocationResultVO result) {
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
        return fromSavedEvidence(save(runId, result.getToolCallId(), boundedSummary), "TOOL", result.getToolCallId(), boundedSummary,
                evidenceContent, result.getResultContentRef(), evidenceFormat, totalChars, totalBytes);
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

    private String save(String runId, String toolCallId, String summary) {
        return evidenceRepository.saveEvidence(AgentEvidenceEntity.builder()
                .runId(runId)
                .evidenceType("TOOL")
                .sourceRef(toolCallId)
                .summary(bounded(summary))
                .confidence(BigDecimal.ONE)
                .usedByFinal(false)
                .createdAt(LocalDateTime.now())
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
                                                           Long totalBytes) {
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
                .build();
        return ToolEvidenceCreationResultVO.builder()
                .evidenceIds(List.of(evidenceId))
                .evidence(List.of(evidence))
                .build();
    }

    private ToolEvidenceCreationResultVO emptyResult() {
        return ToolEvidenceCreationResultVO.builder()
                .evidenceIds(List.of())
                .evidence(List.of())
                .build();
    }
}

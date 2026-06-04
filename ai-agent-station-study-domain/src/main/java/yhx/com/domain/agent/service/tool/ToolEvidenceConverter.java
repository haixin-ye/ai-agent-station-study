package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.tool.ToolEvidenceCreationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationBuildResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ToolEvidenceConverter {

    private static final int EVIDENCE_SUMMARY_LIMIT = 500;

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
        return fromSavedEvidence(save(runId, buildResult.getToolCallId(), summary), "TOOL", buildResult.getToolCallId(), summary,
                null, null, null, null, null);
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
        return fromSavedEvidence(save(runId, result.getToolCallId(), boundedSummary), "TOOL", result.getToolCallId(), boundedSummary,
                result.getResultContent(), result.getResultContentRef(), result.getResultContentFormat(),
                result.getResultTotalChars(), result.getResultTotalBytes());
    }

    private String save(String runId, String toolCallId, String summary) {
        return evidenceRepository.saveEvidence(AgentEvidenceEntity.builder()
                .runId(runId)
                .evidenceType("TOOL")
                .sourceRef(toolCallId)
                .summary(summary)
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

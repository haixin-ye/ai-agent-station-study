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
        return fromSavedEvidence(save(runId, buildResult.getToolCallId(), summary), "TOOL", buildResult.getToolCallId(), summary);
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
        return fromSavedEvidence(save(runId, result.getToolCallId(), summary), "TOOL", result.getToolCallId(), summary);
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

    private ToolEvidenceCreationResultVO fromSavedEvidence(String evidenceId, String evidenceType, String sourceRef, String summary) {
        if (evidenceId == null || evidenceId.isBlank()) {
            return emptyResult();
        }
        MaterializedEvidenceVO evidence = MaterializedEvidenceVO.builder()
                .evidenceId(evidenceId)
                .evidenceType(evidenceType)
                .sourceRef(sourceRef)
                .summary(summary)
                .boundedSnippet(summary)
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

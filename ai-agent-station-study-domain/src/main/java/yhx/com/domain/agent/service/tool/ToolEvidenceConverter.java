package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationBuildResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ToolEvidenceConverter {

    private final IEvidenceRepository evidenceRepository;

    public ToolEvidenceConverter(IEvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    public List<String> createDenialEvidence(String runId, ToolInvocationBuildResultVO buildResult) {
        if (buildResult == null) {
            return List.of();
        }
        String summary = "Tool action did not run: " + safe(firstNonBlank(buildResult.getFailureMessage(), buildResult.getFailureCode(), "permission denied"));
        return List.of(save(runId, buildResult.getToolCallId(), summary));
    }

    public List<String> createInvocationEvidence(String runId, ToolInvocationResultVO result) {
        if (result == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        String summary;
        if (result.getStatus() != null && "SUCCESS".equals(result.getStatus().name())) {
            summary = "Tool action succeeded. receiptRef=" + safe(result.getReceiptRef());
        } else {
            summary = "Tool action failed: " + safe(firstNonBlank(result.getFailureMessage(), result.getFailureCode(), "unknown tool failure"));
        }
        ids.add(save(runId, result.getToolCallId(), summary));
        return ids;
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
}

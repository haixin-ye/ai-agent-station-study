package yhx.com.domain.agent.service.evidence;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;

public class ToolReceiptSummarizer {

    private static final int MAX_SNIPPET_CHARS = 300;

    public MaterializedEvidenceVO summarizeToolEvidence(AgentEvidenceEntity evidence, AgentPayloadEntity receiptPayload) {
        String receipt = receiptPayload == null ? null : receiptPayload.getContent();
        String bounded = summarizeReceipt(receipt);
        return MaterializedEvidenceVO.builder()
                .evidenceId(evidence.getEvidenceId())
                .evidenceType(evidence.getEvidenceType())
                .sourceRef(evidence.getSourceRef())
                .summary(evidence.getSummary())
                .boundedSnippet(bounded)
                .build();
    }

    private String summarizeReceipt(String receipt) {
        if (receipt == null || receipt.isBlank()) {
            return null;
        }
        try {
            JSONObject json = JSON.parseObject(receipt);
            JSONObject safe = new JSONObject();
            copyIfPresent(json, safe, "status");
            copyIfPresent(json, safe, "url");
            copyIfPresent(json, safe, "id");
            copyIfPresent(json, safe, "error");
            copyIfPresent(json, safe, "message");
            return truncate(safe.toJSONString());
        } catch (Exception ignored) {
            return truncate(receipt.replaceAll("(?i)(cookie|authorization|token|password|secret)\\s*[:=]\\s*[^,\\s]+", "$1:<redacted>"));
        }
    }

    private void copyIfPresent(JSONObject source, JSONObject target, String field) {
        if (source.containsKey(field)) {
            target.put(field, source.get(field));
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_SNIPPET_CHARS) {
            return value;
        }
        return value.substring(0, MAX_SNIPPET_CHARS);
    }
}

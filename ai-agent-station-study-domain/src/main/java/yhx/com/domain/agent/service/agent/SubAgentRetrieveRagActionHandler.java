package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RagRuntimeStatusEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeResultVO;
import yhx.com.domain.agent.service.runtime.port.RagRuntimePort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SubAgentRetrieveRagActionHandler implements SubAgentActionHandler {

    private final ParentChildRunRegistry registry;
    private final RagRuntimePort ragRuntimePort;

    public SubAgentRetrieveRagActionHandler(ParentChildRunRegistry registry, RagRuntimePort ragRuntimePort) {
        this.registry = registry == null ? new ParentChildRunRegistry() : registry;
        this.ragRuntimePort = ragRuntimePort;
    }

    @Override
    public String actionType() {
        return SubAgentActionTypeEnumVO.RETRIEVE_RAG.code();
    }

    @Override
    public SubAgentActionHandlerResultVO handle(SubAgentActionExecutionContextVO context, SubAgentActionVO action) {
        ParentChildRunRelationVO relation = context == null ? null : context.getRelation();
        if (ragRuntimePort == null) {
            return failed(relation, "RAG retrieval is unavailable for generic subagent.");
        }
        Map<String, Object> request = action == null || action.getActionInput() == null ? Map.of() : action.getActionInput();
        String query = stringValue(request, "query");
        if (isBlank(query)) {
            return failed(relation, "Generic subagent RETRIEVE_RAG requires actionInput.query.");
        }
        RagRuntimeResultVO result = ragRuntimePort.retrieve(RagRuntimeCommandVO.builder()
                .runId(relation == null ? null : relation.getChildRunId())
                .sessionId(context == null || context.getCommand() == null ? null : context.getCommand().getSessionId())
                .userId(context == null || context.getCommand() == null ? null : context.getCommand().getUserId())
                .loopIndex(context == null ? null : context.getLoopIndex())
                .query(query)
                .knowledgeName(stringValue(request, "knowledgeName"))
                .options(request)
                .build());
        if (result == null || result.getStatus() == null) {
            return failed(relation, "RAG retrieval failed for generic subagent.");
        }
        if (result.getStatus() == RagRuntimeStatusEnumVO.SUCCESS || result.getStatus() == RagRuntimeStatusEnumVO.NO_HIT) {
            return SubAgentActionHandlerResultVO.builder()
                    .action(actionType())
                    .terminal(false)
                    .message(result.getMessage())
                    .resultSnapshot(resultSnapshot(result))
                    .build();
        }
        return failed(relation, result.getMessage() == null ? "RAG retrieval failed for generic subagent." : result.getMessage());
    }

    private SubAgentActionHandlerResultVO failed(ParentChildRunRelationVO relation, String failureMessage) {
        if (relation != null) {
            registry.markFailed(relation.getChildRunId(), failureMessage);
        }
        return SubAgentActionHandlerResultVO.builder()
                .action(actionType())
                .terminal(true)
                .status(ChildAgentRunStatusEnumVO.FAILED)
                .failureMessage(failureMessage)
                .message(failureMessage)
                .resultSnapshot(Map.of(
                        "action", actionType(),
                        "status", ChildAgentRunStatusEnumVO.FAILED.code(),
                        "failureMessage", failureMessage))
                .build();
    }

    private Map<String, Object> resultSnapshot(RagRuntimeResultVO result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", result.getStatus() == null ? null : result.getStatus().code());
        snapshot.put("message", result.getMessage());
        snapshot.put("evidenceIds", result.getEvidenceIds() == null ? List.of() : result.getEvidenceIds());
        snapshot.put("evidence", result.getEvidence() == null ? List.of() : result.getEvidence().stream()
                .map(this::evidenceSnapshot)
                .toList());
        return snapshot;
    }

    private Map<String, Object> evidenceSnapshot(MaterializedEvidenceVO evidence) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (evidence == null) {
            return snapshot;
        }
        snapshot.put("evidenceId", evidence.getEvidenceId());
        snapshot.put("summary", evidence.getSummary());
        snapshot.put("content", evidence.getContent());
        snapshot.put("boundedSnippet", evidence.getBoundedSnippet());
        snapshot.put("sourceRef", evidence.getSourceRef());
        return snapshot;
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

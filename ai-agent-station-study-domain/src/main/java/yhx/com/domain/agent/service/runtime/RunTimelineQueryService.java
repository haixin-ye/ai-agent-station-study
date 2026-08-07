package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionEffectStatusEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.LoopRuntimeOutcomeVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RunTimelineQueryService {

    public RunLoopRecordVO findSuccessfulToolCall(RunContextStateVO state, Map<String, Object> toolIntent) {
        List<RunLoopRecordVO> records = timeline(state);
        for (int index = records.size() - 1; index >= 0; index--) {
            RunLoopRecordVO record = records.get(index);
            if (isAction(record, MainAgentActionTypeEnumVO.CALL_TOOL)
                    && sameToolIntent(toolIntent(record), toolIntent)
                    && effectStatus(record, ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name())) {
                return record;
            }
        }
        return null;
    }

    public boolean wasToolCallRejected(RunContextStateVO state, Map<String, Object> toolIntent) {
        return timeline(state).stream()
                .filter(record -> isAction(record, MainAgentActionTypeEnumVO.CALL_TOOL))
                .filter(record -> sameToolIntent(toolIntent(record), toolIntent))
                .map(RunLoopRecordVO::getUserInteraction)
                .filter(Objects::nonNull)
                .anyMatch(this::isRejectedDecision);
    }

    public boolean hasAnsweredQuestion(RunContextStateVO state, String question) {
        String normalizedQuestion = normalize(question);
        if (normalizedQuestion.isEmpty()) {
            return false;
        }
        return userClarifications(state).stream()
                .filter(clarification -> normalizedQuestion.equals(normalize(clarification.getQuestion())))
                .anyMatch(this::hasUsableAnswer);
    }

    public boolean hasRagNoHit(RunContextStateVO state, String query) {
        String normalizedQuery = normalize(query);
        return timeline(state).stream()
                .filter(record -> isAction(record, MainAgentActionTypeEnumVO.RETRIEVE_RAG))
                .filter(record -> normalizedQuery.equals(normalize(ragQuery(record))))
                .anyMatch(record -> effectStatus(record, "NO_HIT"));
    }

    public List<String> verifiedToolCallRefs(RunContextStateVO state) {
        Set<String> refs = new LinkedHashSet<>();
        for (RunLoopRecordVO record : timeline(state)) {
            if (!isAction(record, MainAgentActionTypeEnumVO.CALL_TOOL)
                    || !effectStatus(record, ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name())) {
                continue;
            }
            LoopRuntimeOutcomeVO outcome = record.getRuntimeOutcome();
            if (outcome != null && outcome.getEvidenceRefs() != null) {
                outcome.getEvidenceRefs().stream().filter(this::notBlank).forEach(refs::add);
            }
        }
        initialEvidence(state).stream()
                .filter(this::verificationPassed)
                .map(MaterializedEvidenceVO::getSourceRef)
                .filter(this::notBlank)
                .forEach(refs::add);
        return List.copyOf(refs);
    }

    public List<UserClarificationVO> userClarifications(RunContextStateVO state) {
        List<UserClarificationVO> clarifications = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        if (state != null && state.getBaseContext() != null) {
            for (UserClarificationVO clarification : defaultList(state.getBaseContext().getUserClarifications())) {
                appendClarification(clarifications, keys, clarification);
            }
        }
        for (RunLoopRecordVO record : timeline(state)) {
            Map<String, Object> interaction = record.getUserInteraction();
            if (interaction == null || !hasUsableAnswer(interaction)) {
                continue;
            }
            Map<String, Object> answer = map(interaction.get("answer"));
            appendClarification(clarifications, keys, UserClarificationVO.builder()
                    .sourceComponent(string(interaction.get("sourceComponent")))
                    .pendingId(string(answer.get("pendingId")))
                    .question(question(record))
                    .answerType(string(answer.get("answerType")))
                    .selectedOptionId(string(answer.get("selectedOptionId")))
                    .value(answer.get("value"))
                    .freeText(string(answer.get("freeText")))
                    .metadata(map(answer.get("metadata")))
                    .build());
        }
        return clarifications;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toolIntent(RunLoopRecordVO record) {
        Object value = stateDelta(record).get("toolIntent");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    public boolean sameToolIntent(Map<String, Object> left, Map<String, Object> right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.get("capabilityCode"), right.get("capabilityCode"))
                && compatibleOptionalRoutingHint(left.get("mcpServerCode"), right.get("mcpServerCode"))
                && Objects.equals(left.get("toolName"), right.get("toolName"))
                && Objects.equals(left.get("arguments"), right.get("arguments"));
    }

    private boolean compatibleOptionalRoutingHint(Object left, Object right) {
        String leftValue = string(left);
        String rightValue = string(right);
        return !notBlank(leftValue) || !notBlank(rightValue) || Objects.equals(leftValue, rightValue);
    }

    private boolean isAction(RunLoopRecordVO record, MainAgentActionTypeEnumVO actionType) {
        return record != null && record.getMainOutput() != null
                && actionType.code().equals(record.getMainOutput().getAction());
    }

    private boolean effectStatus(RunLoopRecordVO record, String expected) {
        Map<String, Object> details = record == null || record.getRuntimeOutcome() == null
                ? null : record.getRuntimeOutcome().getDetails();
        return details != null && expected.equals(string(details.get("effectStatus")));
    }

    private boolean isRejectedDecision(Map<String, Object> interaction) {
        Map<String, Object> answer = map(interaction.get("answer"));
        Map<String, Object> value = map(answer.get("value"));
        return "REJECTED".equalsIgnoreCase(string(value.get("decision")));
    }

    private boolean hasUsableAnswer(Map<String, Object> interaction) {
        Map<String, Object> answer = map(interaction.get("answer"));
        if (answer.isEmpty()) {
            return false;
        }
        return answer.get("value") != null || notBlank(string(answer.get("freeText")));
    }

    private boolean hasUsableAnswer(UserClarificationVO clarification) {
        return clarification != null
                && (clarification.getValue() != null || notBlank(clarification.getFreeText()));
    }

    private void appendClarification(List<UserClarificationVO> target,
                                     Set<String> keys,
                                     UserClarificationVO clarification) {
        if (clarification == null || !hasUsableAnswer(clarification)) {
            return;
        }
        String key = clarificationKey(clarification);
        if (keys.add(key)) {
            target.add(clarification);
        }
    }

    private String clarificationKey(UserClarificationVO clarification) {
        if (notBlank(clarification.getPendingId())) {
            return "pending:" + clarification.getPendingId();
        }
        return "answer:" + normalize(clarification.getQuestion())
                + "|" + string(clarification.getAnswerType())
                + "|" + string(clarification.getSelectedOptionId())
                + "|" + string(clarification.getFreeText())
                + "|" + JSON.toJSONString(clarification.getValue());
    }

    private String question(RunLoopRecordVO record) {
        Map<String, Object> request = map(stateDelta(record).get("askUserRequest"));
        return string(request.get("question"));
    }

    private String ragQuery(RunLoopRecordVO record) {
        Map<String, Object> request = map(stateDelta(record).get("ragRequest"));
        return string(request.get("query"));
    }

    private Map<String, Object> stateDelta(RunLoopRecordVO record) {
        return record == null || record.getMainOutput() == null
                ? Map.of() : map(record.getMainOutput().getStateDelta());
    }

    private List<MaterializedEvidenceVO> initialEvidence(RunContextStateVO state) {
        if (state == null || state.getBaseContext() == null
                || state.getBaseContext().getSelectedSessionContext() == null
                || state.getBaseContext().getSelectedSessionContext().getEvidencePack() == null) {
            return List.of();
        }
        return state.getBaseContext().getSelectedSessionContext().getEvidencePack();
    }

    private boolean verificationPassed(MaterializedEvidenceVO evidence) {
        if (evidence == null || evidence.getMetadata() == null) {
            return false;
        }
        return "PASSED".equalsIgnoreCase(string(evidence.getMetadata().get("verificationStatus")));
    }

    private List<RunLoopRecordVO> timeline(RunContextStateVO state) {
        return state == null || state.getLoopTimeline() == null ? List.of() : state.getLoopTimeline();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value == null) {
            return Map.of();
        }
        return JSON.parseObject(JSON.toJSONString(value), Map.class);
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }
}

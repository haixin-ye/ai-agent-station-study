package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.FailureVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;

import java.util.List;
import java.util.Map;

public class MainActionHandlerSupport {

    protected final RuntimeFailureFactory failureFactory;
    protected final DeveloperTraceRecorder traceRecorder;

    public MainActionHandlerSupport(RuntimeFailureFactory failureFactory, DeveloperTraceRecorder traceRecorder) {
        this.failureFactory = failureFactory == null ? new RuntimeFailureFactory() : failureFactory;
        this.traceRecorder = traceRecorder;
    }

    protected Map<String, Object> requireStateDelta(MainAgentActionVO action) {
        if (action == null || action.getStateDelta() == null) {
            throw new IllegalArgumentException("stateDelta is required.");
        }
        return action.getStateDelta();
    }

    protected FinalAnswerCandidateVO requireFinalAnswerCandidate(MainAgentActionVO action) {
        FinalAnswerCandidateVO candidate = optionalFinalAnswerCandidate(action);
        if (candidate == null || isBlank(candidate.getContent()) && isBlank(candidate.getContentRef())) {
            throw new IllegalArgumentException("stateDelta.finalAnswerCandidate.content or contentRef is required.");
        }
        return candidate;
    }

    protected FinalAnswerCandidateVO optionalFinalAnswerCandidate(MainAgentActionVO action) {
        Object value = requireStateDelta(action).get("finalAnswerCandidate");
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        Object content = firstNonNull(map.get("content"), map.get("text"));
        return FinalAnswerCandidateVO.builder()
                .content(content == null ? null : String.valueOf(content))
                .contentRef(stringValue(map, "contentRef"))
                .format(stringValue(map, "format"))
                .build();
    }

    protected Map<String, Object> requireArtifactDraft(MainAgentActionVO action) {
        return requireMap(action, "artifactDraft");
    }

    protected Map<String, Object> requireArtifactPatch(MainAgentActionVO action) {
        return requireMap(action, "artifactPatch");
    }

    protected AskUserRequestVO requireAskUserRequest(MainAgentActionVO action) {
        Map<String, Object> request = requireMap(action, "askUserRequest");
        return AskUserRequestVO.builder()
                .question(stringValue(request, "question"))
                .inputMode(stringValue(request, "inputMode"))
                .allowFreeText(Boolean.TRUE.equals(request.get("allowFreeText")))
                .options(listValue(request, "options"))
                .build();
    }

    protected Map<String, Object> requireRagRequest(MainAgentActionVO action) {
        return requireMap(action, "ragRequest");
    }

    protected Map<String, Object> requireToolIntent(MainAgentActionVO action) {
        return requireMap(action, "toolIntent");
    }

    protected FailureVO requireFailure(MainAgentActionVO action) {
        Map<String, Object> failure = requireMap(action, "failure");
        String userMessage = stringValue(failure, "userMessage");
        if (isBlank(userMessage)) {
            userMessage = stringValue(failure, "message");
        }
        if (isBlank(userMessage)) {
            throw new IllegalArgumentException("stateDelta.failure.userMessage is required.");
        }
        return FailureVO.builder()
                .failureCode(stringValue(failure, "failureCode"))
                .message(userMessage)
                .build();
    }

    protected MainActionHandlerResult safeFailure(RuntimeExecutionContext context,
                                                  RuntimeFailureCodeEnumVO code,
                                                  String userMessage,
                                                  String developerMessage) {
        if (traceRecorder != null && context != null) {
            traceRecorder.error(context.getRunId(), context.getLoopIndex(), code, developerMessage, null);
        }
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .safeFailure(failureFactory.create(code, RuntimePhaseEnumVO.HANDLING_ACTION, developerMessage, true))
                .message(developerMessage)
                .build();
    }

    protected MainActionHandlerResult validationFailure(RuntimeExecutionContext context, String developerMessage) {
        return safeFailure(context, RuntimeFailureCodeEnumVO.MAIN_ACTION_CONTRACT_FAILED,
                "The action could not be handled safely.", developerMessage);
    }

    protected boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    protected String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    protected Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> listValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> requireMap(MainAgentActionVO action, String fieldName) {
        Object value = requireStateDelta(action).get(fieldName);
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("stateDelta." + fieldName + " is required.");
        }
        return (Map<String, Object>) rawMap;
    }
}

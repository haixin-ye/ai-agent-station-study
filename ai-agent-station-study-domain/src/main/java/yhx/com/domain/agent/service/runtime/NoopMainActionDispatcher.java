package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.util.Map;

public class NoopMainActionDispatcher implements MainActionDispatcher {

    private final boolean testMode;
    private final RuntimeFailureFactory failureFactory;

    public NoopMainActionDispatcher(boolean testMode, RuntimeFailureFactory failureFactory) {
        this.testMode = testMode;
        this.failureFactory = failureFactory;
    }

    @Override
    public MainActionHandlerResult dispatch(RuntimeExecutionContext context, MainAgentActionVO action) {
        MainAgentActionTypeEnumVO actionType = action == null ? null : MainAgentActionTypeEnumVO.ofCode(action.getAction()).orElse(null);
        if (actionType == MainAgentActionTypeEnumVO.FINAL && testMode) {
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.COMPLETED)
                    .nextPhase(RuntimePhaseEnumVO.VERIFYING_FINAL)
                    .finalAnswerCandidate(extractFinalAnswerCandidate(action.getStateDelta()))
                    .message("Noop dispatcher accepted FINAL in test mode.")
                    .build();
        }
        if (actionType == MainAgentActionTypeEnumVO.ASK_USER) {
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.WAITING_USER)
                    .nextPhase(RuntimePhaseEnumVO.WAITING_USER)
                    .askUserRequest(extractAskUserRequest(action.getStateDelta()))
                    .message("Noop dispatcher routed ASK_USER to Runtime pending input.")
                    .build();
        }
        if (actionType == MainAgentActionTypeEnumVO.CONTINUE) {
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                    .nextPhase(RuntimePhaseEnumVO.PREPARING_CONTEXT)
                    .message("Noop dispatcher continued loop.")
                    .build();
        }
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .safeFailure(failureFactory.actionHandlerUnavailable(action == null ? null : action.getAction()))
                .message("Action handler not implemented in Phase 5.")
                .build();
    }

    @SuppressWarnings("unchecked")
    private FinalAnswerCandidateVO extractFinalAnswerCandidate(Map<String, Object> stateDelta) {
        Object candidate = stateDelta == null ? null : stateDelta.get("finalAnswerCandidate");
        if (!(candidate instanceof Map<?, ?> rawMap)) {
            return FinalAnswerCandidateVO.builder().build();
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        Object content = map.get("content");
        if (content == null) {
            content = map.get("text");
        }
        return FinalAnswerCandidateVO.builder()
                .content(content == null ? null : String.valueOf(content))
                .contentRef(stringValue(map, "contentRef"))
                .format(stringValue(map, "format"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private AskUserRequestVO extractAskUserRequest(Map<String, Object> stateDelta) {
        Object request = stateDelta == null ? null : stateDelta.get("askUserRequest");
        if (!(request instanceof Map<?, ?> rawMap)) {
            return AskUserRequestVO.builder().build();
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        return AskUserRequestVO.builder()
                .question(stringValue(map, "question"))
                .inputMode(stringValue(map, "inputMode"))
                .allowFreeText(Boolean.TRUE.equals(map.get("allowFreeText")))
                .options(map.get("options") instanceof java.util.List<?> list ? (java.util.List<Map<String, Object>>) list : java.util.List.of())
                .build();
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }
}

package yhx.com.domain.agent.service.agent;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.interaction.SubAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;

import java.util.LinkedHashMap;
import java.util.Map;

public class SubAgentAskUserActionHandler implements SubAgentActionHandler {

    public static final String HANDLER_CODE = SubAgentPendingInputHandler.HANDLER_CODE;

    private final ParentChildRunRegistry registry;
    private final UserInteractionManager userInteractionManager;

    public SubAgentAskUserActionHandler(ParentChildRunRegistry registry,
                                        UserInteractionManager userInteractionManager) {
        this.registry = registry == null ? new ParentChildRunRegistry() : registry;
        this.userInteractionManager = userInteractionManager;
    }

    @Override
    public String actionType() {
        return SubAgentActionTypeEnumVO.ASK_USER.code();
    }

    @Override
    public SubAgentActionHandlerResultVO handle(SubAgentActionExecutionContextVO context, SubAgentActionVO action) {
        ParentChildRunRelationVO relation = context == null ? null : context.getRelation();
        if (userInteractionManager == null) {
            return failed(relation, "ASK_USER is unavailable for generic subagent.");
        }
        AskUserRequestVO request = askUserRequest(action);
        if (request == null || isBlank(request.getQuestion()) || isBlank(request.getInputMode())) {
            return failed(relation, "Generic subagent ASK_USER requires askUserRequest.question and inputMode.");
        }
        PendingInputCreateResult pending = userInteractionManager.createPendingInput(PendingInputCreateCommand.builder()
                .runId(relation == null ? null : relation.getParentRunId())
                .sessionId(context == null || context.getCommand() == null ? null : context.getCommand().getSessionId())
                .sourceComponent(HANDLER_CODE)
                .pendingType(PendingInputTypeEnumVO.CHILD_AGENT_QUESTION.code())
                .askUserRequest(request)
                .runtimeContext(parentRuntimeContext(context))
                .continuation(ContinuationCheckpointVO.builder()
                        .handler(HANDLER_CODE)
                        .resumePhase(RuntimePhaseEnumVO.WAITING_CHILDREN)
                        .sourceComponent(HANDLER_CODE)
                        .relatedRunId(relation == null ? null : relation.getParentRunId())
                        .relatedLoopIndex(context == null ? null : context.getLoopIndex())
                        .expectedAnswerValueType(request.getInputMode())
                        .payload(checkpointPayload(context))
                        .build())
                .build());
        if (pending == null || !Boolean.TRUE.equals(pending.getCreated())) {
            return failed(relation, pending == null ? "ASK_USER pending input creation failed." : pending.getFailureMessage());
        }
        registry.markWaitingUser(relation.getChildRunId(), pending.getPendingInputId());
        return SubAgentActionHandlerResultVO.builder()
                .action(actionType())
                .terminal(true)
                .status(ChildAgentRunStatusEnumVO.WAITING_USER)
                .pendingInputId(pending.getPendingInputId())
                .askUserRequest(request)
                .message("Generic subagent requested user input.")
                .resultSnapshot(Map.of(
                        "action", actionType(),
                        "status", ChildAgentRunStatusEnumVO.WAITING_USER.code(),
                        "pendingInputId", pending.getPendingInputId(),
                        "question", request.getQuestion()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private AskUserRequestVO askUserRequest(SubAgentActionVO action) {
        Map<String, Object> input = action == null ? null : action.getActionInput();
        if (input == null || input.isEmpty()) {
            return null;
        }
        Object request = input.get("askUserRequest");
        if (request instanceof AskUserRequestVO vo) {
            return vo;
        }
        if (request instanceof Map<?, ?> map) {
            return JSON.parseObject(JSON.toJSONString(map), AskUserRequestVO.class);
        }
        return JSON.parseObject(JSON.toJSONString(input), AskUserRequestVO.class);
    }

    private Map<String, Object> checkpointPayload(SubAgentActionExecutionContextVO context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        ParentChildRunRelationVO relation = context == null ? null : context.getRelation();
        if (relation != null) {
            payload.put("parentRunId", relation.getParentRunId());
            payload.put("childRunId", relation.getChildRunId());
            payload.put("taskId", relation.getTaskId());
        }
        if (context != null && context.getCommand() != null) {
            payload.put("effectiveCapabilities", context.getCommand().getEffectiveCapabilityCodes());
            payload.put("initialContext", context.getCommand().getInitialContext());
        }
        return payload;
    }

    private RuntimeExecutionContext parentRuntimeContext(SubAgentActionExecutionContextVO context) {
        if (context != null && context.getCommand() != null && context.getCommand().getParentRuntimeContext() != null) {
            return context.getCommand().getParentRuntimeContext();
        }
        return null;
    }

    private SubAgentActionHandlerResultVO failed(ParentChildRunRelationVO relation, String failureMessage) {
        String safeMessage = isBlank(failureMessage) ? "Generic subagent ASK_USER failed." : failureMessage;
        if (relation != null) {
            registry.markFailed(relation.getChildRunId(), safeMessage);
        }
        return SubAgentActionHandlerResultVO.builder()
                .action(actionType())
                .terminal(true)
                .status(ChildAgentRunStatusEnumVO.FAILED)
                .failureMessage(safeMessage)
                .message(safeMessage)
                .resultSnapshot(Map.of(
                        "action", actionType(),
                        "status", ChildAgentRunStatusEnumVO.FAILED.code(),
                        "failureMessage", safeMessage))
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

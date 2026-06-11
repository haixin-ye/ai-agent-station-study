package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.runtime.port.RagRuntimePort;
import yhx.com.domain.agent.service.runtime.port.ToolActionOrchestratorPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SubAgentActionDispatcher {

    private final Map<String, SubAgentActionHandler> handlers;

    public SubAgentActionDispatcher(List<SubAgentActionHandler> handlers) {
        this.handlers = new LinkedHashMap<>();
        if (handlers != null) {
            handlers.stream()
                    .filter(handler -> handler != null && handler.actionType() != null)
                    .forEach(handler -> this.handlers.put(handler.actionType(), handler));
        }
    }

    public static SubAgentActionDispatcher defaultDispatcher(ParentChildRunRegistry registry) {
        return runtimeDispatcher(registry, null, null, null);
    }

    public static SubAgentActionDispatcher runtimeDispatcher(ParentChildRunRegistry registry,
                                                            ToolActionOrchestratorPort toolActionOrchestratorPort,
                                                            RagRuntimePort ragRuntimePort,
                                                            UserInteractionManager userInteractionManager) {
        return new SubAgentActionDispatcher(List.of(
                new ContinueSubAgentActionHandler(),
                new CommitSubAgentActionHandler(registry),
                new FailSubAgentActionHandler(registry),
                new SubAgentCallToolActionHandler(registry, toolActionOrchestratorPort),
                new SubAgentRetrieveRagActionHandler(registry, ragRuntimePort),
                new SubAgentAskUserActionHandler(registry, userInteractionManager)
        ));
    }

    public SubAgentActionHandlerResultVO dispatch(ParentChildRunRelationVO relation, SubAgentActionVO action) {
        return dispatch(SubAgentActionExecutionContextVO.builder()
                .relation(relation)
                .build(), action);
    }

    public SubAgentActionHandlerResultVO dispatch(SubAgentActionExecutionContextVO context, SubAgentActionVO action) {
        String actionType = action == null ? null : action.getAction();
        SubAgentActionHandler handler = handlers.get(actionType);
        if (handler == null) {
            return SubAgentActionHandlerResultVO.builder()
                    .action(actionType)
                    .terminal(true)
                    .status(ChildAgentRunStatusEnumVO.FAILED)
                    .failureMessage("Generic subagent action routing is not implemented for " + safeAction(actionType) + ".")
                    .message("Generic subagent action routing is not implemented for " + safeAction(actionType) + ".")
                    .resultSnapshot(Map.of(
                            "action", safeAction(actionType),
                            "status", ChildAgentRunStatusEnumVO.FAILED.code(),
                            "failureMessage", "Generic subagent action routing is not implemented for " + safeAction(actionType) + "."))
                    .build();
        }
        return handler.handle(context, action);
    }

    private String safeAction(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}

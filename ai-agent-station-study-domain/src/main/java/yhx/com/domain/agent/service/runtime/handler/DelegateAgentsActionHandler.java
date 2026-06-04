package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.agent.AgentDispatchResultVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentsRequestVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.agent.AgentDispatchRuntime;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;

import java.util.List;
import java.util.Map;

public class DelegateAgentsActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final AgentDispatchRuntime dispatchRuntime;

    public DelegateAgentsActionHandler(AgentDispatchRuntime dispatchRuntime,
                                       RuntimeFailureFactory failureFactory,
                                       DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.dispatchRuntime = dispatchRuntime;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.DELEGATE_AGENTS;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        if (dispatchRuntime == null) {
            return validationFailure(context, "AgentDispatchRuntime is not configured.");
        }
        DelegateAgentsRequestVO request = toRequest(requireMap(action, "delegateAgentsRequest"));
        AgentDispatchResultVO dispatchResult = dispatchRuntime.dispatch(context.getRunId(), request);
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.WAITING_CHILDREN)
                .nextPhase(RuntimePhaseEnumVO.WAITING_CHILDREN)
                .actionEffect(ActionEffectVO.builder()
                        .action(MainAgentActionTypeEnumVO.DELEGATE_AGENTS.code())
                        .status(MainActionHandlerStatusEnumVO.WAITING_CHILDREN.code())
                        .message("Delegated child agent work.")
                        .resultSnapshot(Map.of(
                                "waitMode", dispatchResult.getWaitMode(),
                                "childRunIds", dispatchResult.getChildRunIds()))
                        .build())
                .message("Parent run is waiting for delegated child agents.")
                .build();
    }

    private DelegateAgentsRequestVO toRequest(Map<String, Object> raw) {
        return DelegateAgentsRequestVO.builder()
                .waitMode(stringValue(raw, "waitMode"))
                .tasks(toTasks(listValue(raw, "tasks")))
                .build();
    }

    private List<DelegateAgentTaskVO> toTasks(List<Map<String, Object>> rawTasks) {
        return rawTasks.stream()
                .map(task -> DelegateAgentTaskVO.builder()
                        .taskId(stringValue(task, "taskId"))
                        .name(stringValue(task, "name"))
                        .objective(stringValue(task, "objective"))
                        .boundary(stringValue(task, "boundary"))
                        .requiredOutput(stringValue(task, "requiredOutput"))
                        .requestedCapabilities(toStringList(task.get("requestedCapabilities")))
                        .parentContext(mapValue(task.get("parentContext")))
                        .build())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (Object item : iterable) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }
}

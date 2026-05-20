package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.PlanStateVO;
import yhx.com.domain.agent.model.valobj.runtime.PlanStepVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.PlanStatePort;

import java.util.List;
import java.util.Map;

public class PlanActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final PlanStatePort planStatePort;

    public PlanActionHandler(PlanStatePort planStatePort,
                             RuntimeFailureFactory failureFactory,
                             DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.planStatePort = planStatePort;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.PLAN;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        try {
            Map<String, Object> draft = requireMap(action, "planDraft");
            String goal = stringValue(draft, "goal");
            if (isBlank(goal)) {
                throw new IllegalArgumentException("planDraft.goal is required.");
            }
            if (planStatePort == null) {
                return safeFailure(context, RuntimeFailureCodeEnumVO.ACTION_HANDLER_UNAVAILABLE,
                        "Plan persistence is unavailable.", "PlanStatePort is not configured.");
            }
            planStatePort.savePlan(context.getRunId(), PlanStateVO.builder()
                    .goal(goal)
                    .steps(toSteps(listValue(draft, "steps")))
                    .metadata(draft)
                    .build());
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                    .nextPhase(RuntimePhaseEnumVO.CALLING_MAIN_NODE)
                    .message("Plan state saved.")
                    .build();
        } catch (IllegalArgumentException e) {
            return validationFailure(context, e.getMessage());
        }
    }

    private List<PlanStepVO> toSteps(List<Map<String, Object>> rawSteps) {
        return rawSteps.stream().map(step -> PlanStepVO.builder()
                .stepId(stringValue(step, "stepId"))
                .title(stringValue(step, "title"))
                .status(stringValue(step, "status"))
                .note(stringValue(step, "note"))
                .build()).toList();
    }
}

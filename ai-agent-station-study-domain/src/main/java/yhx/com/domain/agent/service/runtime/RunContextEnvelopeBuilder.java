package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.valobj.runtime.RunContextEnvelopeVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunPayloadWorkingSetVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunContextEnvelopeBuilder {

    private final IPayloadRepository payloadRepository;
    private final RunPayloadProjectionPolicy payloadProjectionPolicy;

    public RunContextEnvelopeBuilder() {
        this(null, new RunPayloadProjectionPolicy());
    }

    public RunContextEnvelopeBuilder(IPayloadRepository payloadRepository,
                                     RunPayloadProjectionPolicy payloadProjectionPolicy) {
        this.payloadRepository = payloadRepository;
        this.payloadProjectionPolicy = payloadProjectionPolicy == null
                ? new RunPayloadProjectionPolicy() : payloadProjectionPolicy;
    }

    public RunContextEnvelopeVO build(RuntimeExecutionContext context) {
        if (context == null || context.getRunContextState() == null) {
            throw new IllegalArgumentException("Run context state is required for MainAgent v2.");
        }
        RunContextStateVO state = context.getRunContextState();
        Map<String, Object> control = new LinkedHashMap<>();
        control.put("mainAgentStage", state.getMainAgentStage().name());
        control.put("loopIndex", context.getLoopIndex());
        control.put("maxLoop", context.getMaxLoop());
        control.put("remainingLoops", remainingLoops(context));
        if (state.getBaseContext() != null && state.getBaseContext().getSelectedSessionContext() != null) {
            control.put("availableCapabilities", state.getBaseContext().getSelectedSessionContext().getAvailableCapabilities());
            control.put("tokenBudget", state.getBaseContext().getSelectedSessionContext().getTokenBudget());
        }
        RunPayloadWorkingSetVO workingSet = payloadProjectionPolicy.build(state, payloadRepository);
        return RunContextEnvelopeVO.builder()
                .runBaseContext(state.getBaseContext())
                .taskLedger(state.getTaskLedger())
                .loopTimeline(state.getLoopTimeline() == null ? List.of() : List.copyOf(state.getLoopTimeline()))
                .runtimeControl(control)
                .payloadManifest(workingSet.getPayloadManifest())
                .activePayloads(workingSet.getActivePayloads())
                .build();
    }

    private int remainingLoops(RuntimeExecutionContext context) {
        int max = context.getMaxLoop() == null ? 0 : context.getMaxLoop();
        int current = context.getLoopIndex() == null ? 0 : context.getLoopIndex();
        return Math.max(0, max - current);
    }
}

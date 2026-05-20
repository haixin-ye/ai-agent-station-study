package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RagRuntimeStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.RagRuntimePort;

import java.util.Map;

public class RetrieveRagActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final IRunRepository runRepository;
    private final RagRuntimePort ragRuntimePort;
    private final RunEventPublisher eventPublisher;

    public RetrieveRagActionHandler(IRunRepository runRepository,
                                    RagRuntimePort ragRuntimePort,
                                    RunEventPublisher eventPublisher,
                                    RuntimeFailureFactory failureFactory,
                                    DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.runRepository = runRepository;
        this.ragRuntimePort = ragRuntimePort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.RETRIEVE_RAG;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        try {
            Map<String, Object> ragRequest = requireRagRequest(action);
            String query = stringValue(ragRequest, "query");
            if (isBlank(query)) {
                throw new IllegalArgumentException("ragRequest.query is required.");
            }
            runRepository.markRagWasUsed(context.getRunId());
            if (ragRuntimePort == null) {
                return safeFailure(context, RuntimeFailureCodeEnumVO.ACTION_HANDLER_UNAVAILABLE,
                        "RAG retrieval is unavailable.", "RagRuntimePort is not configured.");
            }
            RagRuntimeResultVO result = ragRuntimePort.retrieve(RagRuntimeCommandVO.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .userId(context.getUserId())
                    .loopIndex(context.getLoopIndex())
                    .query(query)
                    .knowledgeName(stringValue(ragRequest, "knowledgeName"))
                    .options(ragRequest)
                    .build());
            if (result != null && (result.getStatus() == RagRuntimeStatusEnumVO.SUCCESS || result.getStatus() == RagRuntimeStatusEnumVO.NO_HIT)) {
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                        .nextPhase(RuntimePhaseEnumVO.PREPARING_CONTEXT)
                        .createdEvidenceIds(result.getEvidenceIds())
                        .message(result.getMessage())
                        .build();
            }
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.FAILED)
                    .nextPhase(RuntimePhaseEnumVO.FAILED)
                    .safeFailure(result == null ? null : result.getSafeFailure())
                    .message(result == null ? "RAG runtime returned null result." : result.getMessage())
                    .build();
        } catch (IllegalArgumentException e) {
            return validationFailure(context, e.getMessage());
        } catch (Exception e) {
            return safeFailure(context,
                    RuntimeFailureCodeEnumVO.RAG_RETRIEVAL_FAILED,
                    "RAG retrieval failed.",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}

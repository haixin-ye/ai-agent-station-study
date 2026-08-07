package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RagRuntimeStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RunTimelineQueryService;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.RagRuntimePort;

import java.util.List;
import java.util.Map;

public class RetrieveRagActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final IRunRepository runRepository;
    private final RagRuntimePort ragRuntimePort;
    private final RunTimelineQueryService timelineQueryService = new RunTimelineQueryService();

    public RetrieveRagActionHandler(IRunRepository runRepository,
                                    RagRuntimePort ragRuntimePort,
                                    RunEventPublisher eventPublisher,
                                    RuntimeFailureFactory failureFactory,
                                    DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.runRepository = runRepository;
        this.ragRuntimePort = ragRuntimePort;
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
            if (timelineQueryService.hasRagNoHit(context.getRunContextState(), query)) {
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.WAITING_USER)
                        .nextPhase(RuntimePhaseEnumVO.WAITING_USER)
                        .askUserRequest(repeatedNoHitAskUserRequest(query))
                        .actionEffect(effect(context, RagRuntimeStatusEnumVO.NO_HIT.name(),
                                "Repeated RAG no-hit query was skipped.", List.of(), List.of()))
                        .message("Repeated RAG no-hit query was skipped before retrieval.")
                        .build();
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
                    .options(ragRequest)
                    .build());
            if (result != null && (result.getStatus() == RagRuntimeStatusEnumVO.SUCCESS
                    || result.getStatus() == RagRuntimeStatusEnumVO.NO_HIT)) {
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                        .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                        .createdEvidenceIds(result.getEvidenceIds())
                        .actionEffect(effect(context, result.getStatus().name(), result.getMessage(),
                                result.getEvidenceIds(), result.getEvidence()))
                        .message(result.getMessage())
                        .build();
            }
            String message = result == null ? "RAG runtime returned null result." : result.getMessage();
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.FAILED)
                    .nextPhase(RuntimePhaseEnumVO.FAILED)
                    .safeFailure(result == null ? null : result.getSafeFailure())
                        .actionEffect(effect(context,
                            result == null ? "FAILED" : String.valueOf(result.getStatus()), message, List.of(), List.of()))
                    .message(message)
                    .build();
        } catch (IllegalArgumentException e) {
            return validationFailure(context, e.getMessage());
        } catch (Exception e) {
            return safeFailure(context, RuntimeFailureCodeEnumVO.RAG_RETRIEVAL_FAILED,
                    "RAG retrieval failed.", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private ActionEffectVO effect(RuntimeExecutionContext context,
                                  String status,
                                  String message,
                                  List<String> evidenceIds,
                                  List<yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO> evidence) {
        return ActionEffectVO.builder()
                .action(MainAgentActionTypeEnumVO.RETRIEVE_RAG.code())
                .status(status)
                .message(message)
                .loopIndex(context == null ? null : context.getLoopIndex())
                .createdEvidenceIds(evidenceIds == null ? List.of() : evidenceIds)
                .build();
    }

    private AskUserRequestVO repeatedNoHitAskUserRequest(String query) {
        return AskUserRequestVO.builder()
                .question("The same knowledge-base query already returned no usable evidence. How should I continue?")
                .inputMode("SINGLE_CHOICE_OR_FREE_TEXT")
                .allowFreeText(true)
                .options(List.of(
                        Map.of("optionId", "answer_without_rag", "label", "Answer without RAG",
                                "value", Map.of("decision", "ANSWER_WITHOUT_RAG", "query", query)),
                        Map.of("optionId", "change_or_upload", "label", "Change knowledge source",
                                "value", Map.of("decision", "CHANGE_QUERY_OR_UPLOAD", "query", query))))
                .build();
    }
}

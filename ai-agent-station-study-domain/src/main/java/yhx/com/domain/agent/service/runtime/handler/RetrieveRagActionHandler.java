package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.PreviousLoopOutcomeVO;
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
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.RagRuntimePort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RetrieveRagActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private static final String PREVIOUS_LOOP_OUTCOME = "previousLoopOutcome";

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
            if (isRepeatedNoHitQuery(context, query)) {
                PreviousLoopOutcomeVO outcome = recordOutcome(context, RagRuntimeStatusEnumVO.NO_HIT.name(), query,
                        "The same RAG query already returned no usable evidence.", List.of());
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.WAITING_USER)
                        .nextPhase(RuntimePhaseEnumVO.WAITING_USER)
                        .askUserRequest(repeatedNoHitAskUserRequest(query))
                        .actionEffect(effect(context, MainAgentActionTypeEnumVO.RETRIEVE_RAG.code(), RagRuntimeStatusEnumVO.NO_HIT.name(),
                                "Repeated RAG no-hit query blocked before retrieval.", List.of(), outcome))
                        .message("Repeated RAG no-hit query blocked before retrieval.")
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
            if (result != null && (result.getStatus() == RagRuntimeStatusEnumVO.SUCCESS || result.getStatus() == RagRuntimeStatusEnumVO.NO_HIT)) {
                PreviousLoopOutcomeVO outcome = recordOutcome(context, result.getStatus().name(), query, result.getMessage(), result.getEvidenceIds());
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                        .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                        .createdEvidenceIds(result.getEvidenceIds())
                        .actionEffect(effect(context, MainAgentActionTypeEnumVO.RETRIEVE_RAG.code(), result.getStatus().name(),
                                result.getMessage(), result.getEvidenceIds(), outcome))
                        .message(result.getMessage())
                        .build();
            }
            PreviousLoopOutcomeVO outcome = recordOutcome(context, result == null ? "FAILED" : String.valueOf(result.getStatus()), query,
                    result == null ? "RAG runtime returned null result." : result.getMessage(), List.of());
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.FAILED)
                    .nextPhase(RuntimePhaseEnumVO.FAILED)
                    .safeFailure(result == null ? null : result.getSafeFailure())
                    .actionEffect(effect(context, MainAgentActionTypeEnumVO.RETRIEVE_RAG.code(),
                            result == null ? "FAILED" : String.valueOf(result.getStatus()),
                            result == null ? "RAG runtime returned null result." : result.getMessage(), List.of(), outcome))
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

    private boolean isRepeatedNoHitQuery(RuntimeExecutionContext context, String query) {
        PreviousLoopOutcomeVO outcome = previousLoopOutcome(context);
        return outcome != null
                && MainAgentActionTypeEnumVO.RETRIEVE_RAG.code().equals(outcome.getAction())
                && RagRuntimeStatusEnumVO.NO_HIT.name().equals(outcome.getStatus())
                && normalizeQuery(query).equals(normalizeQuery(outcome.getQuery()));
    }

    @SuppressWarnings("unchecked")
    private PreviousLoopOutcomeVO previousLoopOutcome(RuntimeExecutionContext context) {
        if (context == null || context.getRuntimeFacts() == null) {
            return null;
        }
        Object value = context.getRuntimeFacts().get(PREVIOUS_LOOP_OUTCOME);
        if (value instanceof PreviousLoopOutcomeVO outcome) {
            return outcome;
        }
        if (value instanceof Map<?, ?> map) {
            Object evidenceIds = map.get("createdEvidenceIds");
            return PreviousLoopOutcomeVO.builder()
                    .action(stringValue((Map<String, Object>) map, "action"))
                    .status(stringValue((Map<String, Object>) map, "status"))
                    .query(stringValue((Map<String, Object>) map, "query"))
                    .message(stringValue((Map<String, Object>) map, "message"))
                    .createdEvidenceIds(evidenceIds instanceof List<?> list
                            ? (List<String>) list.stream().map(String::valueOf).toList()
                            : List.of())
                    .build();
        }
        return null;
    }

    private PreviousLoopOutcomeVO recordOutcome(RuntimeExecutionContext context,
                                                String status,
                                                String query,
                                                String message,
                                                List<String> evidenceIds) {
        PreviousLoopOutcomeVO outcome = PreviousLoopOutcomeVO.builder()
                .action(MainAgentActionTypeEnumVO.RETRIEVE_RAG.code())
                .status(status)
                .query(query)
                .message(message)
                .createdEvidenceIds(evidenceIds == null ? List.of() : evidenceIds)
                .loopIndex(context == null ? null : context.getLoopIndex())
                .build();
        if (context == null) {
            return outcome;
        }
        if (context.getRuntimeFacts() == null) {
            context.setRuntimeFacts(new LinkedHashMap<>());
        }
        context.getRuntimeFacts().put(PREVIOUS_LOOP_OUTCOME, outcome);
        return outcome;
    }

    private ActionEffectVO effect(RuntimeExecutionContext context,
                                  String action,
                                  String status,
                                  String message,
                                  List<String> evidenceIds,
                                  PreviousLoopOutcomeVO outcome) {
        return ActionEffectVO.builder()
                .action(action)
                .status(status)
                .message(message)
                .loopIndex(context == null ? null : context.getLoopIndex())
                .createdEvidenceIds(evidenceIds == null ? List.of() : evidenceIds)
                .previousLoopOutcome(outcome)
                .build();
    }

    private AskUserRequestVO repeatedNoHitAskUserRequest(String query) {
        return AskUserRequestVO.builder()
                .question("刚才已经用同一个查询检索知识库，但没有找到匹配内容。你希望我怎么继续？")
                .inputMode("SINGLE_CHOICE_OR_FREE_TEXT")
                .allowFreeText(true)
                .options(List.of(
                        Map.of(
                                "optionId", "answer_without_rag",
                                "label", "直接回答",
                                "value", Map.of("decision", "ANSWER_WITHOUT_RAG", "query", query)),
                        Map.of(
                                "optionId", "change_or_upload",
                                "label", "更换知识来源",
                                "value", Map.of("decision", "CHANGE_QUERY_OR_UPLOAD", "query", query))
                ))
                .build();
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.replaceAll("\\s+", "").trim().toLowerCase();
    }
}

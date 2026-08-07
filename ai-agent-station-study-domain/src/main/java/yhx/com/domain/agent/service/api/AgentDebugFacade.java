package yhx.com.domain.agent.service.api;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunContextRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunContextEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunLoopEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.observability.AgentObservabilityLoopVO;
import yhx.com.domain.agent.model.valobj.observability.AgentObservabilitySnapshotVO;
import yhx.com.domain.agent.service.debug.DebugAccessPolicy;
import yhx.com.domain.agent.service.debug.DebugPayloadPreviewPolicy;
import yhx.com.domain.agent.service.runtime.RunOrphanRecoveryService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentDebugFacade {

    private final IEventTraceRepository eventTraceRepository;
    private final IEvidenceRepository evidenceRepository;
    private final IToolRepository toolRepository;
    private final IPayloadRepository payloadRepository;
    private final DebugAccessPolicy debugAccessPolicy;
    private final DebugPayloadPreviewPolicy debugPayloadPreviewPolicy;
    private final IRunRepository runRepository;
    private final IRunContextRepository runContextRepository;
    private final RunOrphanRecoveryService runOrphanRecoveryService;

    public AgentDebugFacade(IEventTraceRepository eventTraceRepository,
                            IEvidenceRepository evidenceRepository,
                            IToolRepository toolRepository,
                            IPayloadRepository payloadRepository,
                            DebugAccessPolicy debugAccessPolicy,
                            DebugPayloadPreviewPolicy debugPayloadPreviewPolicy) {
        this(eventTraceRepository, evidenceRepository, toolRepository, payloadRepository,
                debugAccessPolicy, debugPayloadPreviewPolicy, null, null, null);
    }

    public AgentDebugFacade(IEventTraceRepository eventTraceRepository,
                            IEvidenceRepository evidenceRepository,
                            IToolRepository toolRepository,
                            IPayloadRepository payloadRepository,
                            DebugAccessPolicy debugAccessPolicy,
                            DebugPayloadPreviewPolicy debugPayloadPreviewPolicy,
                            IRunRepository runRepository,
                            IRunContextRepository runContextRepository) {
        this(eventTraceRepository, evidenceRepository, toolRepository, payloadRepository,
                debugAccessPolicy, debugPayloadPreviewPolicy, runRepository, runContextRepository, null);
    }

    public AgentDebugFacade(IEventTraceRepository eventTraceRepository,
                            IEvidenceRepository evidenceRepository,
                            IToolRepository toolRepository,
                            IPayloadRepository payloadRepository,
                            DebugAccessPolicy debugAccessPolicy,
                            DebugPayloadPreviewPolicy debugPayloadPreviewPolicy,
                            IRunRepository runRepository,
                            IRunContextRepository runContextRepository,
                            RunOrphanRecoveryService runOrphanRecoveryService) {
        this.eventTraceRepository = eventTraceRepository;
        this.evidenceRepository = evidenceRepository;
        this.toolRepository = toolRepository;
        this.payloadRepository = payloadRepository;
        this.debugAccessPolicy = debugAccessPolicy;
        this.debugPayloadPreviewPolicy = debugPayloadPreviewPolicy;
        this.runRepository = runRepository;
        this.runContextRepository = runContextRepository;
        this.runOrphanRecoveryService = runOrphanRecoveryService;
    }

    public List<AgentRunTraceEntity> listTraces(String runId, int limit) {
        debugAccessPolicy.requireDebugApiEnabled();
        return eventTraceRepository.listDebugTrace(runId, normalizedLimit(limit));
    }

    public List<AgentRunTraceEntity> listTracesAfter(String runId, long lastSeq, int limit) {
        debugAccessPolicy.requireDebugApiEnabled();
        List<AgentRunTraceEntity> traces = eventTraceRepository.listDebugTraceAfter(runId,
                Math.max(0L, lastSeq), normalizedLimit(limit));
        return traces == null ? List.of() : traces;
    }

    public List<AgentEvidenceEntity> listEvidence(String runId) {
        debugAccessPolicy.requireDebugApiEnabled();
        return evidenceRepository.listRunEvidence(runId);
    }

    public List<ToolCallEntity> listToolCalls(String runId) {
        debugAccessPolicy.requireDebugApiEnabled();
        return toolRepository.listRunToolCalls(runId, 100);
    }

    public Optional<AgentPayloadEntity> findPayload(String payloadId) {
        debugAccessPolicy.requirePayloadPreviewEnabled();
        return payloadRepository.findPayload(payloadId)
                .map(debugPayloadPreviewPolicy::applyPreviewPolicy);
    }

    public AgentObservabilitySnapshotVO loadStudio(String runId) {
        debugAccessPolicy.requireDebugApiEnabled();
        AgentRunEntity run = runRepository == null ? null
                : (runOrphanRecoveryService == null
                ? runRepository.findRun(runId)
                : runOrphanRecoveryService.recoverIfOrphaned(runId)).orElse(null);
        List<AgentRunTraceEntity> traces = eventTraceRepository.listDebugTrace(runId, 500);
        Map<String, AgentPayloadEntity> payloads = loadTracePayloads(traces);
        Map<String, Map<String, Object>> traceDetails = parseTraceDetails(payloads);
        List<AgentEvidenceEntity> evidence = evidenceRepository.listRunEvidence(runId);
        List<ToolCallEntity> toolCalls = toolRepository.listRunToolCalls(runId, 100);
        toolCalls.forEach(tool -> {
            loadPayload(payloads, tool.getArgumentsRef());
            loadPayload(payloads, tool.getReceiptRef());
        });

        Map<String, Object> context = new LinkedHashMap<>();
        List<AgentObservabilityLoopVO> loops = new ArrayList<>();
        if (runContextRepository != null) {
            runContextRepository.findContext(runId).ifPresentOrElse(entity -> {
                loadPayload(payloads, entity.getBaseContextRef());
                loadPayload(payloads, entity.getTaskLedgerRef());
                loadPayload(payloads, entity.getRuntimeControlRef());
                runContextRepository.listLoops(runId).forEach(loop -> loadPayload(payloads, loop.getRecordRef()));
                context.putAll(contextDetails(entity));
                loops.addAll(loopDetails(runId, context, traceDetails, traces, toolCalls));
            }, () -> context.put("available", false));
        } else {
            context.put("available", false);
        }

        return AgentObservabilitySnapshotVO.builder()
                .header(header(runId, run))
                .status(run == null || run.getStatus() == null ? null : run.getStatus().code())
                .currentPhase(run == null || run.getPhase() == null ? null : run.getPhase().code())
                .context(context)
                .loops(loops)
                .graphNodes(buildGraphNodes(context, loops, traceDetails, traces, run))
                .traces(traces)
                .payloads(payloads)
                .evidence(evidence)
                .toolCalls(toolCalls)
                .lastSeq(traces.stream().map(AgentRunTraceEntity::getSeq).filter(java.util.Objects::nonNull)
                        .max(Long::compareTo).orElse(0L))
                .build();
    }

    private Map<String, Object> header(String runId, AgentRunEntity run) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("runId", runId);
        if (run != null) {
            header.put("sessionId", run.getSessionId());
            header.put("userId", run.getUserId());
            header.put("agentId", run.getAgentId());
            header.put("startedAt", run.getCreatedAt());
            header.put("updatedAt", run.getUpdatedAt());
            header.put("failureCode", run.getFailureCode());
        }
        return header;
    }

    private Map<String, Object> contextDetails(AgentRunContextEntity entity) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("available", true);
        context.put("schemaVersion", entity.getSchemaVersion());
        context.put("contextVersion", entity.getContextVersion());
        context.put("mainAgentStage", entity.getMainAgentStage() == null ? null : entity.getMainAgentStage().name());
        context.put("baseContext", readPayloadMap(entity.getBaseContextRef()));
        context.put("taskLedger", readPayloadMap(entity.getTaskLedgerRef()));
        context.put("runtimeControl", readPayloadMap(entity.getRuntimeControlRef()));
        Map<String, Object> sourceRefs = new LinkedHashMap<>();
        sourceRefs.put("baseContext", entity.getBaseContextRef());
        sourceRefs.put("taskLedger", entity.getTaskLedgerRef());
        sourceRefs.put("runtimeControl", entity.getRuntimeControlRef());
        context.put("sourceRefs", sourceRefs);
        return context;
    }

    private List<AgentObservabilityLoopVO> loopDetails(String runId,
                                                        Map<String, Object> runContextDetails,
                                                        Map<String, Map<String, Object>> traceDetails,
                                                        List<AgentRunTraceEntity> traces,
                                                        List<ToolCallEntity> toolCalls) {
        return runContextRepository.listLoops(runId).stream()
                .sorted(Comparator.comparing(AgentRunLoopEntity::getLoopIndex))
                .map(loop -> loopDetail(loop, runContextDetails, traceDetails, traces, toolCalls))
                .toList();
    }

    private AgentObservabilityLoopVO loopDetail(AgentRunLoopEntity loop,
                                                 Map<String, Object> runContextDetails,
                                                 Map<String, Map<String, Object>> traceDetails,
                                                 List<AgentRunTraceEntity> traces,
                                                 List<ToolCallEntity> toolCalls) {
        Map<String, Object> record = readPayloadMap(loop.getRecordRef());
        Map<String, Object> mainOutput = map(record.get("mainOutput"));
        List<Map<String, Object>> inputs = traceMapsForLoop(loop.getLoopIndex(), "node_input_full", traceDetails, traces);
        List<Map<String, Object>> outputs = traceMapsForLoop(loop.getLoopIndex(), "node_output_full", traceDetails, traces);
        List<Map<String, Object>> plannerInputs = traceMapsForLoop(loop.getLoopIndex(), "node_input_full",
                "CONTEXT_PLANNER", traceDetails, traces);
        List<Map<String, Object>> plannerOutputs = traceMapsForLoop(loop.getLoopIndex(), "node_output_full",
                "CONTEXT_PLANNER", traceDetails, traces);
        List<Map<String, Object>> attempts = mergeAttempts(inputs, outputs);
        Map<String, Object> inputView = inputs.isEmpty() ? Map.of() : map(inputs.get(inputs.size() - 1).get("inputView"));
        Map<String, Object> canonicalBaseContext = map(map(runContextDetails == null ? null
                : runContextDetails.get("baseContext")).get("selectedSessionContext"));
        Map<String, Object> stateObservation = latestObservation(loop.getLoopIndex(), "STATE_VIEW",
                "materialized", traceDetails, traces);
        Map<String, Object> stateView = mainAgentStateView(stateObservation, inputView, canonicalBaseContext);
        Map<String, Object> selectedContext = selectedContext(stateView, canonicalBaseContext);
        Map<String, Object> runtimeOutcome = map(record.get("runtimeOutcome"));
        Map<String, Object> contextCandidates = latestObservation(loop.getLoopIndex(), "CONTEXT_PREPARE",
                "context_candidates", traceDetails, traces);
        Map<String, Object> plannerObservation = latestObservation(loop.getLoopIndex(), "CONTEXT_PLANNER",
                "selection_result", traceDetails, traces);
        if (plannerObservation.isEmpty()) {
            plannerObservation = latestObservation(loop.getLoopIndex(), "CONTEXT_PLANNER",
                    "planner_result", traceDetails, traces);
        }
        Map<String, Object> plannerInputView = plannerInputs.isEmpty() ? Map.of()
                : map(plannerInputs.get(plannerInputs.size() - 1).get("inputView"));
        boolean hasPlannerEvidence = !plannerInputs.isEmpty()
                || !plannerOutputs.isEmpty()
                || !plannerObservation.isEmpty();
        if (map(contextCandidates.get("candidateBundle")).isEmpty()) {
            Map<String, Object> recoveredBundle = !plannerInputView.isEmpty()
                    ? plannerInputView : candidateBundleFromState(selectedContext);
            if (!recoveredBundle.isEmpty()) {
                contextCandidates = new LinkedHashMap<>(contextCandidates);
                contextCandidates.put("candidateBundle", recoveredBundle);
                contextCandidates.putIfAbsent("mode", "recovered-from-node-input");
            }
        }
        Map<String, Object> planner = plannerDetails(plannerInputs, plannerOutputs, plannerObservation);
        if (hasPlannerEvidence && map(planner.get("candidateBundle")).isEmpty()) {
            Map<String, Object> recoveredBundle = !plannerInputView.isEmpty()
                    ? plannerInputView : map(contextCandidates.get("candidateBundle"));
            if (!recoveredBundle.isEmpty()) {
                planner.put("candidateBundle", recoveredBundle);
            }
        }
        List<Map<String, Object>> plannerAttempts = mergeAttempts(plannerInputs, plannerOutputs);
        if (!plannerAttempts.isEmpty()) {
            planner.put("attempts", plannerAttempts);
        }
        String action = string(mainOutput.get("action"));
        if (action == null && !outputs.isEmpty()) {
            Map<String, Object> rawAction = parseMap(string(outputs.get(outputs.size() - 1).get("rawOutput")));
            action = string(rawAction.get("action"));
            if (!rawAction.isEmpty()) {
                mainOutput = rawAction;
            }
        }
        Map<String, Object> taskLedger = map(stateView.get("taskLedger"));
        Map<String, Object> taskUpdate = map(mainOutput.get("taskUpdate"));
        List<Map<String, Object>> roundHistory = mapList(stateView.get("loopTimeline"));
        Map<String, Object> roundDelta = roundDelta(record, taskLedger, taskUpdate);
        Map<String, Object> actionInput = actionInput(mainOutput, record);
        Map<String, Object> finalDelivery = finalDeliveryDetails(action, mainOutput,
                actionInput, runtimeOutcome);
        return AgentObservabilityLoopVO.builder()
                .loopIndex(loop.getLoopIndex())
                .status(loop.getStatus())
                .stage(loop.getMainAgentStage() == null ? null : loop.getMainAgentStage().name())
                .startedAt(loop.getStartedAt())
                .completedAt(loop.getCompletedAt())
                .stateView(stateView)
                .selectedContext(selectedContext)
                .stateViewSources(stateViewSources(stateView))
                .taskLedger(taskLedger)
                .taskUpdate(taskUpdate)
                .roundDelta(roundDelta)
                .roundHistory(roundHistory)
                .promptRefs(inputs.stream().map(input -> string(input.get("payloadRef"))).filter(java.util.Objects::nonNull).toList())
                .attempts(attempts)
                .action(action)
                .actionInput(actionInput)
                .actionOutput(mainOutput)
                .runtimeOutcome(runtimeOutcome)
                .toolResults("CALL_TOOL".equals(action) ? toolCalls.stream().map(this::toToolObservationMap).toList() : List.of())
                .childAgentResults(childAgentResults(runtimeOutcome))
                .checkpoint(map(record.get("userInteraction")))
                .error(errorDetails(record))
                .contextCandidates(contextCandidates)
                .contextPlanner(planner)
                .finalDelivery(finalDelivery)
                .timeline(timelineForLoop(loop, traces, traceDetails))
                .build();
    }

    private List<Map<String, Object>> traceMapsForLoop(Integer loopIndex, String event,
                                                        Map<String, Map<String, Object>> details,
                                                        List<AgentRunTraceEntity> traces) {
        return traceMapsForLoop(loopIndex, event, "MAIN_AGENT", details, traces);
    }

    private List<Map<String, Object>> traceMapsForLoop(Integer loopIndex, String event, String componentCode,
                                                        Map<String, Map<String, Object>> details,
                                                        List<AgentRunTraceEntity> traces) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (AgentRunTraceEntity trace : traces) {
            Map<String, Object> detail = details.get(trace.getPayloadRef());
            if (detail == null || !event.equals(detail.get("event")) || !componentCode.equals(detail.get("code"))) {
                continue;
            }
            Integer observedLoop = integer(detail.get("loopIndex"));
            boolean belongsToLoop = loopIndex == null || loopIndex.equals(observedLoop)
                    // ContextPlanner was added to the invocation metadata after
                    // some runs had already been persisted.  Its initial call is
                    // unambiguously the preparation for loop 0 even when the old
                    // payload has no loopIndex field.
                    || (observedLoop == null && "CONTEXT_PLANNER".equals(componentCode)
                    && Integer.valueOf(0).equals(loopIndex));
            if (belongsToLoop) {
                Map<String, Object> withRef = new LinkedHashMap<>(detail);
                withRef.put("payloadRef", trace.getPayloadRef());
                matches.add(withRef);
            }
        }
        return matches;
    }

    private Map<String, Object> latestObservation(Integer loopIndex, String nodeType, String observationType,
                                                   Map<String, Map<String, Object>> details,
                                                   List<AgentRunTraceEntity> traces) {
        Map<String, Object> latest = new LinkedHashMap<>();
        for (AgentRunTraceEntity trace : traces) {
            Map<String, Object> detail = details.get(trace.getPayloadRef());
            if (detail == null || !"node_observation".equals(detail.get("event"))
                    || !nodeType.equals(detail.get("code"))
                    || !observationType.equals(detail.get("observationType"))) {
                continue;
            }
            Integer observedLoop = integer(detail.get("loopIndex"));
            boolean belongsToLoop = loopIndex == null || loopIndex.equals(observedLoop)
                    || (observedLoop == null && "CONTEXT_PLANNER".equals(nodeType)
                    && Integer.valueOf(0).equals(loopIndex));
            if (belongsToLoop) {
                latest = new LinkedHashMap<>(detail);
                latest.put("payloadRef", trace.getPayloadRef());
            }
        }
        return latest;
    }

    private Map<String, Object> plannerDetails(List<Map<String, Object>> inputs,
                                               List<Map<String, Object>> outputs,
                                               Map<String, Object> observation) {
        Map<String, Object> planner = new LinkedHashMap<>();
        if ((inputs == null || inputs.isEmpty()) && (outputs == null || outputs.isEmpty())
                && (observation == null || observation.isEmpty())) {
            return planner;
        }
        Map<String, Object> input = inputs.isEmpty() ? Map.of() : inputs.get(inputs.size() - 1);
        Map<String, Object> output = outputs.isEmpty() ? Map.of() : outputs.get(outputs.size() - 1);
        planner.put("prompt", input.get("prompt"));
        planner.put("systemPrompt", input.get("systemPrompt"));
        planner.put("userPrompt", input.get("userPrompt"));
        planner.put("inputView", input.get("inputView"));
        planner.put("invocationMetadata", input.get("invocationMetadata"));
        planner.put("output", output.get("typedOutput"));
        planner.put("rawOutput", output.get("rawOutput"));
        planner.put("parseResult", output.get("parseResult"));
        planner.put("validationResult", output.get("validationResult"));
        planner.put("success", output.get("success"));
        planner.put("selectionResult", observation.get("result"));
        planner.put("candidateBundle", observation.get("candidateBundle"));
        planner.values().removeIf(java.util.Objects::isNull);
        return planner;
    }

    private List<Map<String, Object>> timelineForLoop(AgentRunLoopEntity loop,
                                                       List<AgentRunTraceEntity> traces,
                                                       Map<String, Map<String, Object>> details) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (AgentRunTraceEntity trace : traces) {
            Map<String, Object> detail = details.get(trace.getPayloadRef());
            Integer detailLoopIndex = detail == null ? null : integer(detail.get("loopIndex"));
            boolean exactLoop = detail != null && loop.getLoopIndex() != null
                    && loop.getLoopIndex().equals(detailLoopIndex);
            boolean legacyInWindow = detail != null && detailLoopIndex == null && traceWithinLoop(trace, loop);
            if (!exactLoop && !legacyInWindow) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("seq", trace.getSeq());
            event.put("createdAt", trace.getCreatedAt());
            event.put("traceType", trace.getTraceType() == null ? null : trace.getTraceType().code());
            event.put("event", detail.get("event"));
            event.put("code", detail.get("code"));
            event.put("observationType", detail.get("observationType"));
            event.put("attemptNo", detail.get("attemptNo"));
            event.put("summary", detail.get("summary"));
            timeline.add(event);
        }
        timeline.sort(Comparator.comparing(item -> integer(item.get("seq")) == null ? Integer.MAX_VALUE : integer(item.get("seq"))));
        return timeline;
    }

    private List<Map<String, Object>> buildGraphNodes(Map<String, Object> context,
                                                       List<AgentObservabilityLoopVO> loops,
                                                       Map<String, Map<String, Object>> traceDetails,
                                                       List<AgentRunTraceEntity> traces,
                                                       AgentRunEntity run) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> contextDetail = new LinkedHashMap<>(context == null ? Map.of() : context);
        Map<String, Object> candidateObservation = latestObservationAny("CONTEXT_PREPARE", "context_candidates",
                traceDetails, traces);
        Map<String, Object> plannerObservation = latestObservationAny("CONTEXT_PLANNER", "selection_result",
                traceDetails, traces);
        if (!candidateObservation.isEmpty()) {
            contextDetail.put("candidateBundle", candidateObservation.get("candidateBundle"));
            contextDetail.put("candidateMode", candidateObservation.get("mode"));
        }
        if (!plannerObservation.isEmpty()) {
            contextDetail.put("plannerSelection", plannerObservation.get("result"));
            contextDetail.put("plannerOutput", plannerObservation.get("output"));
        }
        if (map(contextDetail.get("candidateBundle")).isEmpty() && loops != null && !loops.isEmpty()) {
            Map<String, Object> recovered = loops.get(0).getContextCandidates();
            if (recovered != null && !recovered.isEmpty()) {
                contextDetail.putAll(recovered);
            }
        }
        nodes.add(graphNode("context-prepare", "CONTEXT_PREPARE", "Context Prepare", null,
                nodeStatus(hasMeaningfulValue(contextDetail.get("candidateBundle"))
                                || hasMeaningfulValue(contextDetail.get("baseContext")), false),
                nodeSeverity(Map.of(), false),
                "MySQL / Vector / RAG candidates", contextDetail, List.of()));

        for (AgentObservabilityLoopVO loop : loops) {
            Integer loopIndex = loop.getLoopIndex();
            Map<String, Object> planner = loop.getContextPlanner();
            if (!planner.isEmpty()) {
                nodes.add(graphNode("context-planner-" + loopIndex, "CONTEXT_PLANNER", "Context Planner",
                        loopIndex, nodeStatus(true, planner.get("success") != null && Boolean.FALSE.equals(planner.get("success"))),
                        nodeSeverity(loop.getError(), false), "candidate selection", planner, loop.getTimeline()));
            }
            nodes.add(graphNode("state-view-" + loopIndex, "STATE_VIEW", "State View", loopIndex,
                    nodeStatus(!loop.getStateView().isEmpty(), false), nodeSeverity(loop.getError(), false),
                    "materialized memory and provenance", stateDetails(loop), loop.getTimeline()));
            Map<String, Object> mainDetails = new LinkedHashMap<>();
            mainDetails.put("input", loop.getStateView());
            mainDetails.put("stateView", loop.getStateView());
            mainDetails.put("selectedContext", loop.getSelectedContext());
            mainDetails.put("stateViewSources", loop.getStateViewSources());
            mainDetails.put("taskLedger", loop.getTaskLedger());
            mainDetails.put("taskUpdate", loop.getTaskUpdate());
            mainDetails.put("roundDelta", loop.getRoundDelta());
            mainDetails.put("roundHistory", loop.getRoundHistory());
            mainDetails.put("attempts", loop.getAttempts());
            mainDetails.put("action", loop.getAction());
            mainDetails.put("actionInput", loop.getActionInput());
            mainDetails.put("actionOutput", loop.getActionOutput());
            mainDetails.put("runtimeOutcome", loop.getRuntimeOutcome());
            mainDetails.put("timeline", loop.getTimeline());
            mainDetails.put("contextCandidates", loop.getContextCandidates());
            mainDetails.put("contextPlanner", loop.getContextPlanner());
            nodes.add(graphNode("main-node-" + loopIndex, "MAIN_NODE", "MainNode", loopIndex,
                    nodeStatus(!loop.getAttempts().isEmpty() || !loop.getActionOutput().isEmpty()
                                    || !loop.getStateView().isEmpty(), !loop.getError().isEmpty()),
                    nodeSeverity(loop.getError(), !loop.getAttempts().isEmpty() && attemptFailed(loop.getAttempts())),
                    "plan, full round memory, action", mainDetails, loop.getTimeline()));
            if (loop.getAction() != null && !loop.getAction().isBlank()) {
                String action = loop.getAction().toUpperCase();
                String actionType = actionNodeType(action);
                Map<String, Object> actionDetails = new LinkedHashMap<>();
                actionDetails.put("action", action);
                actionDetails.put("input", loop.getActionInput());
                actionDetails.put("output", loop.getActionOutput());
                actionDetails.put("runtimeOutcome", loop.getRuntimeOutcome());
                actionDetails.put("toolResults", loop.getToolResults());
                actionDetails.put("childAgentResults", loop.getChildAgentResults());
                actionDetails.put("checkpoint", loop.getCheckpoint());
                actionDetails.put("error", loop.getError());
                actionDetails.put("taskUpdate", loop.getTaskUpdate());
                actionDetails.put("roundDelta", loop.getRoundDelta());
                actionDetails.put("finalDelivery", loop.getFinalDelivery());
                nodes.add(graphNode("action-" + loopIndex + "-" + action.toLowerCase(), actionType,
                        actionLabel(action), loopIndex,
                        nodeStatus(!loop.getRuntimeOutcome().isEmpty() || !loop.getFinalDelivery().isEmpty(),
                                !loop.getError().isEmpty()),
                        nodeSeverity(loop.getError(), false), "Runtime result", actionDetails, loop.getTimeline()));
            }
        }
        appendRunLevelFailure(nodes, loops, run);
        return nodes;
    }

    private void appendRunLevelFailure(List<Map<String, Object>> nodes,
                                       List<AgentObservabilityLoopVO> loops,
                                       AgentRunEntity run) {
        if (run == null || run.getStatus() != yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO.FAILED
                || run.getFailureCode() == null || run.getFailureCode().isBlank()) {
            return;
        }
        String failureCode = run.getFailureCode();
        if (!failureCode.startsWith("BACKEND_") && !"UNEXPECTED_RUNTIME_ERROR".equals(failureCode)) {
            return;
        }
        Integer loopIndex = loops == null || loops.isEmpty() ? null : loops.get(loops.size() - 1).getLoopIndex();
        String summary = switch (failureCode) {
            case "BACKEND_OUT_OF_MEMORY" -> "JVM 内存耗尽，本次 Agent 运行已终止";
            case "BACKEND_PROCESS_TERMINATED" -> "后端进程在完成前终止，本次遗留运行已判定失败";
            default -> "后端出现未捕获异常，本次 Agent 运行已终止";
        };
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("failureCode", failureCode);
        details.put("failureOrigin", "BACKEND_RUNTIME");
        details.put("runStatus", run.getStatus().code());
        details.put("terminal", true);
        details.put("updatedAt", run.getUpdatedAt());
        nodes.add(graphNode("run-level-failure", "RUNTIME_FAILURE", "后端异常终止", loopIndex,
                "FAILED", "ERROR", summary, details, List.of()));
    }

    private Map<String, Object> latestObservationAny(String nodeType, String observationType,
                                                      Map<String, Map<String, Object>> details,
                                                      List<AgentRunTraceEntity> traces) {
        Map<String, Object> latest = new LinkedHashMap<>();
        for (AgentRunTraceEntity trace : traces) {
            Map<String, Object> detail = details.get(trace.getPayloadRef());
            if (detail != null && "node_observation".equals(detail.get("event"))
                    && nodeType.equals(detail.get("code"))
                    && observationType.equals(detail.get("observationType"))) {
                latest = new LinkedHashMap<>(detail);
                latest.put("payloadRef", trace.getPayloadRef());
            }
        }
        return latest;
    }

    private Map<String, Object> stateDetails(AgentObservabilityLoopVO loop) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("stateView", loop.getStateView());
        details.put("selectedContext", loop.getSelectedContext());
        details.put("sources", loop.getStateViewSources());
        details.put("taskLedger", loop.getTaskLedger());
        details.put("roundHistory", loop.getRoundHistory());
        details.put("contextCandidates", loop.getContextCandidates());
        details.put("contextPlanner", loop.getContextPlanner());
        return details;
    }

    private Map<String, Object> mainAgentStateView(Map<String, Object> stateObservation,
                                                    Map<String, Object> inputView,
                                                    Map<String, Object> canonicalSelectedContext) {
        if (inputView != null && !inputView.isEmpty()) {
            return new LinkedHashMap<>(inputView);
        }
        Map<String, Object> observed = map(stateObservation == null ? null : stateObservation.get("stateView"));
        if (!observed.isEmpty()) {
            return observed;
        }
        return canonicalSelectedContext == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(canonicalSelectedContext);
    }

    private Map<String, Object> selectedContext(Map<String, Object> stateView,
                                                 Map<String, Object> canonicalSelectedContext) {
        Map<String, Object> selected = map(map(stateView.get("runBaseContext")).get("selectedSessionContext"));
        if (selected.isEmpty()) {
            selected = map(stateView.get("selectedSessionContext"));
        }
        if (selected.isEmpty() && containsContextPack(stateView)) {
            selected = new LinkedHashMap<>(stateView);
        }
        if (selected.isEmpty() && canonicalSelectedContext != null) {
            selected = new LinkedHashMap<>(canonicalSelectedContext);
        }
        return selected;
    }

    private boolean containsContextPack(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return List.of("conversation", "memoryPack", "ragPack", "resolvedArtifacts",
                        "artifactContent", "evidencePack", "availableCapabilities", "pendingAction", "tokenBudget")
                .stream().anyMatch(value::containsKey);
    }

    private Map<String, Object> roundDelta(Map<String, Object> record,
                                            Map<String, Object> taskLedger,
                                            Map<String, Object> taskUpdate) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("taskLedgerVersionBefore", record.get("taskLedgerVersionBefore"));
        delta.put("taskLedgerVersionAfter", record.get("taskLedgerVersionAfter"));
        delta.put("currentStepBefore", taskLedger.get("currentStepId"));
        delta.put("currentStepAfter", taskUpdate.get("currentStepId"));
        delta.put("affectedStepIds", record.get("affectedStepIds"));
        delta.put("affectedDeliverableIds", record.get("affectedDeliverableIds"));
        delta.put("stepUpdates", taskUpdate.get("stepUpdates"));
        delta.put("deliverableUpdates", taskUpdate.get("deliverableUpdates"));
        delta.put("planRevision", taskUpdate.get("planRevision"));
        delta.put("facts", taskUpdate.get("facts"));
        delta.put("blockers", taskUpdate.get("blockers"));
        delta.put("lastDecision", taskUpdate.get("lastDecision"));
        delta.values().removeIf(value -> !hasMeaningfulValue(value));
        return delta;
    }

    private Map<String, Object> actionInput(Map<String, Object> mainOutput,
                                            Map<String, Object> record) {
        Map<String, Object> stateDelta = map(mainOutput.get("stateDelta"));
        if (!stateDelta.isEmpty()) {
            return stateDelta;
        }
        return map(record.get("actionRequest"));
    }

    private Map<String, Object> finalDeliveryDetails(String action,
                                                      Map<String, Object> mainOutput,
                                                      Map<String, Object> actionRequest,
                                                      Map<String, Object> runtimeOutcome) {
        if (!"FINAL".equalsIgnoreCase(action)) {
            return Map.of();
        }
        Map<String, Object> details = map(runtimeOutcome.get("details"));
        Map<String, Object> resultSnapshot = map(details.get("resultSnapshot"));
        Map<String, Object> resultCandidate = map(resultSnapshot.get("finalAnswerCandidate"));
        Map<String, Object> requestCandidate = map(actionRequest.get("finalAnswerCandidate"));
        Map<String, Object> outputCandidate = map(map(mainOutput.get("stateDelta")).get("finalAnswerCandidate"));
        Map<String, Object> candidate = firstNonEmptyMap(resultCandidate, requestCandidate, outputCandidate);
        String resultPayloadRef = string(runtimeOutcome.get("resultPayloadRef"));
        Map<String, Object> resultPayload = readPayloadMap(resultPayloadRef);
        Map<String, Object> payloadCandidate = map(resultPayload.get("finalAnswerCandidate"));

        String deliveredContent = firstNonBlank(
                string(resultCandidate.get("content")),
                string(requestCandidate.get("content")),
                string(outputCandidate.get("content")),
                string(payloadCandidate.get("content")),
                string(resultPayload.get("deliveredContent")),
                string(resultPayload.get("content")));
        String contentRef = firstNonBlank(
                string(resultCandidate.get("contentRef")),
                string(requestCandidate.get("contentRef")),
                string(outputCandidate.get("contentRef")),
                string(payloadCandidate.get("contentRef")),
                string(resultSnapshot.get("finalAnswerRef")),
                resultPayloadRef);
        if (deliveredContent == null && contentRef != null) {
            deliveredContent = readPayloadText(contentRef);
        }

        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("status", runtimeOutcome.get("status"));
        delivery.put("summary", runtimeOutcome.get("summary"));
        delivery.put("deliveredContent", deliveredContent);
        delivery.put("format", firstNonBlank(string(candidate.get("format")), string(payloadCandidate.get("format"))));
        delivery.put("contentRef", contentRef);
        delivery.put("finalMessageId", resultSnapshot.get("finalMessageId"));
        delivery.put("resultPayloadRef", resultPayloadRef);
        delivery.put("evidenceRefs", runtimeOutcome.get("evidenceRefs"));
        delivery.put("artifactRefs", runtimeOutcome.get("artifactRefs"));
        delivery.put("candidate", candidate);
        delivery.put("guard", details);
        if (!resultPayload.isEmpty() && resultPayload.get("loadError") == null) {
            delivery.put("resultPayload", resultPayload);
        }
        delivery.values().removeIf(value -> !hasMeaningfulValue(value));
        return delivery;
    }

    @SafeVarargs
    private final Map<String, Object> firstNonEmptyMap(Map<String, Object>... values) {
        for (Map<String, Object> value : values) {
            if (value != null && !value.isEmpty()) {
                return new LinkedHashMap<>(value);
            }
        }
        return new LinkedHashMap<>();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean traceWithinLoop(AgentRunTraceEntity trace, AgentRunLoopEntity loop) {
        if (trace == null || trace.getCreatedAt() == null || loop == null) {
            return false;
        }
        boolean afterStart = loop.getStartedAt() == null || !trace.getCreatedAt().isBefore(loop.getStartedAt());
        boolean beforeEnd = loop.getCompletedAt() == null || !trace.getCreatedAt().isAfter(loop.getCompletedAt());
        return afterStart && beforeEnd;
    }

    private boolean hasMeaningfulValue(Object value) {
        if (value == null) return false;
        if (value instanceof CharSequence text) return !text.toString().isBlank();
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(this::hasMeaningfulValue);
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        return true;
    }

    private Map<String, Object> candidateBundleFromState(Map<String, Object> stateView) {
        if (stateView == null || stateView.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> bundle = new LinkedHashMap<>();
        Map<String, Object> conversation = map(stateView.get("conversation"));
        bundle.put("runMeta", stateView.get("runMeta"));
        bundle.put("userInput", stateView.get("userInput"));
        bundle.put("fixedRecentMessages", List.of());
        bundle.put("recentMessages", listValue(conversation.get("recentMessages")));
        bundle.put("sessionSummaries", listValue(conversation.get("summaries")));
        bundle.put("memoryCandidates", listValue(stateView.get("memoryPack")));
        bundle.put("ragCandidates", listValue(stateView.get("ragPack")));
        bundle.put("evidenceCandidates", listValue(stateView.get("evidencePack")));
        bundle.put("artifactCandidates", listValue(stateView.get("resolvedArtifacts")));
        bundle.put("availableCapabilities", listValue(stateView.get("availableCapabilities")));
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("mode", "recovered-from-state-view");
        diagnostics.put("fullText", listValue(conversation.get("recentMessages")).size());
        diagnostics.put("summaries", listValue(conversation.get("summaries")).size());
        diagnostics.put("memory", listValue(stateView.get("memoryPack")).size());
        diagnostics.put("rag", listValue(stateView.get("ragPack")).size());
        diagnostics.put("evidence", listValue(stateView.get("evidencePack")).size());
        bundle.put("recallDiagnostics", diagnostics);
        bundle.values().removeIf(java.util.Objects::isNull);
        return bundle;
    }

    private List<Object> listValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value == null ? new ArrayList<>() : new ArrayList<>(List.of(value));
    }

    private boolean attemptFailed(List<Map<String, Object>> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return false;
        }
        Map<String, Object> last = attempts.get(attempts.size() - 1);
        return !Boolean.TRUE.equals(last.get("success"))
                && (Boolean.FALSE.equals(last.get("success")) || last.get("failureType") != null);
    }

    private String nodeStatus(boolean hasData, boolean failed) {
        if (failed) return "FAILED";
        return hasData ? "SUCCEEDED" : "PENDING";
    }

    private String nodeSeverity(Map<String, Object> error, boolean attemptFailed) {
        return (!error.isEmpty() || attemptFailed) ? "ERROR" : "INFO";
    }

    private String actionNodeType(String action) {
        return switch (action) {
            case "CALL_TOOL" -> "TOOL_USE";
            case "ASK_USER" -> "ASK_USER";
            case "DELEGATE_AGENTS" -> "DELEGATE";
            case "RETRIEVE_RAG" -> "RAG_RETRIEVAL";
            case "READY_TO_DELIVER" -> "READY_TO_DELIVER";
            case "FINAL" -> "FINAL_DELIVERY";
            default -> "RUNTIME_ACTION";
        };
    }

    private String actionLabel(String action) {
        return switch (action) {
            case "CALL_TOOL" -> "调用工具";
            case "ASK_USER" -> "询问用户";
            case "DELEGATE_AGENTS" -> "委派子 Agent";
            case "RETRIEVE_RAG" -> "检索知识库";
            case "READY_TO_DELIVER" -> "准备最终交付";
            case "FINAL" -> "最终回答";
            default -> action.toLowerCase();
        };
    }

    private Map<String, Object> graphNode(String id, String type, String title, Integer loopIndex,
                                          String status, String severity, String summary,
                                          Map<String, Object> details,
                                          List<Map<String, Object>> timeline) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("title", title);
        node.put("loopIndex", loopIndex);
        node.put("status", status);
        node.put("severity", severity);
        node.put("summary", summary);
        node.put("details", details == null ? Map.of() : details);
        node.put("timeline", timeline == null ? List.of() : timeline);
        return node;
    }

    private List<Map<String, Object>> mergeAttempts(List<Map<String, Object>> inputs, List<Map<String, Object>> outputs) {
        Map<Integer, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> input : inputs) {
            Integer attemptNo = integer(input.get("attemptNo"));
            Map<String, Object> attempt = merged.computeIfAbsent(attemptNo, ignored -> new LinkedHashMap<>());
            attempt.putAll(input);
            attempt.put("inputPayloadRef", input.get("payloadRef"));
        }
        for (Map<String, Object> output : outputs) {
            Integer attemptNo = integer(output.get("attemptNo"));
            Map<String, Object> attempt = merged.computeIfAbsent(attemptNo, ignored -> new LinkedHashMap<>());
            attempt.put("outputPayloadRef", output.get("payloadRef"));
            attempt.put("rawOutput", output.get("rawOutput"));
            attempt.put("parseResult", output.get("parseResult"));
            attempt.put("validationResult", output.get("validationResult"));
            attempt.put("typedOutput", output.get("typedOutput"));
            attempt.put("success", output.get("success"));
            attempt.put("failureType", output.get("failureType"));
            attempt.put("failureMessage", output.get("failureMessage"));
        }
        return new ArrayList<>(merged.values());
    }

    private List<Map<String, Object>> stateViewSources(Map<String, Object> stateView) {
        List<Map<String, Object>> sources = new ArrayList<>();
        Map<String, Object> selectedContext = selectedContext(stateView, Map.of());
        List<String> fields = List.of("conversation", "memoryPack", "ragPack", "resolvedArtifacts",
                "artifactContent", "evidencePack", "availableCapabilities", "pendingAction", "tokenBudget");
        for (String field : fields) {
            addStateSource(sources, field, "runBaseContext.selectedSessionContext." + field,
                    selectedContext.get(field));
        }
        addStateSource(sources, "taskLedger", "taskLedger", stateView.get("taskLedger"));
        addStateSource(sources, "loopTimeline", "loopTimeline", stateView.get("loopTimeline"));
        addStateSource(sources, "payloadManifest", "payloadManifest", stateView.get("payloadManifest"));
        addStateSource(sources, "activePayloads", "activePayloads", stateView.get("activePayloads"));
        addStateSource(sources, "resolvedPayloads", "resolvedPayloads", stateView.get("resolvedPayloads"));
        addStateSource(sources, "runtimeControl", "runtimeControl", stateView.get("runtimeControl"));
        return sources;
    }

    private void addStateSource(List<Map<String, Object>> sources, String field, String path, Object value) {
        if (!hasMeaningfulValue(value)) {
            return;
        }
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("field", field);
        source.put("path", path);
        source.put("count", meaningfulCount(field, value));
        source.put("value", value);
        sources.add(source);
    }

    private int meaningfulCount(String field, Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        Map<String, Object> object = map(value);
        if ("conversation".equals(field) && !object.isEmpty()) {
            int recent = listValue(object.get("recentMessages")).size();
            int summaries = listValue(object.get("summaries")).size();
            int taskSummary = hasMeaningfulValue(object.get("sessionTaskSummary")) ? 1 : 0;
            return recent + summaries + taskSummary;
        }
        if ("taskLedger".equals(field) && !object.isEmpty()) {
            return listValue(object.get("steps")).size() + listValue(object.get("deliverables")).size();
        }
        return value instanceof Map<?, ?> ? 1 : 1;
    }

    private Map<String, Object> errorDetails(Map<String, Object> record) {
        Map<String, Object> runtimeOutcome = map(record.get("runtimeOutcome"));
        Map<String, Object> error = new LinkedHashMap<>();
        String status = string(runtimeOutcome.get("status"));
        if (status != null && isFailureStatus(status)) {
            error.put("status", status);
        }
        for (String key : List.of("failureCode", "failureMessage", "error", "verificationFailure")) {
            if (runtimeOutcome.get(key) != null) error.put(key, runtimeOutcome.get(key));
        }
        Map<String, Object> details = map(runtimeOutcome.get("details"));
        for (String key : List.of("failureCode", "failureMessage", "error", "verificationFailure")) {
            if (details.get(key) != null) error.putIfAbsent(key, details.get(key));
        }
        return error;
    }

    private boolean isFailureStatus(String status) {
        String normalized = status == null ? "" : status.toUpperCase();
        return normalized.contains("FAIL") || normalized.contains("ERROR")
                || normalized.contains("REJECT") || normalized.contains("CANCEL");
    }

    private List<Map<String, Object>> childAgentResults(Map<String, Object> runtimeOutcome) {
        Map<String, Object> details = map(runtimeOutcome.get("details"));
        Map<String, Object> projected = map(details.get("childAgentResults"));
        if (!projected.isEmpty()) {
            return projected.entrySet().stream().map(entry -> {
                Map<String, Object> child = map(entry.getValue());
                child.putIfAbsent("childRunId", entry.getKey());
                return child;
            }).toList();
        }
        List<Map<String, Object>> direct = mapList(details.get("childResults"));
        if (!direct.isEmpty()) return direct;
        Map<String, Object> resultSnapshot = map(details.get("resultSnapshot"));
        return stringList(resultSnapshot.get("childRunIds")).stream().map(childRunId -> {
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("childRunId", childRunId);
            child.put("status", runtimeOutcome.get("status"));
            return child;
        }).toList();
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            Map<String, Object> map = map(item);
            if (!map.isEmpty()) result.add(map);
        }
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : iterable) if (item != null) result.add(String.valueOf(item));
        return result;
    }

    private Map<String, AgentPayloadEntity> loadTracePayloads(List<AgentRunTraceEntity> traces) {
        Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        for (AgentRunTraceEntity trace : traces) {
            if (trace.getPayloadRef() == null || payloads.containsKey(trace.getPayloadRef())) {
                continue;
            }
            payloadRepository.findPayload(trace.getPayloadRef())
                    .map(debugPayloadPreviewPolicy::applyPreviewPolicy)
                    .ifPresent(payload -> payloads.put(trace.getPayloadRef(), payload));
        }
        return payloads;
    }

    private Map<String, Map<String, Object>> parseTraceDetails(Map<String, AgentPayloadEntity> payloads) {
        Map<String, Map<String, Object>> details = new LinkedHashMap<>();
        payloads.forEach((ref, payload) -> details.put(ref, parseMap(payload == null ? null : payload.getContent())));
        return details;
    }

    private Map<String, Object> readPayloadMap(String ref) {
        if (ref == null) {
            return Map.of();
        }
        return payloadRepository.findContent(ref).map(this::parseMap).orElseGet(Map::of);
    }

    private String readPayloadText(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        return payloadRepository.findContent(ref).orElse(null);
    }

    private Map<String, Object> parseMap(String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.parseObject(content, Map.class);
        } catch (Exception error) {
            return Map.of("loadError", error.getMessage(), "rawPreview", content.substring(0, Math.min(500, content.length())));
        }
    }

    private Map<String, Object> toMap(Object value) {
        return value == null ? Map.of() : JSON.parseObject(JSON.toJSONString(value), Map.class);
    }

    private Map<String, Object> toToolObservationMap(ToolCallEntity tool) {
        Map<String, Object> value = toMap(tool);
        value.put("arguments", readPayloadMap(tool.getArgumentsRef()));
        value.put("receipt", readPayloadMap(tool.getReceiptRef()));
        return value;
    }

    private void loadPayload(Map<String, AgentPayloadEntity> payloads, String payloadRef) {
        if (payloadRef == null || payloadRef.isBlank() || payloads.containsKey(payloadRef)) {
            return;
        }
        payloadRepository.findPayload(payloadRef)
                .map(debugPayloadPreviewPolicy::applyPreviewPolicy)
                .ifPresent(payload -> payloads.put(payloadRef, payload));
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return value == null ? new LinkedHashMap<>() : toMap(value);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int normalizedLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
    }
}


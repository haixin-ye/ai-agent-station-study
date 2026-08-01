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

    public AgentDebugFacade(IEventTraceRepository eventTraceRepository,
                            IEvidenceRepository evidenceRepository,
                            IToolRepository toolRepository,
                            IPayloadRepository payloadRepository,
                            DebugAccessPolicy debugAccessPolicy,
                            DebugPayloadPreviewPolicy debugPayloadPreviewPolicy) {
        this(eventTraceRepository, evidenceRepository, toolRepository, payloadRepository,
                debugAccessPolicy, debugPayloadPreviewPolicy, null, null);
    }

    public AgentDebugFacade(IEventTraceRepository eventTraceRepository,
                            IEvidenceRepository evidenceRepository,
                            IToolRepository toolRepository,
                            IPayloadRepository payloadRepository,
                            DebugAccessPolicy debugAccessPolicy,
                            DebugPayloadPreviewPolicy debugPayloadPreviewPolicy,
                            IRunRepository runRepository,
                            IRunContextRepository runContextRepository) {
        this.eventTraceRepository = eventTraceRepository;
        this.evidenceRepository = evidenceRepository;
        this.toolRepository = toolRepository;
        this.payloadRepository = payloadRepository;
        this.debugAccessPolicy = debugAccessPolicy;
        this.debugPayloadPreviewPolicy = debugPayloadPreviewPolicy;
        this.runRepository = runRepository;
        this.runContextRepository = runContextRepository;
    }

    public List<AgentRunTraceEntity> listTraces(String runId, int limit) {
        debugAccessPolicy.requireDebugApiEnabled();
        return eventTraceRepository.listDebugTrace(runId, normalizedLimit(limit));
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
        AgentRunEntity run = runRepository == null ? null : runRepository.findRun(runId).orElse(null);
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
                loops.addAll(loopDetails(runId, traceDetails, traces, toolCalls));
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
        context.put("sourceRefs", Map.of(
                "baseContext", entity.getBaseContextRef(),
                "taskLedger", entity.getTaskLedgerRef(),
                "runtimeControl", entity.getRuntimeControlRef()));
        return context;
    }

    private List<AgentObservabilityLoopVO> loopDetails(String runId,
                                                        Map<String, Map<String, Object>> traceDetails,
                                                        List<AgentRunTraceEntity> traces,
                                                        List<ToolCallEntity> toolCalls) {
        return runContextRepository.listLoops(runId).stream()
                .sorted(Comparator.comparing(AgentRunLoopEntity::getLoopIndex))
                .map(loop -> loopDetail(loop, traceDetails, traces, toolCalls))
                .toList();
    }

    private AgentObservabilityLoopVO loopDetail(AgentRunLoopEntity loop,
                                                 Map<String, Map<String, Object>> traceDetails,
                                                 List<AgentRunTraceEntity> traces,
                                                 List<ToolCallEntity> toolCalls) {
        Map<String, Object> record = readPayloadMap(loop.getRecordRef());
        Map<String, Object> mainOutput = map(record.get("mainOutput"));
        List<Map<String, Object>> inputs = traceMapsForLoop(loop.getLoopIndex(), "node_input_full", traceDetails, traces);
        List<Map<String, Object>> outputs = traceMapsForLoop(loop.getLoopIndex(), "node_output_full", traceDetails, traces);
        List<Map<String, Object>> attempts = mergeAttempts(inputs, outputs);
        Map<String, Object> stateView = inputs.isEmpty() ? Map.of() : map(inputs.get(inputs.size() - 1).get("inputView"));
        Map<String, Object> runtimeOutcome = map(record.get("runtimeOutcome"));
        String action = string(mainOutput.get("action"));
        if (action == null && !outputs.isEmpty()) {
            Map<String, Object> rawAction = parseMap(string(outputs.get(outputs.size() - 1).get("rawOutput")));
            action = string(rawAction.get("action"));
            if (!rawAction.isEmpty()) {
                mainOutput = rawAction;
            }
        }
        return AgentObservabilityLoopVO.builder()
                .loopIndex(loop.getLoopIndex())
                .status(loop.getStatus())
                .stage(loop.getMainAgentStage() == null ? null : loop.getMainAgentStage().name())
                .startedAt(loop.getStartedAt())
                .completedAt(loop.getCompletedAt())
                .stateView(stateView)
                .stateViewSources(stateViewSources(stateView))
                .promptRefs(inputs.stream().map(input -> string(input.get("payloadRef"))).filter(java.util.Objects::nonNull).toList())
                .attempts(attempts)
                .action(action)
                .actionInput(map(record.get("actionRequest")))
                .actionOutput(mainOutput)
                .runtimeOutcome(runtimeOutcome)
                .toolResults("CALL_TOOL".equals(action) ? toolCalls.stream().map(this::toToolObservationMap).toList() : List.of())
                .childAgentResults(childAgentResults(runtimeOutcome))
                .checkpoint(map(record.get("userInteraction")))
                .error(errorDetails(record))
                .build();
    }

    private List<Map<String, Object>> traceMapsForLoop(Integer loopIndex, String event,
                                                        Map<String, Map<String, Object>> details,
                                                        List<AgentRunTraceEntity> traces) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (AgentRunTraceEntity trace : traces) {
            Map<String, Object> detail = details.get(trace.getPayloadRef());
            if (detail == null || !event.equals(detail.get("event")) || !"MAIN_AGENT".equals(detail.get("code"))) {
                continue;
            }
            if (loopIndex != null && loopIndex.equals(integer(detail.get("loopIndex")))) {
                Map<String, Object> withRef = new LinkedHashMap<>(detail);
                withRef.put("payloadRef", trace.getPayloadRef());
                matches.add(withRef);
            }
        }
        return matches;
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
        List<String> fields = List.of("conversation", "memoryPack", "ragPack", "resolvedArtifacts",
                "artifactContent", "evidencePack", "availableCapabilities", "pendingAction");
        for (String field : fields) {
            Object value = stateView.get(field);
            if (value == null || value instanceof List<?> list && list.isEmpty()) {
                continue;
            }
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("field", field);
            source.put("count", value instanceof List<?> list ? list.size() : 1);
            source.put("value", value);
            sources.add(source);
        }
        return sources;
    }

    private Map<String, Object> errorDetails(Map<String, Object> record) {
        Map<String, Object> runtimeOutcome = map(record.get("runtimeOutcome"));
        Map<String, Object> error = new LinkedHashMap<>();
        for (String key : List.of("status", "code", "summary", "failureCode", "failureMessage", "error", "verificationFailure")) {
            if (runtimeOutcome.get(key) != null) error.put(key, runtimeOutcome.get(key));
        }
        Map<String, Object> details = map(runtimeOutcome.get("details"));
        for (String key : List.of("failureCode", "failureMessage", "error", "verificationFailure")) {
            if (details.get(key) != null) error.putIfAbsent(key, details.get(key));
        }
        return error;
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


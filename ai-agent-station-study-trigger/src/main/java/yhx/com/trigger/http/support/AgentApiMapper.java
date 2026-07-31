package yhx.com.trigger.http.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import yhx.com.api.dto.agent.AgentArtifactDetailDTO;
import yhx.com.api.dto.agent.AgentArtifactSummaryDTO;
import yhx.com.api.dto.agent.AgentArtifactVersionDTO;
import yhx.com.api.dto.agent.AgentDebugPayloadDTO;
import yhx.com.api.dto.agent.AgentDebugTraceDTO;
import yhx.com.api.dto.agent.AgentObservabilityLoopDTO;
import yhx.com.api.dto.agent.AgentObservabilityStudioDTO;
import yhx.com.api.dto.agent.AgentFinalResponseDTO;
import yhx.com.api.dto.agent.AgentMessageDTO;
import yhx.com.api.dto.agent.AgentMockScenarioDTO;
import yhx.com.api.dto.agent.AgentPendingInputDTO;
import yhx.com.api.dto.agent.AgentPendingOptionDTO;
import yhx.com.api.dto.agent.AgentRunDTO;
import yhx.com.api.dto.agent.AgentUserVisibleEventDTO;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.observability.AgentObservabilityLoopVO;
import yhx.com.domain.agent.model.valobj.observability.AgentObservabilitySnapshotVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.mock.AgentMockEventVO;
import yhx.com.domain.agent.model.valobj.mock.AgentMockScenarioVO;
import yhx.com.domain.agent.service.api.AgentQueryFacade;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentApiMapper {

    private AgentApiMapper() {
    }

    public static AgentRunDTO toRun(AgentRunEntity entity) {
        if (entity == null) {
            return null;
        }
        boolean done = entity.getStatus() == RunStatusEnumVO.COMPLETED
                || entity.getStatus() == RunStatusEnumVO.FAILED
                || entity.getStatus() == RunStatusEnumVO.CANCELLED;
        return AgentRunDTO.builder()
                .runId(entity.getRunId())
                .sessionId(entity.getSessionId())
                .userId(entity.getUserId())
                .agentId(entity.getAgentId())
                .status(code(entity.getStatus()))
                .currentPhase(code(entity.getPhase()))
                .loopIndex(null)
                .startedAt(entity.getCreatedAt())
                .completedAt(done ? entity.getUpdatedAt() : null)
                .build();
    }

    public static AgentMessageDTO toMessage(AgentMessageEntity entity, AgentQueryFacade queryFacade) {
        return AgentMessageDTO.builder()
                .messageId(entity.getMessageId())
                .sessionId(entity.getSessionId())
                .runId(entity.getRunId())
                .role(code(entity.getRole()))
                .content(queryFacade.resolveContent(entity.getContentRef()).orElse(null))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static AgentFinalResponseDTO toFinal(AgentRunEntity run, Optional<String> finalAnswer, List<AgentArtifactEntity> artifacts) {
        return AgentFinalResponseDTO.builder()
                .runId(run.getRunId())
                .sessionId(run.getSessionId())
                .status(code(run.getStatus()))
                .messageId(run.getFinalAnswerRef())
                .finalAnswer(finalAnswer.orElse(null))
                .artifacts(artifacts.stream().map(AgentApiMapper::toArtifactSummary).toList())
                .citations(List.of())
                .followUpOptions(List.of())
                .completed(run.getStatus() == RunStatusEnumVO.COMPLETED)
                .build();
    }

    public static AgentUserVisibleEventDTO toUserEvent(AgentRunEventEntity entity, AgentQueryFacade queryFacade) {
        Map<String, Object> payload = queryFacade.readJsonPayloadAsMap(entity.getPayloadRef());
        String title = string(payload.get("title"));
        String summary = string(payload.get("summary"));
        String pendingInputId = string(payload.get("pendingInputId"));
        String artifactId = string(payload.get("artifactId"));
        return AgentUserVisibleEventDTO.builder()
                .eventId(entity.getEventId())
                .runId(entity.getRunId())
                .seq(entity.getSeq())
                .eventType(code(entity.getEventType()))
                .phase(title)
                .status(code(entity.getEventType()))
                .title(title)
                .message(summary)
                .summary(summary)
                .artifactId(artifactId)
                .artifactRefs(artifactId == null ? List.of() : List.of(artifactId))
                .pendingId(pendingInputId)
                .pendingInputId(pendingInputId)
                .safePayload(payload)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static AgentUserVisibleEventDTO toMockEvent(AgentMockEventVO event) {
        return AgentUserVisibleEventDTO.builder()
                .eventId(event.getEventId())
                .runId(event.getRunId())
                .seq(event.getSeq())
                .eventType(event.getEventType())
                .phase(event.getTitle())
                .status(event.getEventType())
                .title(event.getTitle())
                .message(event.getMessage())
                .summary(event.getMessage())
                .artifactId(event.getArtifactId())
                .artifactRefs(event.getArtifactId() == null ? List.of() : List.of(event.getArtifactId()))
                .pendingId(event.getPendingId())
                .pendingInputId(event.getPendingId())
                .safePayload(event.getSafePayload())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public static AgentPendingInputDTO toPendingInput(AgentPendingInputEntity entity, AgentQueryFacade queryFacade) {
        List<AgentPendingOptionDTO> options = parseOptions(entity.getOptionsRef(), queryFacade);
        boolean allowFreeText = "FREE_TEXT".equals(entity.getInputMode())
                || "SINGLE_CHOICE_OR_FREE_TEXT".equals(entity.getInputMode());
        return AgentPendingInputDTO.builder()
                .pendingId(entity.getPendingId())
                .runId(entity.getRunId())
                .pendingType(entity.getPendingType())
                .inputMode(entity.getInputMode())
                .allowFreeText(allowFreeText)
                .question(entity.getQuestion())
                .options(options)
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    public static AgentArtifactSummaryDTO toArtifactSummary(AgentArtifactEntity entity) {
        return AgentArtifactSummaryDTO.builder()
                .artifactId(entity.getArtifactId())
                .sessionId(entity.getSessionId())
                .runId(entity.getRunId())
                .artifactType(entity.getArtifactType())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .version(entity.getVersion())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static AgentArtifactDetailDTO toArtifactDetail(AgentArtifactEntity entity, AgentQueryFacade queryFacade) {
        return AgentArtifactDetailDTO.builder()
                .artifactId(entity.getArtifactId())
                .sessionId(entity.getSessionId())
                .runId(entity.getRunId())
                .artifactType(entity.getArtifactType())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .content(queryFacade.resolveContent(entity.getContentRef()).orElse(null))
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static AgentArtifactVersionDTO toArtifactVersion(AgentArtifactEntity entity) {
        return AgentArtifactVersionDTO.builder()
                .artifactId(entity.getArtifactId())
                .version(entity.getVersion())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static AgentDebugTraceDTO toDebugTrace(AgentRunTraceEntity entity, AgentQueryFacade queryFacade) {
        Map<String, Object> payload = queryFacade.readJsonPayloadAsMap(entity.getPayloadRef());
        return AgentDebugTraceDTO.builder()
                .traceId(entity.getTraceId())
                .runId(entity.getRunId())
                .seq(entity.getSeq())
                .traceType(code(entity.getTraceType()))
                .componentName(string(payload.get("code")))
                .actionType(string(payload.get("event")))
                .severity("INFO")
                .summary(string(payload.get("summary")))
                .payloadRef(entity.getPayloadRef())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static AgentDebugPayloadDTO toDebugPayload(AgentPayloadEntity entity) {
        boolean rawIncluded = entity.getContent() != null;
        return AgentDebugPayloadDTO.builder()
                .payloadId(entity.getPayloadId())
                .payloadType(code(entity.getPayloadType()))
                .preview(entity.getPreview())
                .previewTruncated(false)
                .rawContentIncluded(rawIncluded)
                .rawContent(entity.getContent())
                .build();
    }

    public static AgentObservabilityStudioDTO toObservabilityStudio(AgentObservabilitySnapshotVO snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, AgentDebugPayloadDTO> payloads = new java.util.LinkedHashMap<>();
        snapshot.getPayloads().forEach((ref, payload) -> payloads.put(ref, toDebugPayload(payload)));
        return AgentObservabilityStudioDTO.builder()
                .header(snapshot.getHeader())
                .status(snapshot.getStatus())
                .currentPhase(snapshot.getCurrentPhase())
                .context(snapshot.getContext())
                .loops(snapshot.getLoops().stream().map(AgentApiMapper::toObservabilityLoop).toList())
                .traces(snapshot.getTraces().stream().map(trace -> toDebugTrace(trace, snapshot.getPayloads().get(trace.getPayloadRef()))).toList())
                .payloads(payloads)
                .evidence(snapshot.getEvidence().stream().map(AgentApiMapper::toEvidenceMap).toList())
                .toolCalls(snapshot.getToolCalls().stream().map(AgentApiMapper::toToolMap).toList())
                .lastSeq(snapshot.getLastSeq())
                .build();
    }

    private static AgentDebugTraceDTO toDebugTrace(AgentRunTraceEntity entity, AgentPayloadEntity payloadEntity) {
        Map<String, Object> payload = payloadEntity == null || payloadEntity.getContent() == null
                ? Collections.emptyMap()
                : JSON.parseObject(payloadEntity.getContent(), Map.class);
        return AgentDebugTraceDTO.builder()
                .traceId(entity.getTraceId())
                .runId(entity.getRunId())
                .seq(entity.getSeq())
                .traceType(code(entity.getTraceType()))
                .componentName(string(payload.get("code")))
                .actionType(string(payload.get("event")))
                .severity("INFO")
                .summary(string(payload.get("summary")))
                .payloadRef(entity.getPayloadRef())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static AgentObservabilityLoopDTO toObservabilityLoop(AgentObservabilityLoopVO loop) {
        return AgentObservabilityLoopDTO.builder()
                .loopIndex(loop.getLoopIndex())
                .status(loop.getStatus())
                .stage(loop.getStage())
                .startedAt(loop.getStartedAt())
                .completedAt(loop.getCompletedAt())
                .stateView(loop.getStateView())
                .stateViewSources(loop.getStateViewSources())
                .promptRefs(loop.getPromptRefs())
                .attempts(loop.getAttempts())
                .action(loop.getAction())
                .actionInput(loop.getActionInput())
                .actionOutput(loop.getActionOutput())
                .runtimeOutcome(loop.getRuntimeOutcome())
                .toolResults(loop.getToolResults())
                .childAgentResults(loop.getChildAgentResults())
                .checkpoint(loop.getCheckpoint())
                .error(loop.getError())
                .build();
    }

    private static Map<String, Object> toEvidenceMap(AgentEvidenceEntity entity) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("evidenceId", entity.getEvidenceId());
        value.put("runId", entity.getRunId());
        value.put("evidenceType", entity.getEvidenceType());
        value.put("sourceRef", entity.getSourceRef());
        value.put("summary", entity.getSummary());
        value.put("confidence", entity.getConfidence());
        value.put("usedByFinal", entity.getUsedByFinal());
        value.put("createdAt", entity.getCreatedAt());
        return value;
    }

    private static Map<String, Object> toToolMap(ToolCallEntity entity) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("toolCallId", entity.getToolCallId());
        value.put("runId", entity.getRunId());
        value.put("toolName", entity.getToolName());
        value.put("mcpServerName", entity.getMcpServerName());
        value.put("status", entity.getStatus() == null ? null : entity.getStatus().code());
        value.put("argumentsRef", entity.getArgumentsRef());
        value.put("receiptRef", entity.getReceiptRef());
        value.put("failureCode", entity.getFailureCode());
        value.put("createdAt", entity.getCreatedAt());
        return value;
    }

    public static AgentMockScenarioDTO toMockScenario(AgentMockScenarioVO scenario) {
        return AgentMockScenarioDTO.builder()
                .scenario(scenario.getScenario())
                .title(scenario.getTitle())
                .description(scenario.getDescription())
                .debugScenario(scenario.getDebugScenario())
                .build();
    }

    private static List<AgentPendingOptionDTO> parseOptions(String optionsRef, AgentQueryFacade queryFacade) {
        return queryFacade.resolveContent(optionsRef)
                .map(content -> {
                    try {
                        JSONArray array = JSON.parseArray(content);
                        return array.stream()
                                .filter(JSONObject.class::isInstance)
                                .map(JSONObject.class::cast)
                                .map(item -> AgentPendingOptionDTO.builder()
                                        .optionId(item.getString("optionId"))
                                        .label(item.getString("label"))
                                        .description(item.getString("description"))
                                        .value(item.getObject("value", Map.class))
                                        .build())
                                .toList();
                    } catch (Exception ignored) {
                        return Collections.<AgentPendingOptionDTO>emptyList();
                    }
                })
                .orElseGet(Collections::emptyList);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String code(Object enumLike) {
        if (enumLike == null) {
            return null;
        }
        try {
            return String.valueOf(enumLike.getClass().getMethod("code").invoke(enumLike));
        } catch (Exception ignored) {
            return String.valueOf(enumLike);
        }
    }
}


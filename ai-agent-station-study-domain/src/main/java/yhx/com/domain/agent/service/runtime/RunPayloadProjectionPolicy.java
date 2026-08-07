package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolResultContentModeEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.LoopRuntimeOutcomeVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.RunPayloadWorkingSetVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the de-duplicated payload working set sent to MainAgent from canonical Timeline references.
 * Required content remains complete; only metadata-only payloads stay outside the model input.
 */
public class RunPayloadProjectionPolicy {

    public RunPayloadWorkingSetVO build(RunContextStateVO state,
                                        IPayloadRepository payloadRepository) {
        Map<String, PayloadSource> sources = payloadSources(state);
        Map<String, Object> manifest = new LinkedHashMap<>();
        sources.forEach((ref, source) -> manifest.put(ref, manifestEntry(ref, source)));

        Map<String, Object> active = new LinkedHashMap<>();
        for (PayloadSource source : sources.values()) {
            Map<String, Object> projection = activeProjection(source, payloadRepository);
            if (projection == null) {
                continue;
            }
            active.put(source.payloadRef(), projection);
        }
        return RunPayloadWorkingSetVO.builder()
                .payloadManifest(manifest)
                .activePayloads(active)
                .build();
    }

    private Map<String, PayloadSource> payloadSources(RunContextStateVO state) {
        Map<String, PayloadSource> sources = new LinkedHashMap<>();
        if (state == null || state.getLoopTimeline() == null) {
            return sources;
        }
        for (RunLoopRecordVO record : state.getLoopTimeline()) {
            LoopRuntimeOutcomeVO outcome = record == null ? null : record.getRuntimeOutcome();
            String payloadRef = outcome == null ? null : outcome.getResultPayloadRef();
            if (isBlank(payloadRef)) {
                continue;
            }
            Map<String, Object> resultMetadata = resultMetadata(outcome);
            String action = record.getMainOutput() == null ? null : record.getMainOutput().getAction();
            sources.remove(payloadRef);
            sources.put(payloadRef, new PayloadSource(
                    payloadRef,
                    record.getLoopIndex(),
                    action,
                    outcome.getStatus(),
                    contentMode(resultMetadata, action),
                    string(resultMetadata.get("contentFormat")),
                    integer(resultMetadata.get("totalChars")),
                    longValue(resultMetadata.get("totalBytes"))));
        }
        return sources;
    }

    private Map<String, Object> manifestEntry(String payloadRef, PayloadSource source) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("payloadRef", payloadRef);
        entry.put("sourceLoopIndex", source.loopIndex());
        entry.put("sourceAction", source.action());
        entry.put("outcomeStatus", source.outcomeStatus());
        entry.put("contentMode", source.contentMode().code());
        entry.put("contentFormat", source.contentFormat());
        entry.put("totalChars", source.totalChars());
        entry.put("totalBytes", source.totalBytes());
        entry.values().removeIf(java.util.Objects::isNull);
        return entry;
    }

    private Map<String, Object> activeProjection(PayloadSource source,
                                                  IPayloadRepository payloadRepository) {
        if (source.contentMode() == ToolResultContentModeEnumVO.METADATA_ONLY) {
            return null;
        }
        if (payloadRepository == null) {
            return descriptor(source, "REFERENCE_ONLY", null, false);
        }
        String content = payloadRepository.findContent(source.payloadRef()).orElse(null);
        if (content == null) {
            return descriptor(source, "MISSING", null, false);
        }
        Map<String, Object> projection = descriptor(source, "FULL_TEXT", content, true);
        projection.put("totalChars", content.length());
        if (source.contentMode() == ToolResultContentModeEnumVO.SUMMARY_ONLY) {
            projection.put("summaryUnavailableFallback", "FULL_TEXT");
        }
        return projection;
    }

    private Map<String, Object> descriptor(PayloadSource source,
                                           String materialization,
                                           String content,
                                           boolean complete) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("payloadRef", source.payloadRef());
        projection.put("materialization", materialization);
        projection.put("requestedContentMode", source.contentMode().code());
        if (content != null) {
            projection.put("content", content);
        }
        projection.put("complete", complete);
        projection.put("totalChars", source.totalChars());
        projection.values().removeIf(java.util.Objects::isNull);
        return projection;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultMetadata(LoopRuntimeOutcomeVO outcome) {
        if (outcome == null || outcome.getDetails() == null) {
            return Map.of();
        }
        Object value = outcome.getDetails().get("resultMetadata");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private ToolResultContentModeEnumVO contentMode(Map<String, Object> metadata, String action) {
        String code = string(metadata.get("contentMode"));
        return ToolResultContentModeEnumVO.ofCode(code)
                .orElse("CALL_TOOL".equals(action)
                        ? ToolResultContentModeEnumVO.SUMMARY_ONLY
                        : ToolResultContentModeEnumVO.FULL_TEXT_REQUIRED);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record PayloadSource(String payloadRef,
                                 Integer loopIndex,
                                 String action,
                                 String outcomeStatus,
                                 ToolResultContentModeEnumVO contentMode,
                                 String contentFormat,
                                 Integer totalChars,
                                 Long totalBytes) {
    }
}

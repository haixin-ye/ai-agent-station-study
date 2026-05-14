package yhx.com.domain.agent.service.tool;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolArgumentContentModeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.ToolArgumentsMaterializationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolIntentVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolArgumentMaterializer {

    private final IArtifactRepository artifactRepository;
    private final IEvidenceRepository evidenceRepository;
    private final IPayloadRepository payloadRepository;

    public ToolArgumentMaterializer(IArtifactRepository artifactRepository,
                                    IEvidenceRepository evidenceRepository,
                                    IPayloadRepository payloadRepository) {
        this.artifactRepository = artifactRepository;
        this.evidenceRepository = evidenceRepository;
        this.payloadRepository = payloadRepository;
    }

    public ToolArgumentsMaterializationResultVO materialize(ToolIntentVO intent, CapabilitySpecVO capability) {
        Map<String, Object> source = new LinkedHashMap<>();
        if (capability != null && capability.getArgumentDefaults() != null) {
            source.putAll(capability.getArgumentDefaults());
        }
        if (intent != null && intent.getArguments() != null) {
            source.putAll(intent.getArguments());
        }
        List<String> artifactIds = new ArrayList<>();
        List<String> evidenceIds = new ArrayList<>();
        Map<String, Object> materialized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            MaterializedValue value = materializeValue(entry.getKey(), entry.getValue(), capability);
            if (value.failureCode() != null) {
                return ToolArgumentsMaterializationResultVO.builder()
                        .failureCode(value.failureCode())
                        .failureMessage(value.failureMessage())
                        .build();
            }
            materialized.put(value.outputKey(), value.value());
            artifactIds.addAll(value.artifactIds());
            evidenceIds.addAll(value.evidenceIds());
        }
        String argumentsRef = saveArguments(materialized);
        return ToolArgumentsMaterializationResultVO.builder()
                .arguments(materialized)
                .argumentsRef(argumentsRef)
                .materializedArtifactIds(artifactIds)
                .materializedEvidenceIds(evidenceIds)
                .build();
    }

    @SuppressWarnings("unchecked")
    private MaterializedValue materializeValue(String key, Object value, CapabilitySpecVO capability) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return MaterializedValue.success(key, value);
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        if (map.containsKey("contentSource")) {
            Object nested = map.get("contentSource");
            return materializeValue(key, nested, capability);
        }
        if (map.containsKey("evidenceSource")) {
            Object nested = map.get("evidenceSource");
            return materializeValue(key, nested, capability);
        }
        String type = string(map.get("type"));
        if ("ARTIFACT".equals(type)) {
            return materializeArtifact(outputKey(key, "contentSource", "content"), map, capability);
        }
        if ("EVIDENCE".equals(type)) {
            return materializeEvidence(outputKey(key, "evidenceSource", "evidence"), map);
        }
        if ("INLINE_VALUE".equals(type)) {
            return MaterializedValue.success(outputKey(key, "contentSource", "value"), map.get("value"));
        }
        return MaterializedValue.success(key, value);
    }

    private MaterializedValue materializeArtifact(String outputKey, Map<String, Object> source, CapabilitySpecVO capability) {
        String artifactId = string(source.get("artifactId"));
        if (artifactId == null || artifactId.isBlank()) {
            return MaterializedValue.failure("TOOL_ARGUMENT_ARTIFACT_MISSING", "artifactId is required.");
        }
        AgentArtifactEntity artifact = artifactRepository.findArtifact(artifactId).orElse(null);
        if (artifact == null) {
            return MaterializedValue.failure("TOOL_ARGUMENT_ARTIFACT_MISSING", "Artifact not found: " + artifactId);
        }
        ToolArgumentContentModeEnumVO mode = contentMode(source, capability);
        if (mode == ToolArgumentContentModeEnumVO.METADATA_ONLY) {
            return MaterializedValue.success(outputKey, Map.of("artifactId", artifactId, "title", nullToEmpty(artifact.getTitle()), "summary", nullToEmpty(artifact.getSummary())), List.of(artifactId), List.of());
        }
        if (mode == ToolArgumentContentModeEnumVO.SUMMARY_ONLY) {
            return MaterializedValue.success(outputKey, nullToEmpty(artifact.getSummary()), List.of(artifactId), List.of());
        }
        String content = artifact.getContentRef() == null ? null : payloadRepository.findContent(artifact.getContentRef()).orElse(null);
        if (content == null || content.isBlank()) {
            return MaterializedValue.failure("TOOL_ARGUMENT_ARTIFACT_MISSING", "Artifact content is required but unavailable: " + artifactId);
        }
        return MaterializedValue.success(outputKey, content, List.of(artifactId), List.of());
    }

    private MaterializedValue materializeEvidence(String outputKey, Map<String, Object> source) {
        String evidenceId = string(source.get("evidenceId"));
        if (evidenceId == null || evidenceId.isBlank()) {
            return MaterializedValue.failure("TOOL_ARGUMENT_EVIDENCE_MISSING", "evidenceId is required.");
        }
        AgentEvidenceEntity evidence = evidenceRepository.findEvidence(evidenceId).orElse(null);
        if (evidence == null) {
            return MaterializedValue.failure("TOOL_ARGUMENT_EVIDENCE_MISSING", "Evidence not found: " + evidenceId);
        }
        return MaterializedValue.success(outputKey, nullToEmpty(evidence.getSummary()), List.of(), List.of(evidenceId));
    }

    private ToolArgumentContentModeEnumVO contentMode(Map<String, Object> source, CapabilitySpecVO capability) {
        String mode = string(source.get("contentMode"));
        if (mode != null) {
            return ToolArgumentContentModeEnumVO.ofCode(mode).orElse(ToolArgumentContentModeEnumVO.FULL_TEXT_REQUIRED);
        }
        return capability == null || capability.getDefaultContentMode() == null
                ? ToolArgumentContentModeEnumVO.FULL_TEXT_REQUIRED
                : capability.getDefaultContentMode();
    }

    private String outputKey(String key, String sourceKey, String fallback) {
        return sourceKey.equals(key) ? fallback : key;
    }

    private String saveArguments(Map<String, Object> arguments) {
        if (payloadRepository == null) {
            return null;
        }
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(arguments))
                .preview("tool-arguments")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record MaterializedValue(String outputKey, Object value, List<String> artifactIds, List<String> evidenceIds,
                                     String failureCode, String failureMessage) {
        static MaterializedValue success(String outputKey, Object value) {
            return success(outputKey, value, List.of(), List.of());
        }

        static MaterializedValue success(String outputKey, Object value, List<String> artifactIds, List<String> evidenceIds) {
            return new MaterializedValue(outputKey, value, artifactIds, evidenceIds, null, null);
        }

        static MaterializedValue failure(String failureCode, String failureMessage) {
            return new MaterializedValue(null, null, List.of(), List.of(), failureCode, failureMessage);
        }
    }
}

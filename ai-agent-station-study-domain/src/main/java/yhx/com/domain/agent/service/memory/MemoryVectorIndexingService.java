package yhx.com.domain.agent.service.memory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryOutputVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryVectorIndexingService {

    private final IVectorMemoryRepository vectorMemoryRepository;
    private final IVectorIndexRepository vectorIndexRepository;
    private final IPayloadRepository payloadRepository;

    public MemoryVectorIndexingService(IVectorMemoryRepository vectorMemoryRepository,
                                       IVectorIndexRepository vectorIndexRepository,
                                       IPayloadRepository payloadRepository) {
        this.vectorMemoryRepository = vectorMemoryRepository;
        this.vectorIndexRepository = vectorIndexRepository;
        this.payloadRepository = payloadRepository;
    }

    public void indexTurnSummary(AgentTurnEntity turn, AgentTurnSummaryEntity summary, TurnSummaryOutputVO output) {
        if (turn == null || summary == null || output == null || isBlank(output.getSummary())) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "turnId", summary.getTurnId());
        putIfPresent(metadata, "runId", summary.getRunId());
        putIfPresent(metadata, "intent", output.getIntent());
        metadata.put("topics", output.getTopics() == null ? List.of() : output.getTopics());
        metadata.put("artifactRefs", output.getArtifactRefs() == null ? List.of() : output.getArtifactRefs());
        upsert(VectorCollectionTypeEnumVO.TURN_SUMMARY,
                VectorSourceTypeEnumVO.TURN_SUMMARY,
                summary.getSummaryId(),
                summary.getUserId(),
                summary.getSessionId(),
                output.getSummary(),
                output.getSummary(),
                metadata,
                firstNonNull(turn.getCompletedAt(), summary.getUpdatedAt(), summary.getCreatedAt(), LocalDateTime.now()));
    }

    public void indexConversationSummary(AgentConversationSummaryEntity summary) {
        if (summary == null || isBlank(summary.getSummaryId())) {
            return;
        }
        String text = loadPayloadText(summary.getSummaryRef());
        if (isBlank(text)) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "messageStartSeq", summary.getMessageStartSeq());
        putIfPresent(metadata, "messageEndSeq", summary.getMessageEndSeq());
        upsert(VectorCollectionTypeEnumVO.CONVERSATION_SUMMARY,
                VectorSourceTypeEnumVO.CONVERSATION_SUMMARY,
                summary.getSummaryId(),
                summary.getUserId(),
                summary.getSessionId(),
                text,
                preview(text),
                metadata,
                firstNonNull(summary.getUpdatedAt(), summary.getCreatedAt(), LocalDateTime.now()));
    }

    public void indexMemory(AgentMemoryEntity memory) {
        indexMemory(memory, Map.of());
    }

    public void indexMemory(AgentMemoryEntity memory, Map<String, Object> extraMetadata) {
        if (memory == null || isBlank(memory.getMemoryId())) {
            return;
        }
        String text = firstNonBlank(recallText(memory), firstNonBlank(loadPayloadText(memory.getContentRef()), memory.getSummary()));
        if (isBlank(text)) {
            return;
        }
        VectorCollectionTypeEnumVO collectionType = collectionType(memory.getMemoryType());
        VectorSourceTypeEnumVO sourceType = sourceType(memory.getMemoryType());
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }
        putIfPresent(metadata, "memoryType", memory.getMemoryType());
        putIfPresent(metadata, "score", memory.getScore());
        upsert(collectionType,
                sourceType,
                memory.getMemoryId(),
                memory.getUserId(),
                memory.getSessionId(),
                text,
                preview(memory.getSummary()),
                metadata,
                firstNonNull(memory.getUpdatedAt(), memory.getCreatedAt(), LocalDateTime.now()));
    }

    private void upsert(VectorCollectionTypeEnumVO collectionType,
                        VectorSourceTypeEnumVO sourceType,
                        String sourceId,
                        String userId,
                        String sessionId,
                        String text,
                        String summary,
                        Map<String, Object> metadata,
                        LocalDateTime occurredAt) {
        if (vectorMemoryRepository == null || vectorIndexRepository == null || collectionType == null || sourceType == null || isBlank(sourceId) || isBlank(text)) {
            return;
        }
        String vectorId = vectorMemoryRepository.upsert(VectorIndexRecordVO.builder()
                .collectionType(collectionType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .userId(userId)
                .sessionId(sessionId)
                .text(text)
                .summary(summary)
                .metadata(metadata == null ? Map.of() : metadata)
                .occurredAt(occurredAt == null ? LocalDateTime.now() : occurredAt)
                .build());
        vectorIndexRepository.saveOrUpdate(AgentVectorIndexEntity.builder()
                .collectionType(collectionType.name())
                .sourceType(sourceType.name())
                .sourceId(sourceId)
                .vectorId(vectorId)
                .userId(userId)
                .sessionId(sessionId)
                .status("ACTIVE")
                .indexedAt(LocalDateTime.now())
                .build());
    }

    private VectorCollectionTypeEnumVO collectionType(String memoryType) {
        return "USER_PREFERENCE".equalsIgnoreCase(memoryType)
                ? VectorCollectionTypeEnumVO.USER_PREFERENCE
                : VectorCollectionTypeEnumVO.LONG_TERM_MEMORY;
    }

    private VectorSourceTypeEnumVO sourceType(String memoryType) {
        return "USER_PREFERENCE".equalsIgnoreCase(memoryType)
                ? VectorSourceTypeEnumVO.USER_PREFERENCE
                : VectorSourceTypeEnumVO.LONG_TERM_MEMORY;
    }

    private String loadPayloadText(String payloadRef) {
        if (payloadRepository == null || isBlank(payloadRef)) {
            return null;
        }
        return payloadRepository.findPayload(payloadRef)
                .map(payload -> firstNonBlank(payload.getContent(), payload.getPreview()))
                .orElse(null);
    }

    private String recallText(AgentMemoryEntity memory) {
        if (memory == null || isBlank(memory.getMetadataJson())) {
            return null;
        }
        try {
            JSONObject object = JSON.parseObject(memory.getMetadataJson());
            return object == null ? null : object.getString("recallText");
        } catch (Exception ignored) {
            return null;
        }
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 300 ? content : content.substring(0, 300);
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (metadata != null && key != null && value != null) {
            metadata.put(key, value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return isBlank(second) ? null : second;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}

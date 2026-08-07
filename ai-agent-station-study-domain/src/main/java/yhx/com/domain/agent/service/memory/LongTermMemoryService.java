package yhx.com.domain.agent.service.memory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;

import java.util.LinkedHashMap;
import java.util.Map;

public class LongTermMemoryService {

    private final IPayloadRepository payloadRepository;
    private final IMemoryRepository memoryRepository;
    private final MemoryVectorIndexingService vectorIndexingService;

    public LongTermMemoryService(IPayloadRepository payloadRepository,
                                 IMemoryRepository memoryRepository,
                                 MemoryVectorIndexingService vectorIndexingService) {
        this.payloadRepository = payloadRepository;
        this.memoryRepository = memoryRepository;
        this.vectorIndexingService = vectorIndexingService;
    }

    public AgentMemoryEntity acceptExternalMemory(AgentMemoryEntity memory) {
        return memory;
    }

    public AgentMemoryEntity ingestExternalMemory(AgentMemoryEntity memory,
                                                   String content,
                                                   Map<String, Object> indexingMetadata) {
        validate(memory, content);
        memory.setContentRef(payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.TEXT)
                .content(content)
                .preview(preview(content))
                .build()));
        memory.setStatus(isBlank(memory.getStatus()) ? "ACTIVE" : memory.getStatus());
        memory.setMetadataJson(mergeMetadata(memory.getMetadataJson(), indexingMetadata, content));
        memoryRepository.saveLongTermMemory(memory);
        vectorIndexingService.indexMemory(memory, indexingMetadata);
        return memory;
    }

    private void validate(AgentMemoryEntity memory, String content) {
        if (memory == null) {
            throw new IllegalArgumentException("Memory is required.");
        }
        if (!"LONG_TERM_MEMORY".equalsIgnoreCase(memory.getMemoryType())
                && !"USER_PREFERENCE".equalsIgnoreCase(memory.getMemoryType())) {
            throw new IllegalArgumentException("Evaluation memory type must be LONG_TERM_MEMORY or USER_PREFERENCE.");
        }
        if (isBlank(content)) {
            throw new IllegalArgumentException("Memory content is required.");
        }
        if (payloadRepository == null || memoryRepository == null || vectorIndexingService == null) {
            throw new IllegalStateException("Memory ingestion dependencies are not configured.");
        }
    }

    private String mergeMetadata(String existingJson, Map<String, Object> extra, String content) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (!isBlank(existingJson)) {
            try {
                JSONObject object = JSON.parseObject(existingJson);
                if (object != null) {
                    merged.putAll(object.getInnerMap());
                }
            } catch (Exception ignored) {
                // Preserve a valid metadata object for externally managed evaluation records.
            }
        }
        if (extra != null) {
            merged.putAll(extra);
        }
        merged.putIfAbsent("recallText", content);
        return JSON.toJSONString(merged);
    }

    private String preview(String content) {
        String normalized = content == null ? null : content.trim();
        return normalized == null || normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

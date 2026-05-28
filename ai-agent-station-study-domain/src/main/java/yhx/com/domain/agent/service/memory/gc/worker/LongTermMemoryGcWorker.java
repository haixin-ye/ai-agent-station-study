package yhx.com.domain.agent.service.memory.gc.worker;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.ExtractedMemoryVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryExtractionInputVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryExtractionOutputVO;
import yhx.com.domain.agent.service.memory.MemoryManager;
import yhx.com.domain.agent.service.node.memoryextraction.MemoryExtractionNodeService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LongTermMemoryGcWorker implements MemoryGcTaskWorker {

    private static final int MAX_FAILURE_MESSAGE_CHARS = 4000;
    private static final Pattern EXPLICIT_USER_NAME_PATTERN = Pattern.compile(
            "(?:我叫|我的名字是|我的昵称是|我的称呼是|叫我)([^，。,.!?！？\\s]{1,32})");

    private final ITurnRepository turnRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IPayloadRepository payloadRepository;
    private final MemoryManager memoryManager;
    private final MemoryExtractionNodeService nodeService;

    public LongTermMemoryGcWorker(ITurnRepository turnRepository,
                                  IMemoryTaskRepository taskRepository,
                                  IPayloadRepository payloadRepository,
                                  MemoryManager memoryManager,
                                  MemoryExtractionNodeService nodeService) {
        this.turnRepository = turnRepository;
        this.taskRepository = taskRepository;
        this.payloadRepository = payloadRepository;
        this.memoryManager = memoryManager;
        this.nodeService = nodeService;
    }

    @Override
    public String taskType() {
        return MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name();
    }

    @Override
    public void handle(String taskId) {
        try {
            taskRepository.markRunning(taskId);
            AgentMemoryTaskEntity task = taskRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Memory task not found: " + taskId));
            AgentTurnEntity turn = turnRepository.findByTurnId(task.getTurnId())
                    .orElseThrow(() -> new IllegalArgumentException("Turn not found: " + task.getTurnId()));
            String userInput = loadPayloadContent(turn.getUserPayloadRef());
            String finalAnswer = loadPayloadContent(turn.getAssistantPayloadRef());
            String turnSummary = loadPayloadContent(task.getInputRef());
            MemoryExtractionOutputVO output = nodeService.extract(MemoryExtractionInputVO.builder()
                    .runId(turn.getRunId())
                    .sessionId(turn.getSessionId())
                    .turnId(turn.getTurnId())
                    .userInput(userInput)
                    .finalAnswer(finalAnswer)
                    .turnSummary(turnSummary)
                    .build(), turn.getAgentId(), null);
            List<ExtractedMemoryVO> memories = output == null ? List.of() : output.getMemories();
            saveMemories(turn, enrichWithDeterministicIdentityFallback(userInput, memories));
            taskRepository.markSucceeded(taskId, null);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, "LONG_TERM_MEMORY_EXTRACTION_FAILED", truncate(e.getMessage()));
        }
    }

    private List<ExtractedMemoryVO> enrichWithDeterministicIdentityFallback(String userInput, List<ExtractedMemoryVO> memories) {
        if (memories != null && memories.stream().anyMatch(memory -> memory != null && !isBlank(memory.getSummary()))) {
            return memories;
        }
        String displayName = explicitDisplayName(userInput);
        if (isBlank(displayName)) {
            return memories == null ? List.of() : memories;
        }
        List<ExtractedMemoryVO> enriched = new ArrayList<>();
        if (memories != null) {
            enriched.addAll(memories);
        }
        enriched.add(ExtractedMemoryVO.builder()
                .memoryType("LONG_TERM_MEMORY")
                .summary("用户的称呼或昵称是" + displayName + "。")
                .content("用户明确表示自己叫" + displayName + "，后续可以用该称呼识别用户。")
                .recallText("用户姓名、名字、称呼、昵称、我叫什么、我的名字是" + displayName + "。用户希望被称为" + displayName + "。")
                .score(new BigDecimal("0.90"))
                .reason("用户输入中包含明确的自我称呼表达。")
                .build());
        return enriched;
    }

    private String explicitDisplayName(String userInput) {
        if (isBlank(userInput)) {
            return null;
        }
        Matcher matcher = EXPLICIT_USER_NAME_PATTERN.matcher(userInput);
        if (!matcher.find()) {
            return null;
        }
        String name = matcher.group(1);
        if (name == null) {
            return null;
        }
        return name.trim();
    }

    private void saveMemories(AgentTurnEntity turn, List<ExtractedMemoryVO> memories) {
        if (memories == null || memoryManager == null) {
            return;
        }
        for (ExtractedMemoryVO item : memories) {
            if (item == null || isBlank(item.getSummary())) {
                continue;
            }
            String memoryType = normalizeMemoryType(item.getMemoryType());
            memoryManager.saveLongTermMemory(AgentMemoryEntity.builder()
                    .userId(turn.getUserId())
                    .sessionId(turn.getSessionId())
                    .memoryType(memoryType)
                    .summary(item.getSummary())
                    .contentRef(saveContentIfPresent(item))
                    .score(item.getScore() == null ? new BigDecimal("0.50") : item.getScore())
                    .status("ACTIVE")
                    .sourceRunId(turn.getRunId())
                    .sourceTurnId(turn.getTurnId())
                    .lastSeenAt(LocalDateTime.now())
                    .metadataJson(metadataJson(item))
                    .build());
        }
    }

    private String saveContentIfPresent(ExtractedMemoryVO item) {
        String content = firstNonBlank(item == null ? null : item.getContent(), item == null ? null : item.getSummary());
        if (payloadRepository == null || item == null || isBlank(content)) {
            return null;
        }
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.TEXT)
                .content(content)
                .preview(preview(content))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String metadataJson(ExtractedMemoryVO item) {
        if (item == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("recallText", recallText(item));
        if (!isBlank(item.getReason())) {
            metadata.put("reason", item.getReason());
        }
        return metadata.isEmpty() ? null : JSON.toJSONString(metadata);
    }

    private String recallText(ExtractedMemoryVO item) {
        String recallText = item == null ? null : item.getRecallText();
        if (!isBlank(recallText)) {
            return recallText;
        }
        String summary = item == null ? null : item.getSummary();
        String content = item == null ? null : item.getContent();
        String memoryType = normalizeMemoryType(item == null ? null : item.getMemoryType());
        String base = firstNonBlank(summary, content);
        if (isBlank(base)) {
            return null;
        }
        if ("USER_PREFERENCE".equals(memoryType)) {
            return "用户偏好、回答风格、喜欢、希望以后、默认回答方式：" + base;
        }
        return "用户信息、用户画像、身份、称呼、名字、家乡、居住地、所在城市、来自哪里、个人背景：" + base;
    }

    private String normalizeMemoryType(String memoryType) {
        return "USER_PREFERENCE".equalsIgnoreCase(memoryType) ? "USER_PREFERENCE" : "LONG_TERM_MEMORY";
    }

    private String loadPayloadContent(String payloadRef) {
        if (payloadRepository == null || payloadRef == null || payloadRef.isBlank()) {
            return null;
        }
        return payloadRepository.findPayload(payloadRef)
                .map(payload -> firstNonBlank(payload.getContent(), payload.getPreview()))
                .orElse(null);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_FAILURE_MESSAGE_CHARS ? message : message.substring(0, MAX_FAILURE_MESSAGE_CHARS);
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return isBlank(second) ? null : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

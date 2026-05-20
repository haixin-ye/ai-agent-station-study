package yhx.com.domain.agent.service.interaction;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PendingInputManager {

    private final IPendingInputRepository pendingInputRepository;
    private final IPayloadRepository payloadRepository;

    public PendingInputManager(IPendingInputRepository pendingInputRepository, IPayloadRepository payloadRepository) {
        this.pendingInputRepository = pendingInputRepository;
        this.payloadRepository = payloadRepository;
    }

    public String create(PendingInputCreateCommand command) {
        AskUserRequestVO askUserRequest = command.getAskUserRequest();
        if (askUserRequest != null) {
            askUserRequest.setOptions(normalizeOptions(askUserRequest.getOptions()));
        }
        String optionsRef = savePayload(askUserRequest == null ? null : askUserRequest.getOptions());
        String continuationRef = savePayload(command.getContinuation());
        return pendingInputRepository.createPendingInput(AgentPendingInputEntity.builder()
                .runId(command.getRunId())
                .sourceComponent(command.getSourceComponent())
                .pendingType(command.getPendingType())
                .inputMode(askUserRequest == null ? null : askUserRequest.getInputMode())
                .status(PendingInputStatusEnumVO.PENDING.code())
                .question(askUserRequest == null ? null : askUserRequest.getQuestion())
                .optionsRef(optionsRef)
                .continuationRef(continuationRef)
                .createdAt(LocalDateTime.now())
                .expiresAt(command.getExpiresAt())
                .build());
    }

    private List<Map<String, Object>> normalizeOptions(List<Map<String, Object>> options) {
        if (options == null || options.isEmpty()) {
            return options;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> option : options) {
            if (option == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(option);
            String optionId = firstNonBlank(stringValue(item.get("optionId")), stringValue(item.get("id")));
            if (optionId == null) {
                optionId = "option-" + index;
            }
            item.put("optionId", optionId);
            item.putIfAbsent("id", optionId);
            item.putIfAbsent("label", "Option " + index);
            normalized.add(item);
            index++;
        }
        return normalized;
    }

    public Optional<AgentPendingInputEntity> findActiveByRunId(String runId) {
        return pendingInputRepository.findActivePendingInput(runId);
    }

    public Optional<AgentPendingInputEntity> findByPendingId(String pendingId) {
        return pendingInputRepository.findByPendingId(pendingId);
    }

    public void markAnswered(String pendingId, String userAnswerRef) {
        pendingInputRepository.markAnswered(pendingId, userAnswerRef);
    }

    public void markCancelled(String pendingId) {
        pendingInputRepository.markCancelled(pendingId);
    }

    public void markExpired(String pendingId) {
        pendingInputRepository.markExpired(pendingId);
    }

    private String savePayload(Object value) {
        if (value == null || payloadRepository == null) {
            return null;
        }
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(value))
                .preview("pending-input")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

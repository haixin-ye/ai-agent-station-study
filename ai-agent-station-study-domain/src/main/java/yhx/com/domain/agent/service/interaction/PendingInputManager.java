package yhx.com.domain.agent.service.interaction;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;

import java.time.LocalDateTime;
import java.util.Optional;

public class PendingInputManager {

    private final IPendingInputRepository pendingInputRepository;
    private final IPayloadRepository payloadRepository;

    public PendingInputManager(IPendingInputRepository pendingInputRepository, IPayloadRepository payloadRepository) {
        this.pendingInputRepository = pendingInputRepository;
        this.payloadRepository = payloadRepository;
    }

    public String create(PendingInputCreateCommand command) {
        String optionsRef = savePayload(command.getAskUserRequest() == null ? null : command.getAskUserRequest().getOptions());
        String continuationRef = savePayload(command.getContinuation());
        return pendingInputRepository.createPendingInput(AgentPendingInputEntity.builder()
                .runId(command.getRunId())
                .sourceComponent(command.getSourceComponent())
                .pendingType(command.getPendingType())
                .inputMode(command.getAskUserRequest() == null ? null : command.getAskUserRequest().getInputMode())
                .status(PendingInputStatusEnumVO.PENDING.code())
                .question(command.getAskUserRequest() == null ? null : command.getAskUserRequest().getQuestion())
                .optionsRef(optionsRef)
                .continuationRef(continuationRef)
                .createdAt(LocalDateTime.now())
                .expiresAt(command.getExpiresAt())
                .build());
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
}

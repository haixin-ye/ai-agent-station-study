package yhx.com.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputConsumptionRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputConsumptionResultVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.service.interaction.PendingInputConsumptionConflictException;

import java.time.LocalDateTime;

@Repository
public class PendingInputConsumptionRepository implements IPendingInputConsumptionRepository {

    private final IPendingInputRepository pendingInputRepository;
    private final IPayloadRepository payloadRepository;

    public PendingInputConsumptionRepository(IPendingInputRepository pendingInputRepository,
                                             IPayloadRepository payloadRepository) {
        this.pendingInputRepository = pendingInputRepository;
        this.payloadRepository = payloadRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PendingInputConsumptionResultVO consume(String pendingId,
                                                   String runId,
                                                   UserAnswerVO answer,
                                                   boolean cancelled) {
        String answerRef = cancelled ? null : saveAnswer(answer);
        int affected = cancelled
                ? pendingInputRepository.markCancelled(pendingId, runId)
                : pendingInputRepository.markAnswered(pendingId, runId, answerRef);
        if (affected != 1) {
            throw new PendingInputConsumptionConflictException(
                    "Pending input was already resolved, expired, or belongs to another Run.");
        }
        return PendingInputConsumptionResultVO.builder()
                .consumed(true)
                .userAnswerRef(answerRef)
                .build();
    }

    private String saveAnswer(UserAnswerVO answer) {
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(answer, SerializerFeature.DisableCircularReferenceDetect))
                .preview("user-answer")
                .createdAt(LocalDateTime.now())
                .build());
    }
}

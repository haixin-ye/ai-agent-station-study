package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.infrastructure.dao.IAgentPayloadDao;
import yhx.com.infrastructure.dao.po.AgentPayloadPO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PayloadRepository implements IPayloadRepository {

    private static final int PREVIEW_MAX_CHARS = 200;

    @Resource
    private IAgentPayloadDao agentPayloadDao;

    @Override
    public String savePayload(AgentPayloadEntity payload) {
        preparePayload(payload);
        agentPayloadDao.insert(toPO(payload));
        return payload.getPayloadId();
    }

    @Override
    public String saveOrUpdatePayload(AgentPayloadEntity payload) {
        preparePayload(payload);
        agentPayloadDao.upsert(toPO(payload));
        return payload.getPayloadId();
    }

    private void preparePayload(AgentPayloadEntity payload) {
        if (payload.getPayloadId() == null || payload.getPayloadId().isBlank()) {
            payload.setPayloadId("payload-" + UUID.randomUUID());
        }
        if (payload.getCreatedAt() == null) {
            payload.setCreatedAt(LocalDateTime.now());
        }
        if (payload.getContentSha256() == null && payload.getContent() != null) {
            payload.setContentSha256(sha256(payload.getContent()));
        }
        if (payload.getPreview() == null && payload.getContent() != null) {
            payload.setPreview(payload.getContent().substring(0, Math.min(PREVIEW_MAX_CHARS, payload.getContent().length())));
        }
    }

    @Override
    public Optional<AgentPayloadEntity> findPayload(String payloadId) {
        return Optional.ofNullable(agentPayloadDao.queryByPayloadId(payloadId)).map(this::toEntity);
    }

    private AgentPayloadPO toPO(AgentPayloadEntity entity) {
        return AgentPayloadPO.builder()
                .payloadId(entity.getPayloadId())
                .payloadType(entity.getPayloadType() == null ? PayloadTypeEnumVO.TEXT.code() : entity.getPayloadType().code())
                .storageType("DB")
                .content(entity.getContent())
                .contentSha256(entity.getContentSha256())
                .preview(entity.getPreview())
                .compressed(0)
                .encrypted(0)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AgentPayloadEntity toEntity(AgentPayloadPO po) {
        return AgentPayloadEntity.builder()
                .payloadId(po.getPayloadId())
                .payloadType(PayloadTypeEnumVO.ofCode(po.getPayloadType()).orElse(PayloadTypeEnumVO.TEXT))
                .content(po.getContent())
                .contentSha256(po.getContentSha256())
                .preview(po.getPreview())
                .createdAt(po.getCreatedAt())
                .build();
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }
}

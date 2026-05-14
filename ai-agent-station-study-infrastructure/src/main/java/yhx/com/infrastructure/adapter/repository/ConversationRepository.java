package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import yhx.com.infrastructure.dao.IAgentMessageDao;
import yhx.com.infrastructure.dao.IAgentSessionDao;
import yhx.com.infrastructure.dao.po.AgentMessagePO;
import yhx.com.infrastructure.dao.po.AgentSessionPO;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class ConversationRepository implements IConversationRepository {

    @Resource
    private IAgentSessionDao agentSessionDao;

    @Resource
    private IAgentMessageDao agentMessageDao;

    @Override
    public String createSession(AgentSessionEntity session) {
        if (session.getSessionId() == null || session.getSessionId().isBlank()) {
            session.setSessionId("session-" + UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.getCreatedAt() == null) {
            session.setCreatedAt(now);
        }
        if (session.getUpdatedAt() == null) {
            session.setUpdatedAt(now);
        }
        agentSessionDao.insert(toSessionPO(session));
        return session.getSessionId();
    }

    @Override
    public Optional<AgentSessionEntity> findSession(String sessionId) {
        return Optional.ofNullable(agentSessionDao.queryBySessionId(sessionId)).map(this::toSessionEntity);
    }

    @Override
    public void appendMessage(AgentMessageEntity message) {
        if (message.getMessageId() == null || message.getMessageId().isBlank()) {
            message.setMessageId("message-" + UUID.randomUUID());
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }
        agentMessageDao.insert(toMessagePO(message));
    }

    @Override
    public List<AgentMessageEntity> listRecentVisibleMessages(String sessionId, int limit) {
        List<AgentMessagePO> rows = agentMessageDao.listRecentVisibleBySessionId(sessionId, limit);
        Collections.reverse(rows);
        return rows.stream().map(this::toMessageEntity).collect(Collectors.toList());
    }

    private AgentSessionPO toSessionPO(AgentSessionEntity entity) {
        return AgentSessionPO.builder()
                .sessionId(entity.getSessionId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentSessionEntity toSessionEntity(AgentSessionPO po) {
        return AgentSessionEntity.builder()
                .sessionId(po.getSessionId())
                .userId(po.getUserId())
                .title(po.getTitle())
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private AgentMessagePO toMessagePO(AgentMessageEntity entity) {
        return AgentMessagePO.builder()
                .messageId(entity.getMessageId())
                .sessionId(entity.getSessionId())
                .runId(entity.getRunId())
                .role(entity.getRole().code())
                .contentRef(entity.getContentRef())
                .metadataRef(entity.getMetadataRef())
                .visibleToUser(Boolean.TRUE.equals(entity.getVisibleToUser()) ? 1 : 0)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AgentMessageEntity toMessageEntity(AgentMessagePO po) {
        return AgentMessageEntity.builder()
                .messageId(po.getMessageId())
                .sessionId(po.getSessionId())
                .runId(po.getRunId())
                .role(MessageRoleEnumVO.ofCode(po.getRole()).orElse(MessageRoleEnumVO.ASSISTANT))
                .contentRef(po.getContentRef())
                .metadataRef(po.getMetadataRef())
                .visibleToUser(po.getVisibleToUser() != null && po.getVisibleToUser() == 1)
                .createdAt(po.getCreatedAt())
                .build();
    }
}

package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.INodePromptRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentNodePromptEntity;
import yhx.com.infrastructure.dao.IAgentNodePromptDao;
import yhx.com.infrastructure.dao.po.AgentNodePromptPO;

import java.util.List;
import java.util.Optional;

@Repository
public class NodePromptRepository implements INodePromptRepository {

    @Resource
    private IAgentNodePromptDao agentNodePromptDao;

    @Override
    public List<AgentNodePromptEntity> listEnabledPrompts(String agentId, String nodeCode) {
        return agentNodePromptDao.listEnabled(agentId, nodeCode).stream().map(this::toEntity).toList();
    }

    @Override
    public Optional<AgentNodePromptEntity> findPromptByVersion(String agentId, String nodeCode, String promptVersion) {
        return Optional.ofNullable(agentNodePromptDao.queryByVersion(agentId, nodeCode, promptVersion)).map(this::toEntity);
    }

    private AgentNodePromptEntity toEntity(AgentNodePromptPO po) {
        return AgentNodePromptEntity.builder()
                .promptId(po.getPromptId())
                .agentId(po.getAgentId())
                .nodeCode(po.getNodeCode())
                .promptVersion(po.getPromptVersion())
                .contentRef(po.getContentRef())
                .enabled(po.getEnabled() != null && po.getEnabled() == 1)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}

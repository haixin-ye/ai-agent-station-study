package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IRunTranscriptRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTranscriptEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.TranscriptBlockTypeEnumVO;
import yhx.com.infrastructure.dao.IAgentRunTranscriptDao;
import yhx.com.infrastructure.dao.po.AgentRunTranscriptPO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class RunTranscriptRepository implements IRunTranscriptRepository {

    @Resource
    private IAgentRunTranscriptDao agentRunTranscriptDao;

    @Override
    public String appendBlock(AgentRunTranscriptEntity block) {
        normalizeBlock(block);
        agentRunTranscriptDao.insert(toPO(block));
        return block.getBlockId();
    }

    @Override
    public List<AgentRunTranscriptEntity> listRunBlocks(String runId) {
        return agentRunTranscriptDao.listByRunId(runId).stream().map(this::toEntity).toList();
    }

    @Override
    public List<AgentRunTranscriptEntity> listBlocksForCompaction(String runId, Long beforeSeq) {
        return agentRunTranscriptDao.listForCompaction(runId, beforeSeq).stream().map(this::toEntity).toList();
    }

    @Override
    public String appendCompactionSummary(AgentRunTranscriptEntity block) {
        normalizeBlock(block);
        block.setBlockType(TranscriptBlockTypeEnumVO.COMPACTION_SUMMARY);
        block.setCompactable(false);
        agentRunTranscriptDao.insert(toPO(block));
        return block.getBlockId();
    }

    private void normalizeBlock(AgentRunTranscriptEntity block) {
        if (block.getBlockId() == null || block.getBlockId().isBlank()) {
            block.setBlockId("transcript-" + UUID.randomUUID());
        }
        if (block.getCreatedAt() == null) {
            block.setCreatedAt(LocalDateTime.now());
        }
        if (block.getBlockType() == null) {
            block.setBlockType(TranscriptBlockTypeEnumVO.RUNTIME_EVENT);
        }
        if (block.getCompactable() == null) {
            block.setCompactable(true);
        }
        if (block.getSeq() == null) {
            block.setSeq(System.currentTimeMillis());
        }
    }

    private AgentRunTranscriptPO toPO(AgentRunTranscriptEntity entity) {
        return AgentRunTranscriptPO.builder()
                .blockId(entity.getBlockId())
                .runId(entity.getRunId())
                .seq(entity.getSeq())
                .blockType(entity.getBlockType().code())
                .payloadRef(entity.getPayloadRef())
                .compactable(Boolean.TRUE.equals(entity.getCompactable()) ? 1 : 0)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AgentRunTranscriptEntity toEntity(AgentRunTranscriptPO po) {
        return AgentRunTranscriptEntity.builder()
                .blockId(po.getBlockId())
                .runId(po.getRunId())
                .seq(po.getSeq())
                .blockType(TranscriptBlockTypeEnumVO.ofCode(po.getBlockType()).orElse(TranscriptBlockTypeEnumVO.RUNTIME_EVENT))
                .payloadRef(po.getPayloadRef())
                .compactable(po.getCompactable() != null && po.getCompactable() == 1)
                .createdAt(po.getCreatedAt())
                .build();
    }
}

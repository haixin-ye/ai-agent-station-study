package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentRunTranscriptEntity;

import java.util.List;

public interface IRunTranscriptRepository {

    String appendBlock(AgentRunTranscriptEntity block);

    List<AgentRunTranscriptEntity> listRunBlocks(String runId);

    List<AgentRunTranscriptEntity> listBlocksForCompaction(String runId, Long beforeSeq);

    String appendCompactionSummary(AgentRunTranscriptEntity block);
}

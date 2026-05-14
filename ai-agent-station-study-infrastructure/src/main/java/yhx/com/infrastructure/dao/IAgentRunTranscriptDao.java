package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentRunTranscriptPO;

import java.util.List;

@Mapper
public interface IAgentRunTranscriptDao {
    int insert(AgentRunTranscriptPO block);
    List<AgentRunTranscriptPO> listByRunId(String runId);
    List<AgentRunTranscriptPO> listForCompaction(@Param("runId") String runId, @Param("beforeSeq") Long beforeSeq);
}

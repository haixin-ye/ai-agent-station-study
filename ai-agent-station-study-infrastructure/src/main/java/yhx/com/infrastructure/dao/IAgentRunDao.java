package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentRunPO;

@Mapper
public interface IAgentRunDao {

    int insert(AgentRunPO run);

    int updatePhase(@Param("runId") String runId, @Param("phase") String phase);

    int updateStatus(@Param("runId") String runId, @Param("status") String status, @Param("failureCode") String failureCode);

    int updateFinalAnswerRef(@Param("runId") String runId, @Param("finalAnswerRef") String finalAnswerRef);

    AgentRunPO queryByRunId(String runId);
}

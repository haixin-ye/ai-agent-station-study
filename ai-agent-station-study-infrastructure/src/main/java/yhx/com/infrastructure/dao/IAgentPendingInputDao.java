package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentPendingInputPO;

@Mapper
public interface IAgentPendingInputDao {

    int insert(AgentPendingInputPO pendingInput);

    AgentPendingInputPO queryActiveByRunId(String runId);

    AgentPendingInputPO queryByPendingId(String pendingId);

    int markAnswered(@Param("pendingId") String pendingId,
                     @Param("runId") String runId,
                     @Param("userAnswerRef") String userAnswerRef);

    int markCancelled(@Param("pendingId") String pendingId, @Param("runId") String runId);

    int markExpired(@Param("pendingId") String pendingId, @Param("runId") String runId);
}

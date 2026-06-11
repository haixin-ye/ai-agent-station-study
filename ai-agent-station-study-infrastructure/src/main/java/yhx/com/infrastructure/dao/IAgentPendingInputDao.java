package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentPendingInputPO;

@Mapper
public interface IAgentPendingInputDao {

    int insert(AgentPendingInputPO pendingInput);

    AgentPendingInputPO queryActiveByRunId(String runId);

    AgentPendingInputPO queryByPendingId(String pendingId);

    int markAnswered(@Param("pendingId") String pendingId, @Param("userAnswerRef") String userAnswerRef);

    int markCancelled(String pendingId);

    int markExpired(String pendingId);
}

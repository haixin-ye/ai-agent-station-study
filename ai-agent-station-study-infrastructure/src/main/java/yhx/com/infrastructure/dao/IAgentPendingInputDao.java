package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentPendingInputPO;

@Mapper
public interface IAgentPendingInputDao {

    int insert(AgentPendingInputPO pendingInput);

    AgentPendingInputPO queryActiveByRunId(String runId);

    int markAnswered(@Param("pendingId") String pendingId, @Param("userAnswerRef") String userAnswerRef);
}

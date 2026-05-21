package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentTurnPO;

import java.util.List;

@Mapper
public interface IAgentTurnDao {

    int insert(AgentTurnPO turn);

    Long nextTurnNo(@Param("sessionId") String sessionId);

    AgentTurnPO queryByTurnId(@Param("turnId") String turnId);

    AgentTurnPO queryByRunId(@Param("runId") String runId);

    List<AgentTurnPO> listRecentCompleted(@Param("sessionId") String sessionId, @Param("limit") int limit);

    List<AgentTurnPO> listCompletedBefore(@Param("sessionId") String sessionId, @Param("beforeTurnNo") Long beforeTurnNo, @Param("limit") int limit);
}

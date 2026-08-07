package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentRunContextPO;

@Mapper
public interface IAgentRunContextDao {
    int insert(AgentRunContextPO context);

    int updateWithVersion(@Param("context") AgentRunContextPO context,
                          @Param("expectedVersion") long expectedVersion);

    AgentRunContextPO queryByRunId(String runId);
}

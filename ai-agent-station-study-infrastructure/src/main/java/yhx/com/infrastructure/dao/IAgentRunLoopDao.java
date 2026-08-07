package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentRunLoopPO;

import java.util.List;

@Mapper
public interface IAgentRunLoopDao {
    int upsert(AgentRunLoopPO loop);

    AgentRunLoopPO query(@Param("runId") String runId, @Param("loopIndex") Integer loopIndex);

    List<AgentRunLoopPO> listByRunId(String runId);
}

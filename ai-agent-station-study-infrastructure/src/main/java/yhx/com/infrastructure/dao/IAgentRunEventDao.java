package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentRunEventPO;

import java.util.List;

@Mapper
public interface IAgentRunEventDao {

    int insert(AgentRunEventPO event);

    List<AgentRunEventPO> listUserVisibleByRunId(@Param("runId") String runId, @Param("limit") int limit);
}

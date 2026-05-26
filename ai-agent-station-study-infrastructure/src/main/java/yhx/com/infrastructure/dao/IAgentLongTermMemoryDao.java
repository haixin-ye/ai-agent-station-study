package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentLongTermMemoryPO;

import java.util.List;

@Mapper
public interface IAgentLongTermMemoryDao {
    int insert(AgentLongTermMemoryPO memory);
    AgentLongTermMemoryPO queryByMemoryId(@Param("memoryId") String memoryId);
    List<AgentLongTermMemoryPO> listCandidates(@Param("userId") String userId, @Param("sessionId") String sessionId, @Param("limit") int limit);
    List<AgentLongTermMemoryPO> listActiveByUser(@Param("userId") String userId, @Param("sessionId") String sessionId, @Param("limit") int limit);
}

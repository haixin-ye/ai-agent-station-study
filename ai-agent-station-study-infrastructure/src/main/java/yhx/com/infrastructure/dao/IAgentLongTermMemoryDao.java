package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentLongTermMemoryPO;

import java.util.List;

@Mapper
public interface IAgentLongTermMemoryDao {
    int insert(AgentLongTermMemoryPO memory);
    List<AgentLongTermMemoryPO> listCandidates(@Param("userId") String userId, @Param("sessionId") String sessionId, @Param("limit") int limit);
}

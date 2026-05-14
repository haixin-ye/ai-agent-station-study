package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentMessagePO;

import java.util.List;

@Mapper
public interface IAgentMessageDao {

    int insert(AgentMessagePO message);

    List<AgentMessagePO> listRecentVisibleBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);
}

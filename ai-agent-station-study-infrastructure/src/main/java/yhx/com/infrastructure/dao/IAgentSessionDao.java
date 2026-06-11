package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentSessionPO;

@Mapper
public interface IAgentSessionDao {

    int insert(AgentSessionPO session);

    AgentSessionPO queryBySessionId(String sessionId);
}

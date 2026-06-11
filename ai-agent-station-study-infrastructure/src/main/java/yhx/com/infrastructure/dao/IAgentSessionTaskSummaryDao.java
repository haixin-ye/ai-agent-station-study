package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentSessionTaskSummaryPO;

@Mapper
public interface IAgentSessionTaskSummaryDao {

    int insert(AgentSessionTaskSummaryPO summary);

    AgentSessionTaskSummaryPO queryActiveBySessionId(@Param("sessionId") String sessionId);

    Integer queryMaxVersionNo(@Param("sessionId") String sessionId);

    int updateActiveSuperseded(@Param("sessionId") String sessionId);
}

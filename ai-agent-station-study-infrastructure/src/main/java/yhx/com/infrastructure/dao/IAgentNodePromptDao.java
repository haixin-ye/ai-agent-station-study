package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentNodePromptPO;

import java.util.List;

@Mapper
public interface IAgentNodePromptDao {
    List<AgentNodePromptPO> listEnabled(@Param("agentId") String agentId, @Param("nodeCode") String nodeCode);
    AgentNodePromptPO queryByVersion(@Param("agentId") String agentId, @Param("nodeCode") String nodeCode, @Param("promptVersion") String promptVersion);
}

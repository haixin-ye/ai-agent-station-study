package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentRagQueryPO;

@Mapper
public interface IAgentRagQueryDao {
    int insert(AgentRagQueryPO query);
}

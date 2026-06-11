package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentMemoryEventPO;

@Mapper
public interface IAgentMemoryEventDao {
    int insert(AgentMemoryEventPO event);
}

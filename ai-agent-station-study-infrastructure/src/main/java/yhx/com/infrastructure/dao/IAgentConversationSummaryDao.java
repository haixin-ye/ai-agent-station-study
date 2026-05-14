package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentConversationSummaryPO;

@Mapper
public interface IAgentConversationSummaryDao {
    int insert(AgentConversationSummaryPO summary);
}

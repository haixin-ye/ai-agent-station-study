package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentRunAuditPO;

@Mapper
public interface IAgentRunAuditDao {

    int insert(AgentRunAuditPO audit);
}

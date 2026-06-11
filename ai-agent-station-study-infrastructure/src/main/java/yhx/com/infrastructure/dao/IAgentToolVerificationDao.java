package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentToolVerificationPO;

@Mapper
public interface IAgentToolVerificationDao {
    int insert(AgentToolVerificationPO verification);
}

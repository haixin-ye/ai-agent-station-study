package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentModelProfilePO;

@Mapper
public interface IAgentModelProfileDao {

    AgentModelProfilePO queryActiveByProfileId(@Param("modelProfileId") String modelProfileId);
}

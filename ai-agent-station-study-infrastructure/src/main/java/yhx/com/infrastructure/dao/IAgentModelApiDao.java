package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentModelApiPO;

@Mapper
public interface IAgentModelApiDao {

    AgentModelApiPO queryActiveByApiId(@Param("apiId") String apiId);
}

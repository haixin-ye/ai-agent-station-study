package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentNodeModelBindingPO;

import java.util.List;

@Mapper
public interface IAgentNodeModelBindingDao {

    AgentNodeModelBindingPO queryActiveByNodeCode(@Param("nodeCode") String nodeCode);

    List<AgentNodeModelBindingPO> listActive();
}

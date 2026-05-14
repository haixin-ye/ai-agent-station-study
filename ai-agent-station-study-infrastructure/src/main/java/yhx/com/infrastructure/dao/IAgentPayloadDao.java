package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentPayloadPO;

@Mapper
public interface IAgentPayloadDao {

    int insert(AgentPayloadPO payload);

    AgentPayloadPO queryByPayloadId(String payloadId);
}

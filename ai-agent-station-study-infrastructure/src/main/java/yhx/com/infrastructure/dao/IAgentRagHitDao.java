package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentRagHitPO;

import java.util.List;

@Mapper
public interface IAgentRagHitDao {
    int insert(AgentRagHitPO hit);
    List<AgentRagHitPO> listByRunId(String runId);
}

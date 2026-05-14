package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentRagQueryPO;

import java.util.List;

@Mapper
public interface IAgentRagQueryDao {
    int insert(AgentRagQueryPO query);

    int updateStatus(@Param("ragQueryId") String ragQueryId,
                     @Param("status") String status,
                     @Param("failureCode") String failureCode,
                     @Param("failureMessage") String failureMessage);

    List<AgentRagQueryPO> listByRunId(String runId);
}

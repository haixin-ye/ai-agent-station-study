package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentRunTracePO;

import java.util.List;

@Mapper
public interface IAgentRunTraceDao {

    int insert(AgentRunTracePO trace);

    Long queryMaxSeqByRunId(@Param("runId") String runId);

    List<AgentRunTracePO> listByRunId(@Param("runId") String runId, @Param("limit") int limit);

    List<AgentRunTracePO> listByRunIdAfter(@Param("runId") String runId,
                                           @Param("lastSeq") long lastSeq,
                                           @Param("limit") int limit);
}

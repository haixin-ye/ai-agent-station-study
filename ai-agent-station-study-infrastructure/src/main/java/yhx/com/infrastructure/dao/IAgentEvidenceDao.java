package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentEvidencePO;

import java.util.List;

@Mapper
public interface IAgentEvidenceDao {

    int insert(AgentEvidencePO evidence);

    AgentEvidencePO queryByEvidenceId(String evidenceId);

    List<AgentEvidencePO> listByRunId(String runId);

    int markUsedByFinal(@Param("evidenceId") String evidenceId);
}

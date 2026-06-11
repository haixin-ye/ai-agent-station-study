package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentArtifactPO;

import java.util.List;

@Mapper
public interface IAgentArtifactDao {

    int insert(AgentArtifactPO artifact);

    AgentArtifactPO queryByArtifactId(String artifactId);

    List<AgentArtifactPO> listCandidates(@Param("sessionId") String sessionId,
                                         @Param("keyword") String keyword,
                                         @Param("limit") int limit);
}

package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentArtifactPO;

@Mapper
public interface IAgentArtifactDao {

    int insert(AgentArtifactPO artifact);

    AgentArtifactPO queryByArtifactId(String artifactId);
}

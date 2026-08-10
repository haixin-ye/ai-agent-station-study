package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentRagDocumentPO;

import java.util.List;

@Mapper
public interface IAgentRagDocumentDao {

    int insert(AgentRagDocumentPO document);

    int updateByDocumentId(AgentRagDocumentPO document);

    AgentRagDocumentPO queryByDocumentId(String documentId);

    AgentRagDocumentPO queryLatestByScopeAndSource(@Param("userId") String userId,
                                                   @Param("sessionId") String sessionId,
                                                   @Param("sourceName") String sourceName);

    List<AgentRagDocumentPO> queryByDocumentIds(List<String> documentIds);
}

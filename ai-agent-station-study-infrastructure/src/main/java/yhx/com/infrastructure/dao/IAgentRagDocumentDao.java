package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentRagDocumentPO;

import java.util.List;

@Mapper
public interface IAgentRagDocumentDao {

    int insert(AgentRagDocumentPO document);

    int updateByDocumentId(AgentRagDocumentPO document);

    AgentRagDocumentPO queryByDocumentId(String documentId);

    List<AgentRagDocumentPO> queryByDocumentIds(List<String> documentIds);
}

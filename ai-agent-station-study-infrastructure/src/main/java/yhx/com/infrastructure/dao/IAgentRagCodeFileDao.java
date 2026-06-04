package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentRagCodeFilePO;

import java.util.List;

@Mapper
public interface IAgentRagCodeFileDao {

    int insert(AgentRagCodeFilePO codeFile);

    List<AgentRagCodeFilePO> queryByDocumentId(String documentId);
}

package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import yhx.com.infrastructure.dao.po.AgentRagChunkPO;

import java.util.List;

@Mapper
public interface IAgentRagChunkDao {

    int insert(AgentRagChunkPO chunk);

    AgentRagChunkPO queryByChunkId(String chunkId);

    List<AgentRagChunkPO> queryByChunkIds(List<String> chunkIds);

    List<AgentRagChunkPO> queryByDocumentId(String documentId);
}

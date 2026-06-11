package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentVectorIndexPO;

@Mapper
public interface IAgentVectorIndexDao {

    int insertOrUpdate(AgentVectorIndexPO index);

    AgentVectorIndexPO queryBySource(@Param("collectionType") String collectionType,
                                     @Param("sourceType") String sourceType,
                                     @Param("sourceId") String sourceId);

    int markDisabled(@Param("collectionType") String collectionType,
                     @Param("sourceType") String sourceType,
                     @Param("sourceId") String sourceId);
}

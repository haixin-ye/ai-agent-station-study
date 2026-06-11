package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;

import java.util.List;

public interface IVectorMemoryRepository {

    String upsert(VectorIndexRecordVO record);

    List<VectorRecallHitVO> search(VectorRecallQueryVO query);

    default List<VectorRecallHitVO> lexicalSearch(VectorRecallQueryVO query) {
        return List.of();
    }

    void disable(VectorCollectionTypeEnumVO collectionType, String sourceId);
}

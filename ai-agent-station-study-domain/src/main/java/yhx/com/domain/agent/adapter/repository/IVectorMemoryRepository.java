package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.model.valobj.memory.VectorStoredRecordVO;

import java.util.List;
import java.util.Map;

public interface IVectorMemoryRepository {

    String upsert(VectorIndexRecordVO record);

    List<VectorRecallHitVO> search(VectorRecallQueryVO query);

    default List<VectorRecallHitVO> lexicalSearch(VectorRecallQueryVO query) {
        return List.of();
    }

    default List<VectorStoredRecordVO> listStoredRecords(List<VectorCollectionTypeEnumVO> collectionTypes,
                                                         Map<String, Object> metadataFilters,
                                                         int limit) {
        return List.of();
    }

    default int mergeMetadata(VectorCollectionTypeEnumVO collectionType,
                              String sourceId,
                              Map<String, Object> metadata) {
        return 0;
    }

    void disable(VectorCollectionTypeEnumVO collectionType, String sourceId);
}

package yhx.com.domain.agent.service.memory;

import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;

import java.util.List;

public class NoopVectorMemoryRepository implements IVectorMemoryRepository {

    @Override
    public String upsert(VectorIndexRecordVO record) {
        return record == null ? null : record.getVectorId();
    }

    @Override
    public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
        return List.of();
    }

    @Override
    public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        // No configured vector index.
    }
}

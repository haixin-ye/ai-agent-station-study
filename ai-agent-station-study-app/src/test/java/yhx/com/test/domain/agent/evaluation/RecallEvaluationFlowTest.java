package yhx.com.test.domain.agent.evaluation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.evaluation.DetailedRecallResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallExecutionOptionsVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RecallEvaluationFlowTest {

    @Test
    public void detailed_memory_recall_uses_run_parameters_and_exact_dataset_filter() {
        CapturingVectorRepository vectors = new CapturingVectorRepository();
        VectorContextRecallPreselector preselector = new VectorContextRecallPreselector(vectors, null, null, null, null);

        DetailedRecallResultVO result = preselector.recallDetailed(
                ContextPreparationCommand.builder()
                        .userId("eval-user:dataset-1")
                        .sessionId("eval-session:dataset-1")
                        .userInput("慢节奏旅行偏好")
                        .build(),
                RecallExecutionOptionsVO.builder()
                        .topK(17)
                        .minScore(0.61D)
                        .collectionTypes(List.of(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY))
                        .metadataFilters(Map.of("evalDatasetId", "dataset-1"))
                        .build());

        Assert.assertEquals(1, vectors.queries.size());
        VectorRecallQueryVO query = vectors.queries.get(0);
        Assert.assertEquals(Integer.valueOf(17), query.getTopK());
        Assert.assertEquals(Double.valueOf(0.61D), query.getMinScore());
        Assert.assertEquals(List.of(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY), query.getFilter().getCollectionTypes());
        Assert.assertEquals(Map.of("evalDatasetId", "dataset-1"), query.getFilter().getMetadataFilters());
        Assert.assertEquals(1, result.getVectorHits().size());
        Assert.assertNotNull(result.getCandidateBundle());
        Assert.assertTrue(result.getElapsedMs() >= 0L);
    }

    private static class CapturingVectorRepository implements IVectorMemoryRepository {
        private final List<VectorRecallQueryVO> queries = new ArrayList<>();

        @Override
        public String upsert(VectorIndexRecordVO record) {
            return null;
        }

        @Override
        public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
            queries.add(query);
            return List.of(VectorRecallHitVO.builder()
                    .collectionType(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY)
                    .sourceType(VectorSourceTypeEnumVO.LONG_TERM_MEMORY)
                    .sourceId("memory-1")
                    .score(0.91D)
                    .build());
        }

        @Override
        public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        }
    }
}

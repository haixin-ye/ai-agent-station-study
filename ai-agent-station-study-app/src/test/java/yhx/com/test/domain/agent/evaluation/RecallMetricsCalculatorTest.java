package yhx.com.test.domain.agent.evaluation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationHitEntity;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCaseMetricsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationMetricsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallExpectedItemVO;
import yhx.com.domain.agent.service.evaluation.RecallMetricsCalculator;

import java.util.List;

public class RecallMetricsCalculatorTest {

    @Test
    public void graded_labels_support_parent_matching_and_rank_metrics() {
        RecallMetricsCalculator calculator = new RecallMetricsCalculator();
        List<RecallExpectedItemVO> expected = List.of(
                RecallExpectedItemVO.builder().sourceId("doc-1").grade(3).matchMode("PARENT_DOCUMENT").build(),
                RecallExpectedItemVO.builder().sourceId("memory-1").grade(2).matchMode("EXACT_SOURCE").build());
        List<RecallEvaluationHitEntity> hits = List.of(
                hit(1, "chunk-1", "doc-1"),
                hit(2, "noise", null),
                hit(3, "memory-1", null));

        RecallCaseMetricsVO metrics = calculator.calculateCase(expected, hits, 3);

        Assert.assertTrue(metrics.getHit());
        Assert.assertEquals(2D / 3D, metrics.getPrecisionAtK(), 0.000001D);
        Assert.assertEquals(1D, metrics.getRecallAtK(), 0.000001D);
        Assert.assertEquals(1D, metrics.getReciprocalRank(), 0.000001D);
        Assert.assertEquals((1D + 2D / 3D) / 2D, metrics.getAveragePrecisionAtK(), 0.000001D);
        Assert.assertTrue(metrics.getNdcgAtK() > 0.9D && metrics.getNdcgAtK() < 1D);
    }

    @Test
    public void aggregate_metrics_include_no_hit_rate_and_latency_percentiles() {
        RecallMetricsCalculator calculator = new RecallMetricsCalculator();
        RecallEvaluationMetricsVO metrics = calculator.aggregate(List.of(
                RecallCaseMetricsVO.builder().hit(true).precisionAtK(1D).recallAtK(1D).reciprocalRank(1D)
                        .ndcgAtK(1D).averagePrecisionAtK(1D).retrievalLatencyMs(10L).build(),
                RecallCaseMetricsVO.builder().hit(false).precisionAtK(0D).recallAtK(0D).reciprocalRank(0D)
                        .ndcgAtK(0D).averagePrecisionAtK(0D).retrievalLatencyMs(20L).build(),
                RecallCaseMetricsVO.builder().hit(true).precisionAtK(0.5D).recallAtK(1D).reciprocalRank(0.5D)
                        .ndcgAtK(0.6D).averagePrecisionAtK(0.5D).retrievalLatencyMs(100L).build()));

        Assert.assertEquals(1D / 3D, metrics.getNoHitRate(), 0.000001D);
        Assert.assertEquals(Long.valueOf(20L), metrics.getRetrievalLatencyP50Ms());
        Assert.assertEquals(Long.valueOf(100L), metrics.getRetrievalLatencyP95Ms());
        Assert.assertEquals(Integer.valueOf(3), metrics.getEvaluatedCaseCount());
    }

    private RecallEvaluationHitEntity hit(int rank, String sourceId, String parentSourceId) {
        return RecallEvaluationHitEntity.builder()
                .rankNo(rank)
                .sourceId(sourceId)
                .parentSourceId(parentSourceId)
                .build();
    }
}

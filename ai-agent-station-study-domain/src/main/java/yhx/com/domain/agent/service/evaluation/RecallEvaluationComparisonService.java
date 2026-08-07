package yhx.com.domain.agent.service.evaluation;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IRecallEvaluationRepository;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseResultEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationHitEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationRunEntity;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCaseComparisonVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationComparisonVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationMetricsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationRunConfigVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RecallEvaluationComparisonService {

    private final IRecallEvaluationRepository repository;

    public RecallEvaluationComparisonService(IRecallEvaluationRepository repository) {
        this.repository = repository;
    }

    public RecallEvaluationComparisonVO compare(String leftRunId, String rightRunId) {
        RecallEvaluationRunEntity left = required(leftRunId);
        RecallEvaluationRunEntity right = required(rightRunId);
        if (!left.getDatasetId().equals(right.getDatasetId())) {
            throw new IllegalArgumentException("A/B runs must belong to the same dataset.");
        }
        RecallEvaluationMetricsVO leftMetrics = metrics(left);
        RecallEvaluationMetricsVO rightMetrics = metrics(right);
        return RecallEvaluationComparisonVO.builder()
                .leftRunId(leftRunId)
                .rightRunId(rightRunId)
                .leftConfig(JSON.parseObject(left.getConfigJson(), RecallEvaluationRunConfigVO.class))
                .rightConfig(JSON.parseObject(right.getConfigJson(), RecallEvaluationRunConfigVO.class))
                .leftMetrics(leftMetrics)
                .rightMetrics(rightMetrics)
                .metricDeltas(deltas(leftMetrics, rightMetrics))
                .cases(compareCases(leftRunId, rightRunId))
                .build();
    }

    private List<RecallCaseComparisonVO> compareCases(String leftRunId, String rightRunId) {
        Map<String, RecallEvaluationCaseResultEntity> left = results(leftRunId);
        Map<String, RecallEvaluationCaseResultEntity> right = results(rightRunId);
        Set<String> caseIds = new LinkedHashSet<>();
        caseIds.addAll(left.keySet());
        caseIds.addAll(right.keySet());
        List<RecallCaseComparisonVO> comparisons = new ArrayList<>();
        for (String caseId : caseIds) {
            RecallEvaluationCaseResultEntity leftResult = left.get(caseId);
            RecallEvaluationCaseResultEntity rightResult = right.get(caseId);
            Integer leftRank = firstRelevantRank(leftRunId, caseId);
            Integer rightRank = firstRelevantRank(rightRunId, caseId);
            comparisons.add(RecallCaseComparisonVO.builder()
                    .caseId(caseId)
                    .leftStatus(leftResult == null ? "MISSING" : leftResult.getStatus())
                    .rightStatus(rightResult == null ? "MISSING" : rightResult.getStatus())
                    .leftHit(leftResult != null && Boolean.TRUE.equals(leftResult.getHit()))
                    .rightHit(rightResult != null && Boolean.TRUE.equals(rightResult.getHit()))
                    .leftFirstRelevantRank(leftRank)
                    .rightFirstRelevantRank(rightRank)
                    .rankDelta(leftRank == null || rightRank == null ? null : leftRank - rightRank)
                    .outcome(outcome(leftResult, rightResult, leftRank, rightRank))
                    .build());
        }
        return comparisons;
    }

    private Map<String, RecallEvaluationCaseResultEntity> results(String runId) {
        return repository.listCaseResults(runId).stream().collect(Collectors.toMap(
                RecallEvaluationCaseResultEntity::getCaseId, Function.identity(), (left, ignored) -> left, LinkedHashMap::new));
    }

    private Integer firstRelevantRank(String runId, String caseId) {
        return repository.listHits(runId, caseId).stream()
                .filter(hit -> hit.getExpectedGrade() != null && hit.getExpectedGrade() > 0)
                .map(RecallEvaluationHitEntity::getRankNo)
                .filter(rank -> rank != null)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private String outcome(RecallEvaluationCaseResultEntity left,
                           RecallEvaluationCaseResultEntity right,
                           Integer leftRank,
                           Integer rightRank) {
        boolean leftHit = left != null && Boolean.TRUE.equals(left.getHit());
        boolean rightHit = right != null && Boolean.TRUE.equals(right.getHit());
        if (!leftHit && rightHit) return "IMPROVED_TO_HIT";
        if (leftHit && !rightHit) return "REGRESSED_TO_MISS";
        if (leftRank != null && rightRank != null && rightRank < leftRank) return "RANK_IMPROVED";
        if (leftRank != null && rightRank != null && rightRank > leftRank) return "RANK_REGRESSED";
        return "UNCHANGED";
    }

    private Map<String, Double> deltas(RecallEvaluationMetricsVO left, RecallEvaluationMetricsVO right) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("hitRateAtK", delta(left.getHitRateAtK(), right.getHitRateAtK()));
        values.put("precisionAtK", delta(left.getPrecisionAtK(), right.getPrecisionAtK()));
        values.put("recallAtK", delta(left.getRecallAtK(), right.getRecallAtK()));
        values.put("meanReciprocalRank", delta(left.getMeanReciprocalRank(), right.getMeanReciprocalRank()));
        values.put("ndcgAtK", delta(left.getNdcgAtK(), right.getNdcgAtK()));
        values.put("mapAtK", delta(left.getMapAtK(), right.getMapAtK()));
        values.put("noHitRate", delta(left.getNoHitRate(), right.getNoHitRate()));
        values.put("plannerPrecision", delta(left.getPlannerPrecision(), right.getPlannerPrecision()));
        values.put("plannerRecall", delta(left.getPlannerRecall(), right.getPlannerRecall()));
        return values;
    }

    private double delta(Double left, Double right) {
        return (right == null ? 0D : right) - (left == null ? 0D : left);
    }

    private RecallEvaluationRunEntity required(String runId) {
        return repository.findRun(runId).orElseThrow(() -> new IllegalArgumentException("Evaluation run does not exist: " + runId));
    }

    private RecallEvaluationMetricsVO metrics(RecallEvaluationRunEntity run) {
        RecallEvaluationMetricsVO value = JSON.parseObject(run.getMetricsJson(), RecallEvaluationMetricsVO.class);
        return value == null ? new RecallEvaluationMetricsVO() : value;
    }
}

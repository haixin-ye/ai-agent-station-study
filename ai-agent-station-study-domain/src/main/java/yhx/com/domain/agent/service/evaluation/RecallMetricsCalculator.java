package yhx.com.domain.agent.service.evaluation;

import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationHitEntity;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCaseMetricsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationMetricsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallExpectedItemVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecallMetricsCalculator {

    public RecallCaseMetricsVO calculateCase(List<RecallExpectedItemVO> expected,
                                              List<RecallEvaluationHitEntity> hits,
                                              int topK) {
        List<RecallExpectedItemVO> labels = expected == null ? List.of() : expected;
        List<RecallEvaluationHitEntity> sorted = hits == null ? List.of() : hits.stream()
                .sorted(Comparator.comparing(RecallEvaluationHitEntity::getRankNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<String, RecallEvaluationHitEntity> logicalResults = new LinkedHashMap<>();
        for (RecallEvaluationHitEntity hit : sorted) {
            logicalResults.putIfAbsent(logicalResultKey(hit), hit);
        }
        List<RecallEvaluationHitEntity> ranked = logicalResults.values().stream()
                .limit(Math.max(1, topK))
                .toList();
        Set<String> matched = new HashSet<>();
        int relevantHits = 0;
        double reciprocalRank = 0D;
        double averagePrecisionSum = 0D;
        double dcg = 0D;
        for (int index = 0; index < ranked.size(); index++) {
            RecallEvaluationHitEntity hit = ranked.get(index);
            RecallExpectedItemVO label = match(labels, hit);
            int rank = index + 1;
            if (label == null || !matched.add(labelKey(label))) {
                hit.setExpectedGrade(null);
                continue;
            }
            int grade = grade(label);
            hit.setExpectedGrade(grade);
            relevantHits++;
            if (reciprocalRank == 0D) {
                reciprocalRank = 1D / rank;
            }
            averagePrecisionSum += relevantHits / (double) rank;
            dcg += gain(grade) / log2(rank + 1D);
        }
        int expectedCount = labels.size();
        int denominator = Math.max(1, Math.min(Math.max(1, topK), ranked.size()));
        double precision = ranked.isEmpty() ? 0D : relevantHits / (double) denominator;
        double recall = expectedCount == 0 ? 0D : relevantHits / (double) expectedCount;
        double ap = expectedCount == 0 ? 0D : averagePrecisionSum / expectedCount;
        double idcg = idealDcg(labels, topK);
        return RecallCaseMetricsVO.builder()
                .hit(relevantHits > 0)
                .precisionAtK(precision)
                .recallAtK(recall)
                .reciprocalRank(reciprocalRank)
                .ndcgAtK(idcg == 0D ? 0D : dcg / idcg)
                .averagePrecisionAtK(ap)
                .build();
    }

    public RecallEvaluationMetricsVO aggregate(List<RecallCaseMetricsVO> values) {
        List<RecallCaseMetricsVO> cases = values == null ? List.of() : values;
        int count = cases.size();
        int noHit = (int) cases.stream().filter(value -> !Boolean.TRUE.equals(value.getHit())).count();
        List<Long> retrievalLatencies = cases.stream().map(RecallCaseMetricsVO::getRetrievalLatencyMs)
                .filter(value -> value != null && value >= 0L).sorted().toList();
        List<RecallCaseMetricsVO> plannerCases = cases.stream().filter(value -> Boolean.TRUE.equals(value.getPlannerInvoked())).toList();
        List<Long> plannerLatencies = plannerCases.stream().map(RecallCaseMetricsVO::getPlannerLatencyMs)
                .filter(value -> value != null && value >= 0L).sorted().toList();
        return RecallEvaluationMetricsVO.builder()
                .evaluatedCaseCount(count)
                .failedCaseCount((int) cases.stream().filter(value -> Boolean.TRUE.equals(value.getPlannerFailed())).count())
                .hitRateAtK(average(cases.stream().map(value -> Boolean.TRUE.equals(value.getHit()) ? 1D : 0D).toList()))
                .precisionAtK(average(cases.stream().map(RecallCaseMetricsVO::getPrecisionAtK).toList()))
                .recallAtK(average(cases.stream().map(RecallCaseMetricsVO::getRecallAtK).toList()))
                .meanReciprocalRank(average(cases.stream().map(RecallCaseMetricsVO::getReciprocalRank).toList()))
                .ndcgAtK(average(cases.stream().map(RecallCaseMetricsVO::getNdcgAtK).toList()))
                .mapAtK(average(cases.stream().map(RecallCaseMetricsVO::getAveragePrecisionAtK).toList()))
                .noHitRate(count == 0 ? 0D : noHit / (double) count)
                .retrievalLatencyAverageMs(averageLong(retrievalLatencies))
                .retrievalLatencyP50Ms(percentile(retrievalLatencies, 0.50D))
                .retrievalLatencyP95Ms(percentile(retrievalLatencies, 0.95D))
                .plannerInvocationCount(plannerCases.size())
                .plannerPrecision(average(plannerCases.stream().map(RecallCaseMetricsVO::getPlannerPrecision).toList()))
                .plannerRecall(average(plannerCases.stream().map(RecallCaseMetricsVO::getPlannerRecall).toList()))
                .plannerHitRateAtK(average(plannerCases.stream()
                        .map(value -> Boolean.TRUE.equals(value.getPlannerHit()) ? 1D : 0D).toList()))
                .plannerMeanReciprocalRank(average(plannerCases.stream()
                        .map(RecallCaseMetricsVO::getPlannerReciprocalRank).toList()))
                .plannerNdcgAtK(average(plannerCases.stream().map(RecallCaseMetricsVO::getPlannerNdcgAtK).toList()))
                .plannerAverageSelectedCount(average(plannerCases.stream()
                        .map(value -> value.getPlannerSelectedCount() == null ? null
                                : value.getPlannerSelectedCount().doubleValue()).toList()))
                .plannerRelevantRetentionRate(average(plannerCases.stream()
                        .map(RecallCaseMetricsVO::getPlannerRelevantRetentionRate).toList()))
                .plannerIrrelevantRemovalRate(average(plannerCases.stream()
                        .map(RecallCaseMetricsVO::getPlannerIrrelevantRemovalRate).toList()))
                .plannerRelevantDroppedCount(plannerCases.stream()
                        .map(RecallCaseMetricsVO::getPlannerRelevantDroppedCount)
                        .filter(value -> value != null && value > 0).mapToInt(Integer::intValue).sum())
                .clarificationRate(average(plannerCases.stream().map(value -> Boolean.TRUE.equals(value.getClarificationRequested()) ? 1D : 0D).toList()))
                .plannerFailureRate(average(plannerCases.stream().map(value -> Boolean.TRUE.equals(value.getPlannerFailed()) ? 1D : 0D).toList()))
                .plannerLatencyAverageMs(averageLong(plannerLatencies))
                .plannerLatencyP50Ms(percentile(plannerLatencies, 0.50D))
                .plannerLatencyP95Ms(percentile(plannerLatencies, 0.95D))
                .build();
    }

    private RecallExpectedItemVO match(List<RecallExpectedItemVO> labels, RecallEvaluationHitEntity hit) {
        if (hit == null) {
            return null;
        }
        for (RecallExpectedItemVO label : labels) {
            if (label == null || label.getSourceId() == null) {
                continue;
            }
            boolean parentMode = "PARENT_DOCUMENT".equalsIgnoreCase(label.getMatchMode());
            if (parentMode && label.getSourceId().equals(hit.getParentSourceId())) {
                return label;
            }
            if (!parentMode && label.getSourceId().equals(hit.getSourceId())) {
                return label;
            }
        }
        return null;
    }

    private double idealDcg(List<RecallExpectedItemVO> labels, int topK) {
        List<Integer> grades = new ArrayList<>(labels.stream().map(this::grade)
                .sorted(Comparator.reverseOrder()).toList());
        double result = 0D;
        for (int index = 0; index < Math.min(Math.max(1, topK), grades.size()); index++) {
            result += gain(grades.get(index)) / log2(index + 2D);
        }
        return result;
    }

    private int grade(RecallExpectedItemVO label) {
        return label == null || label.getGrade() == null ? 1 : Math.max(1, Math.min(3, label.getGrade()));
    }

    private String labelKey(RecallExpectedItemVO label) {
        return String.valueOf(label.getSourceId()) + "|" + String.valueOf(label.getMatchMode());
    }

    private String logicalResultKey(RecallEvaluationHitEntity hit) {
        if (hit == null) {
            return "null";
        }
        String parentSourceId = hit.getParentSourceId();
        if (parentSourceId != null && !parentSourceId.isBlank()) {
            return "PARENT|" + parentSourceId;
        }
        return "SOURCE|" + String.valueOf(hit.getSourceId());
    }

    private double gain(int grade) {
        return Math.pow(2D, grade) - 1D;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2D);
    }

    private double average(List<Double> values) {
        if (values == null) {
            return 0D;
        }
        List<Double> usable = values.stream().filter(value -> value != null && Double.isFinite(value)).toList();
        return usable.isEmpty() ? 0D : usable.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private Long averageLong(List<Long> values) {
        return values == null || values.isEmpty() ? 0L : Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0D));
    }

    private Long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(percentile * sortedValues.size()) - 1);
        return sortedValues.get(Math.min(index, sortedValues.size() - 1));
    }
}

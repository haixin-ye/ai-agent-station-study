package yhx.com.api.dto.agent.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class RecallEvaluationDTO {
    private RecallEvaluationDTO() {
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class DatasetRequest { private String name; private String description; }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CorpusBatchRequest { private List<CorpusItemRequest> items; }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CorpusItemRequest {
        private String externalId; private String type; private String title; private String summary;
        private String content; private BigDecimal score; private List<String> tags;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CaseBatchRequest { private List<CaseItemRequest> items; }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CaseItemRequest {
        private String externalId; private String query; private String sourceScope;
        private List<ExpectedItemRequest> expected; private List<String> tags;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ExpectedItemRequest {
        private String externalId; private String sourceId; private Integer grade; private String matchMode;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CaseUpdateRequest {
        private String query; private String sourceScope; private List<ExpectedItemRequest> expected;
        private List<String> tags; private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RunRequest {
        private String datasetId; private String name; private String sourceScope; private Integer topK;
        private Double minScore; private String retrievalMode; private List<String> collectionTypes;
        private Boolean plannerEnabled; private String plannerModelCode; private Double plannerTemperature;
        private Integer plannerMaxOutputTokens; private Integer caseLimit; private Long caseTimeoutMs;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DatasetView {
        private String datasetId; private String name; private String description; private String status;
        private String evalUserId; private String evalSessionId; private Integer corpusCount;
        private Integer readyCorpusCount; private Integer caseCount; private String failureCode;
        private String failureMessage; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CorpusItemView {
        private String corpusItemId; private String datasetId; private String externalId; private String itemType;
        private String title; private String summary; private String contentRef; private List<String> tags;
        private String sourceType; private String sourceId; private String parentSourceId; private List<String> sourceRefs;
        private String status; private String failureStage; private String failureCode; private String failureMessage;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CaseView {
        private String caseId; private String datasetId; private String externalId; private String query;
        private String sourceScope; private List<ExpectedItemRequest> expected; private List<String> tags;
        private String status; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RunView {
        private String evaluationRunId; private String datasetId; private String name; private String status;
        private RunRequest config; private MetricsView metrics; private Integer totalCaseCount;
        private Integer completedCaseCount; private Integer failedCaseCount; private Boolean cancelRequested;
        private String failureCode; private String failureMessage; private LocalDateTime createdAt;
        private LocalDateTime startedAt; private LocalDateTime completedAt; private LocalDateTime updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MetricsView {
        private Integer evaluatedCaseCount; private Integer failedCaseCount; private Double hitRateAtK;
        private Double precisionAtK; private Double recallAtK; private Double meanReciprocalRank;
        private Double ndcgAtK; private Double mapAtK; private Double noHitRate;
        private Long retrievalLatencyAverageMs; private Long retrievalLatencyP50Ms; private Long retrievalLatencyP95Ms;
        private Integer plannerInvocationCount; private Double plannerPrecision; private Double plannerRecall;
        private Double clarificationRate; private Double plannerFailureRate; private Long plannerLatencyAverageMs;
        private Long plannerLatencyP50Ms; private Long plannerLatencyP95Ms;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CaseResultView {
        private String caseResultId; private String evaluationRunId; private String caseId; private String status;
        private Long retrievalLatencyMs; private Long plannerLatencyMs; private Boolean hit;
        private BigDecimal precisionAtK; private BigDecimal recallAtK; private BigDecimal reciprocalRank;
        private BigDecimal ndcgAtK; private BigDecimal averagePrecisionAtK; private String plannerStatus;
        private String plannerReason; private List<String> plannerSelectedIds; private Object plannerOutput;
        private String failureStage; private String failureCode; private String failureMessage;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HitView {
        private String hitId; private String evaluationRunId; private String caseId; private Integer rankNo;
        private String retrievalChannel; private String collectionType; private String sourceType;
        private String sourceId; private String parentSourceId; private BigDecimal score; private Integer expectedGrade;
        private Boolean selectedByPlanner; private Object candidate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RunDetailView {
        private RunView run; private MetricsView metrics; private List<CaseResultView> results; private List<HitView> hits;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ImportView<T> {
        private Integer acceptedCount; private Integer failedCount; private List<T> items; private List<String> errors;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ComparisonView {
        private String leftRunId; private String rightRunId; private RunRequest leftConfig; private RunRequest rightConfig;
        private MetricsView leftMetrics; private MetricsView rightMetrics; private Map<String, Double> metricDeltas;
        private List<Map<String, Object>> cases;
    }
}

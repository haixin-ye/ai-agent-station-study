package yhx.com.trigger.http.support;

import com.alibaba.fastjson.JSON;
import yhx.com.api.dto.agent.evaluation.RecallEvaluationDTO;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseResultEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCorpusItemEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationDatasetEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationHitEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationRunEntity;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCaseComparisonVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCaseImportItemVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationComparisonVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationMetricsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationRunConfigVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallExpectedItemVO;

import java.util.List;

public final class RecallEvaluationApiMapper {
    private RecallEvaluationApiMapper() {
    }

    public static RecallEvaluationDTO.DatasetView dataset(RecallEvaluationDatasetEntity value) {
        return RecallEvaluationDTO.DatasetView.builder()
                .datasetId(value.getDatasetId()).name(value.getName()).description(value.getDescription())
                .status(value.getStatus()).evalUserId(value.getEvalUserId()).evalSessionId(value.getEvalSessionId())
                .corpusCount(value.getCorpusCount()).readyCorpusCount(value.getReadyCorpusCount())
                .caseCount(value.getCaseCount()).failureCode(value.getFailureCode())
                .failureMessage(value.getFailureMessage()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt())
                .build();
    }

    public static RecallEvaluationDTO.CorpusItemView corpus(RecallEvaluationCorpusItemEntity value) {
        return RecallEvaluationDTO.CorpusItemView.builder()
                .corpusItemId(value.getCorpusItemId()).datasetId(value.getDatasetId()).externalId(value.getExternalId())
                .itemType(value.getItemType()).title(value.getTitle()).summary(value.getSummary())
                .contentRef(value.getContentRef()).tags(strings(value.getTagsJson())).sourceType(value.getSourceType())
                .sourceId(value.getSourceId()).parentSourceId(value.getParentSourceId())
                .sourceRefs(strings(value.getSourceRefsJson())).status(value.getStatus())
                .failureStage(value.getFailureStage()).failureCode(value.getFailureCode())
                .failureMessage(value.getFailureMessage()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt())
                .build();
    }

    public static RecallEvaluationDTO.CaseView testCase(RecallEvaluationCaseEntity value) {
        return RecallEvaluationDTO.CaseView.builder()
                .caseId(value.getCaseId()).datasetId(value.getDatasetId()).externalId(value.getExternalId())
                .query(value.getQueryText()).sourceScope(value.getSourceScope())
                .expected(expectedRequests(value.getExpectedJson())).tags(strings(value.getTagsJson()))
                .status(value.getStatus()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    public static RecallEvaluationDTO.RunView run(RecallEvaluationRunEntity value) {
        RecallEvaluationRunConfigVO config = object(value.getConfigJson(), RecallEvaluationRunConfigVO.class);
        RecallEvaluationMetricsVO metrics = object(value.getMetricsJson(), RecallEvaluationMetricsVO.class);
        return RecallEvaluationDTO.RunView.builder()
                .evaluationRunId(value.getEvaluationRunId()).datasetId(value.getDatasetId()).name(value.getName())
                .status(value.getStatus()).config(runRequest(config)).metrics(metrics(metrics))
                .totalCaseCount(value.getTotalCaseCount()).completedCaseCount(value.getCompletedCaseCount())
                .failedCaseCount(value.getFailedCaseCount()).cancelRequested(value.getCancelRequested())
                .failureCode(value.getFailureCode()).failureMessage(value.getFailureMessage())
                .createdAt(value.getCreatedAt()).startedAt(value.getStartedAt()).completedAt(value.getCompletedAt())
                .updatedAt(value.getUpdatedAt()).build();
    }

    public static RecallEvaluationDTO.MetricsView metrics(RecallEvaluationMetricsVO value) {
        if (value == null) return null;
        return RecallEvaluationDTO.MetricsView.builder()
                .evaluatedCaseCount(value.getEvaluatedCaseCount()).failedCaseCount(value.getFailedCaseCount())
                .hitRateAtK(value.getHitRateAtK()).precisionAtK(value.getPrecisionAtK()).recallAtK(value.getRecallAtK())
                .meanReciprocalRank(value.getMeanReciprocalRank()).ndcgAtK(value.getNdcgAtK()).mapAtK(value.getMapAtK())
                .noHitRate(value.getNoHitRate()).retrievalLatencyAverageMs(value.getRetrievalLatencyAverageMs())
                .retrievalLatencyP50Ms(value.getRetrievalLatencyP50Ms()).retrievalLatencyP95Ms(value.getRetrievalLatencyP95Ms())
                .plannerInvocationCount(value.getPlannerInvocationCount()).plannerPrecision(value.getPlannerPrecision())
                .plannerRecall(value.getPlannerRecall()).clarificationRate(value.getClarificationRate())
                .plannerFailureRate(value.getPlannerFailureRate()).plannerLatencyAverageMs(value.getPlannerLatencyAverageMs())
                .plannerLatencyP50Ms(value.getPlannerLatencyP50Ms()).plannerLatencyP95Ms(value.getPlannerLatencyP95Ms()).build();
    }

    public static RecallEvaluationDTO.CaseResultView result(RecallEvaluationCaseResultEntity value) {
        return RecallEvaluationDTO.CaseResultView.builder()
                .caseResultId(value.getCaseResultId()).evaluationRunId(value.getEvaluationRunId()).caseId(value.getCaseId())
                .status(value.getStatus()).retrievalLatencyMs(value.getRetrievalLatencyMs()).plannerLatencyMs(value.getPlannerLatencyMs())
                .hit(value.getHit()).precisionAtK(value.getPrecisionAtK()).recallAtK(value.getRecallAtK())
                .reciprocalRank(value.getReciprocalRank()).ndcgAtK(value.getNdcgAtK())
                .averagePrecisionAtK(value.getAveragePrecisionAtK()).plannerStatus(value.getPlannerStatus())
                .plannerReason(value.getPlannerReason()).plannerSelectedIds(strings(value.getPlannerSelectedIdsJson()))
                .plannerOutput(any(value.getPlannerOutputJson())).failureStage(value.getFailureStage())
                .failureCode(value.getFailureCode()).failureMessage(value.getFailureMessage()).build();
    }

    public static RecallEvaluationDTO.HitView hit(RecallEvaluationHitEntity value) {
        return RecallEvaluationDTO.HitView.builder()
                .hitId(value.getHitId()).evaluationRunId(value.getEvaluationRunId()).caseId(value.getCaseId())
                .rankNo(value.getRankNo()).retrievalChannel(value.getRetrievalChannel())
                .collectionType(value.getCollectionType()).sourceType(value.getSourceType()).sourceId(value.getSourceId())
                .parentSourceId(value.getParentSourceId()).score(value.getScore()).expectedGrade(value.getExpectedGrade())
                .selectedByPlanner(value.getSelectedByPlanner()).candidate(any(value.getCandidateJson())).build();
    }

    public static RecallEvaluationDTO.ComparisonView comparison(RecallEvaluationComparisonVO value) {
        return RecallEvaluationDTO.ComparisonView.builder()
                .leftRunId(value.getLeftRunId()).rightRunId(value.getRightRunId())
                .leftConfig(runRequest(value.getLeftConfig())).rightConfig(runRequest(value.getRightConfig()))
                .leftMetrics(metrics(value.getLeftMetrics())).rightMetrics(metrics(value.getRightMetrics()))
                .metricDeltas(value.getMetricDeltas()).cases(value.getCases().stream().map(RecallEvaluationApiMapper::caseComparison).toList())
                .build();
    }

    public static RecallCaseImportItemVO caseInput(RecallEvaluationDTO.CaseItemRequest value) {
        return RecallCaseImportItemVO.builder().externalId(value.getExternalId()).query(value.getQuery())
                .sourceScope(value.getSourceScope()).expected(expected(value.getExpected())).tags(value.getTags()).build();
    }

    public static List<RecallExpectedItemVO> expected(List<RecallEvaluationDTO.ExpectedItemRequest> values) {
        return values == null ? List.of() : values.stream().map(value -> RecallExpectedItemVO.builder()
                .externalId(value.getExternalId()).sourceId(value.getSourceId()).grade(value.getGrade())
                .matchMode(value.getMatchMode()).build()).toList();
    }

    public static RecallEvaluationRunConfigVO runConfig(RecallEvaluationDTO.RunRequest value) {
        return RecallEvaluationRunConfigVO.builder().datasetId(value.getDatasetId()).name(value.getName())
                .sourceScope(value.getSourceScope()).topK(value.getTopK()).minScore(value.getMinScore())
                .retrievalMode(value.getRetrievalMode()).collectionTypes(value.getCollectionTypes())
                .plannerEnabled(value.getPlannerEnabled()).plannerModelCode(value.getPlannerModelCode())
                .plannerTemperature(value.getPlannerTemperature()).plannerMaxOutputTokens(value.getPlannerMaxOutputTokens())
                .caseLimit(value.getCaseLimit()).caseTimeoutMs(value.getCaseTimeoutMs()).build();
    }

    private static RecallEvaluationDTO.RunRequest runRequest(RecallEvaluationRunConfigVO value) {
        if (value == null) return null;
        return new RecallEvaluationDTO.RunRequest(value.getDatasetId(), value.getName(), value.getSourceScope(),
                value.getTopK(), value.getMinScore(), value.getRetrievalMode(), value.getCollectionTypes(),
                value.getPlannerEnabled(), value.getPlannerModelCode(), value.getPlannerTemperature(),
                value.getPlannerMaxOutputTokens(), value.getCaseLimit(), value.getCaseTimeoutMs());
    }

    private static List<RecallEvaluationDTO.ExpectedItemRequest> expectedRequests(String json) {
        List<RecallExpectedItemVO> values = array(json, RecallExpectedItemVO.class);
        return values.stream().map(value -> new RecallEvaluationDTO.ExpectedItemRequest(value.getExternalId(),
                value.getSourceId(), value.getGrade(), value.getMatchMode())).toList();
    }

    private static java.util.Map<String, Object> caseComparison(RecallCaseComparisonVO value) {
        return JSON.parseObject(JSON.toJSONString(value));
    }

    private static List<String> strings(String json) {
        return array(json, String.class);
    }

    private static Object any(String json) {
        if (json == null || json.isBlank()) return null;
        try { return JSON.parse(json); } catch (Exception ignored) { return json; }
    }

    private static <T> T object(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try { return JSON.parseObject(json, type); } catch (Exception ignored) { return null; }
    }

    private static <T> List<T> array(String json, Class<T> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<T> values = JSON.parseArray(json, type);
            return values == null ? List.of() : values;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}

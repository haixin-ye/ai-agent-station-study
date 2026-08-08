package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseResultEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCorpusItemEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationDatasetEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationHitEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationRunEntity;

import java.util.List;
import java.util.Optional;

public interface IRecallEvaluationRepository {

    void saveDataset(RecallEvaluationDatasetEntity dataset);

    Optional<RecallEvaluationDatasetEntity> findDataset(String datasetId);

    List<RecallEvaluationDatasetEntity> listDatasets();

    void updateDataset(RecallEvaluationDatasetEntity dataset);

    void saveCorpusItem(RecallEvaluationCorpusItemEntity item);

    Optional<RecallEvaluationCorpusItemEntity> findCorpusItem(String corpusItemId);

    Optional<RecallEvaluationCorpusItemEntity> findCorpusItemByExternalId(String datasetId, String externalId);

    List<RecallEvaluationCorpusItemEntity> listCorpusItems(String datasetId, String status, int limit, int offset);

    void updateCorpusItem(RecallEvaluationCorpusItemEntity item);

    void saveCase(RecallEvaluationCaseEntity testCase);

    Optional<RecallEvaluationCaseEntity> findCase(String caseId);

    List<RecallEvaluationCaseEntity> listCases(String datasetId, String status, int limit, int offset);

    void updateCase(RecallEvaluationCaseEntity testCase);

    void saveRun(RecallEvaluationRunEntity run);

    Optional<RecallEvaluationRunEntity> findRun(String evaluationRunId);

    List<RecallEvaluationRunEntity> listRuns(String datasetId, int limit);

    void updateRun(RecallEvaluationRunEntity run);

    void saveCaseResult(RecallEvaluationCaseResultEntity result);

    List<RecallEvaluationCaseResultEntity> listCaseResults(String evaluationRunId);

    void saveHits(List<RecallEvaluationHitEntity> hits);

    List<RecallEvaluationHitEntity> listHits(String evaluationRunId, String caseId);
}

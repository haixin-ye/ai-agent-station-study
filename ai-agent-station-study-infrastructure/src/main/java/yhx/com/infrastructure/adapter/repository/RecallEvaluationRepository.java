package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IRecallEvaluationRepository;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseResultEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCorpusItemEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationDatasetEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationHitEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationRunEntity;
import yhx.com.infrastructure.dao.IRecallEvaluationDao;
import yhx.com.infrastructure.dao.po.RecallEvaluationCasePO;
import yhx.com.infrastructure.dao.po.RecallEvaluationCaseResultPO;
import yhx.com.infrastructure.dao.po.RecallEvaluationCorpusItemPO;
import yhx.com.infrastructure.dao.po.RecallEvaluationDatasetPO;
import yhx.com.infrastructure.dao.po.RecallEvaluationHitPO;
import yhx.com.infrastructure.dao.po.RecallEvaluationRunPO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RecallEvaluationRepository implements IRecallEvaluationRepository {

    @Resource
    private IRecallEvaluationDao dao;

    @Override
    public void saveDataset(RecallEvaluationDatasetEntity value) {
        LocalDateTime now = LocalDateTime.now();
        value.setDatasetId(id(value.getDatasetId(), "eval-dataset-"));
        value.setStatus(text(value.getStatus(), "ACTIVE"));
        value.setEvalUserId(text(value.getEvalUserId(), "eval-user:" + value.getDatasetId()));
        value.setEvalSessionId(text(value.getEvalSessionId(), "eval-session:" + value.getDatasetId()));
        value.setCorpusCount(number(value.getCorpusCount()));
        value.setReadyCorpusCount(number(value.getReadyCorpusCount()));
        value.setCaseCount(number(value.getCaseCount()));
        value.setCreatedAt(time(value.getCreatedAt(), now));
        value.setUpdatedAt(time(value.getUpdatedAt(), now));
        dao.insertDataset(copy(value, RecallEvaluationDatasetPO.class));
    }

    @Override
    public Optional<RecallEvaluationDatasetEntity> findDataset(String datasetId) {
        return Optional.ofNullable(dao.findDataset(datasetId)).map(value -> copy(value, RecallEvaluationDatasetEntity.class));
    }

    @Override
    public List<RecallEvaluationDatasetEntity> listDatasets() {
        return dao.listDatasets().stream().map(value -> copy(value, RecallEvaluationDatasetEntity.class)).toList();
    }

    @Override
    public void updateDataset(RecallEvaluationDatasetEntity value) {
        value.setUpdatedAt(LocalDateTime.now());
        dao.updateDataset(copy(value, RecallEvaluationDatasetPO.class));
    }

    @Override
    public void saveCorpusItem(RecallEvaluationCorpusItemEntity value) {
        LocalDateTime now = LocalDateTime.now();
        value.setCorpusItemId(id(value.getCorpusItemId(), "eval-corpus-"));
        value.setStatus(text(value.getStatus(), "PENDING"));
        value.setCreatedAt(time(value.getCreatedAt(), now));
        value.setUpdatedAt(time(value.getUpdatedAt(), now));
        dao.insertCorpusItem(copy(value, RecallEvaluationCorpusItemPO.class));
    }

    @Override
    public Optional<RecallEvaluationCorpusItemEntity> findCorpusItem(String corpusItemId) {
        return Optional.ofNullable(dao.findCorpusItem(corpusItemId)).map(value -> copy(value, RecallEvaluationCorpusItemEntity.class));
    }

    @Override
    public Optional<RecallEvaluationCorpusItemEntity> findCorpusItemByExternalId(String datasetId, String externalId) {
        return Optional.ofNullable(dao.findCorpusItemByExternalId(datasetId, externalId))
                .map(value -> copy(value, RecallEvaluationCorpusItemEntity.class));
    }

    @Override
    public List<RecallEvaluationCorpusItemEntity> listCorpusItems(String datasetId, String status, int limit, int offset) {
        return dao.listCorpusItems(datasetId, status, bounded(limit), Math.max(offset, 0)).stream()
                .map(value -> copy(value, RecallEvaluationCorpusItemEntity.class)).toList();
    }

    @Override
    public void updateCorpusItem(RecallEvaluationCorpusItemEntity value) {
        value.setUpdatedAt(LocalDateTime.now());
        dao.updateCorpusItem(copy(value, RecallEvaluationCorpusItemPO.class));
    }

    @Override
    public void saveCase(RecallEvaluationCaseEntity value) {
        LocalDateTime now = LocalDateTime.now();
        value.setCaseId(id(value.getCaseId(), "eval-case-"));
        value.setStatus(text(value.getStatus(), "ACTIVE"));
        value.setCreatedAt(time(value.getCreatedAt(), now));
        value.setUpdatedAt(time(value.getUpdatedAt(), now));
        dao.insertCase(copy(value, RecallEvaluationCasePO.class));
    }

    @Override
    public Optional<RecallEvaluationCaseEntity> findCase(String caseId) {
        return Optional.ofNullable(dao.findCase(caseId)).map(value -> copy(value, RecallEvaluationCaseEntity.class));
    }

    @Override
    public List<RecallEvaluationCaseEntity> listCases(String datasetId, String status, int limit, int offset) {
        return dao.listCases(datasetId, status, bounded(limit), Math.max(offset, 0)).stream()
                .map(value -> copy(value, RecallEvaluationCaseEntity.class)).toList();
    }

    @Override
    public void updateCase(RecallEvaluationCaseEntity value) {
        value.setUpdatedAt(LocalDateTime.now());
        dao.updateCase(copy(value, RecallEvaluationCasePO.class));
    }

    @Override
    public void saveRun(RecallEvaluationRunEntity value) {
        LocalDateTime now = LocalDateTime.now();
        value.setEvaluationRunId(id(value.getEvaluationRunId(), "eval-run-"));
        value.setStatus(text(value.getStatus(), "PENDING"));
        value.setTotalCaseCount(number(value.getTotalCaseCount()));
        value.setCompletedCaseCount(number(value.getCompletedCaseCount()));
        value.setFailedCaseCount(number(value.getFailedCaseCount()));
        value.setCancelRequested(Boolean.TRUE.equals(value.getCancelRequested()));
        value.setCreatedAt(time(value.getCreatedAt(), now));
        value.setUpdatedAt(time(value.getUpdatedAt(), now));
        dao.insertRun(copy(value, RecallEvaluationRunPO.class));
    }

    @Override
    public Optional<RecallEvaluationRunEntity> findRun(String evaluationRunId) {
        return Optional.ofNullable(dao.findRun(evaluationRunId)).map(value -> copy(value, RecallEvaluationRunEntity.class));
    }

    @Override
    public List<RecallEvaluationRunEntity> listRuns(String datasetId, int limit) {
        return dao.listRuns(datasetId, bounded(limit)).stream().map(value -> copy(value, RecallEvaluationRunEntity.class)).toList();
    }

    @Override
    public void updateRun(RecallEvaluationRunEntity value) {
        value.setUpdatedAt(LocalDateTime.now());
        dao.updateRun(copy(value, RecallEvaluationRunPO.class));
    }

    @Override
    public void saveCaseResult(RecallEvaluationCaseResultEntity value) {
        LocalDateTime now = LocalDateTime.now();
        value.setCaseResultId(id(value.getCaseResultId(), "eval-result-"));
        value.setCreatedAt(time(value.getCreatedAt(), now));
        value.setUpdatedAt(time(value.getUpdatedAt(), now));
        dao.insertCaseResult(copy(value, RecallEvaluationCaseResultPO.class));
    }

    @Override
    public List<RecallEvaluationCaseResultEntity> listCaseResults(String evaluationRunId) {
        return dao.listCaseResults(evaluationRunId).stream().map(value -> copy(value, RecallEvaluationCaseResultEntity.class)).toList();
    }

    @Override
    public void saveHits(List<RecallEvaluationHitEntity> hits) {
        if (hits == null) {
            return;
        }
        for (RecallEvaluationHitEntity hit : hits) {
            hit.setHitId(id(hit.getHitId(), "eval-hit-"));
            hit.setCreatedAt(time(hit.getCreatedAt(), LocalDateTime.now()));
            dao.insertHit(copy(hit, RecallEvaluationHitPO.class));
        }
    }

    @Override
    public List<RecallEvaluationHitEntity> listHits(String evaluationRunId, String caseId) {
        return dao.listHits(evaluationRunId, caseId).stream().map(value -> copy(value, RecallEvaluationHitEntity.class)).toList();
    }

    private <T> T copy(Object source, Class<T> type) {
        try {
            T target = type.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to map recall evaluation persistence object.", e);
        }
    }

    private String id(String value, String prefix) {
        return value == null || value.isBlank() ? prefix + UUID.randomUUID() : value;
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Integer number(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime time(LocalDateTime value, LocalDateTime fallback) {
        return value == null ? fallback : value;
    }

    private int bounded(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 1000));
    }
}

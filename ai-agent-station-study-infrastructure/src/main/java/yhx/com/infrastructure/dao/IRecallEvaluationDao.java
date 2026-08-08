package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.RecallEvaluationCasePO;
import yhx.com.infrastructure.dao.po.RecallEvaluationCaseResultPO;
import yhx.com.infrastructure.dao.po.RecallEvaluationCorpusItemPO;
import yhx.com.infrastructure.dao.po.RecallEvaluationDatasetPO;
import yhx.com.infrastructure.dao.po.RecallEvaluationHitPO;
import yhx.com.infrastructure.dao.po.RecallEvaluationRunPO;

import java.util.List;

@Mapper
public interface IRecallEvaluationDao {
    int insertDataset(RecallEvaluationDatasetPO value);
    RecallEvaluationDatasetPO findDataset(@Param("datasetId") String datasetId);
    List<RecallEvaluationDatasetPO> listDatasets();
    int updateDataset(RecallEvaluationDatasetPO value);

    int insertCorpusItem(RecallEvaluationCorpusItemPO value);
    RecallEvaluationCorpusItemPO findCorpusItem(@Param("corpusItemId") String corpusItemId);
    RecallEvaluationCorpusItemPO findCorpusItemByExternalId(@Param("datasetId") String datasetId, @Param("externalId") String externalId);
    List<RecallEvaluationCorpusItemPO> listCorpusItems(@Param("datasetId") String datasetId, @Param("status") String status,
                                                       @Param("limit") int limit, @Param("offset") int offset);
    int updateCorpusItem(RecallEvaluationCorpusItemPO value);

    int insertCase(RecallEvaluationCasePO value);
    RecallEvaluationCasePO findCase(@Param("caseId") String caseId);
    List<RecallEvaluationCasePO> listCases(@Param("datasetId") String datasetId, @Param("status") String status,
                                           @Param("limit") int limit, @Param("offset") int offset);
    int updateCase(RecallEvaluationCasePO value);

    int insertRun(RecallEvaluationRunPO value);
    RecallEvaluationRunPO findRun(@Param("evaluationRunId") String evaluationRunId);
    List<RecallEvaluationRunPO> listRuns(@Param("datasetId") String datasetId, @Param("limit") int limit);
    int updateRun(RecallEvaluationRunPO value);

    int insertCaseResult(RecallEvaluationCaseResultPO value);
    List<RecallEvaluationCaseResultPO> listCaseResults(@Param("evaluationRunId") String evaluationRunId);
    int insertHit(RecallEvaluationHitPO value);
    List<RecallEvaluationHitPO> listHits(@Param("evaluationRunId") String evaluationRunId, @Param("caseId") String caseId);
}

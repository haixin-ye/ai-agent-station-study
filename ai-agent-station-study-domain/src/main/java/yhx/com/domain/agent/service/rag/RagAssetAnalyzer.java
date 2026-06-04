package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.model.valobj.rag.RagAssetAnalysisResultVO;

public interface RagAssetAnalyzer {

    RagAssetAnalysisResultVO analyzeDocument(String sourceName, String sourceType, String text);

    RagAssetAnalysisResultVO analyzeChunk(String sourceName, String sourceType, String text);
}

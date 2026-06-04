package yhx.com.domain.agent.service.node.ragasset;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.rag.RagAssetAnalysisInputVO;
import yhx.com.domain.agent.model.valobj.rag.RagAssetAnalysisResultVO;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.rag.RagAssetAnalyzer;

public class RagAssetAnalyzerNodeService implements RagAssetAnalyzer {

    private static final String CONTRACT_VERSION = "rag-asset-analysis-output-v1";
    private static final String PROMPT_VERSION = "v1";

    private final NodeInvocationPipeline nodeInvocationPipeline;
    private final NodeInvocationProfileVO defaultProfile;

    public RagAssetAnalyzerNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                       NodeInvocationProfileVO defaultProfile) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
        this.defaultProfile = defaultProfile;
    }

    @Override
    public RagAssetAnalysisResultVO analyzeDocument(String sourceName, String sourceType, String text) {
        return invoke(sourceName, sourceType, "DOCUMENT", text);
    }

    @Override
    public RagAssetAnalysisResultVO analyzeChunk(String sourceName, String sourceType, String text) {
        return invoke(sourceName, sourceType, "CHUNK", text);
    }

    private RagAssetAnalysisResultVO invoke(String sourceName, String sourceType, String contentKind, String text) {
        if (nodeInvocationPipeline == null) {
            return null;
        }
        NodeInvocationProfileVO profile = defaultProfile == null ? NodeInvocationProfileVO.builder().build() : defaultProfile;
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .agentId("auto-agent")
                .componentCode(AgentComponentCodeEnumVO.RAG_ASSET_ANALYZER.name())
                .contractVersion(firstNonBlank(profile.getContractVersion(), CONTRACT_VERSION))
                .promptVersion(firstNonBlank(profile.getPromptVersion(), PROMPT_VERSION))
                .modelCode(profile.getModelCode())
                .temperature(profile.getTemperature())
                .maxOutputTokens(profile.getMaxOutputTokens())
                .maxRepairAttempts(profile.getMaxRepairAttempts() == null ? 1 : profile.getMaxRepairAttempts())
                .inputView(RagAssetAnalysisInputVO.builder()
                        .sourceName(sourceName)
                        .sourceType(sourceType)
                        .contentKind(contentKind)
                        .content(text)
                        .build())
                .build());
        if (result.getTypedOutput() instanceof RagAssetAnalysisResultVO output) {
            return output;
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}

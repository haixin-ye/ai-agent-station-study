package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.ArtifactChunkVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedArtifactContentVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;

import java.util.ArrayList;
import java.util.List;

public class ContextBudgetManager {

    private final ContextTokenEstimator tokenEstimator;

    public ContextBudgetManager(ContextTokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public TokenBudgetVO evaluate(MainAgentStateViewVO stateView, TokenBudgetVO budget) {
        int selected = tokenEstimator.estimateObjectTokens(stateView);
        int max = budget.getMaxStateViewTokens() == null ? 6000 : budget.getMaxStateViewTokens();
        budget.setSelectedContextTokens(selected);
        budget.setRemainingTokens(max - selected);
        budget.setOverBudget(selected > max);
        return budget;
    }

    public MainAgentStateViewVO shrinkToFit(MainAgentStateViewVO stateView, TokenBudgetVO budget) {
        evaluate(stateView, budget);
        if (!Boolean.TRUE.equals(budget.getOverBudget())) {
            return stateView;
        }
        if (stateView.getArtifactContent() != null) {
            List<MaterializedArtifactContentVO> shrunk = new ArrayList<>();
            for (MaterializedArtifactContentVO artifact : stateView.getArtifactContent()) {
                if (artifact.getContent() != null) {
                    artifact.setContent(null);
                    artifact.setContextLevel(ContextLevelEnumVO.CHUNKED_CONTEXT);
                    if (artifact.getChunks() == null || artifact.getChunks().isEmpty()) {
                        artifact.setChunks(List.of(ArtifactChunkVO.builder()
                                .index(0)
                                .content(artifact.getSummary())
                                .tokenCount(tokenEstimator.estimateTextTokens(artifact.getSummary()))
                                .build()));
                    }
                    artifact.setTruncated(true);
                }
                shrunk.add(artifact);
            }
            stateView.setArtifactContent(shrunk);
        }
        evaluate(stateView, budget);
        return stateView;
    }
}

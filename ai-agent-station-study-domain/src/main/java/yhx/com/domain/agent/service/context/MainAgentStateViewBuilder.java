package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.ConversationViewVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewBuildCommand;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;

import java.util.List;

public class MainAgentStateViewBuilder {

    public MainAgentStateViewVO build(MainAgentStateViewBuildCommand command) {
        return MainAgentStateViewVO.builder()
                .runMeta(command.getCandidates().getRunMeta())
                .userInput(command.getCandidates().getUserInput())
                .conversation(ConversationViewVO.builder()
                        .recentMessages(command.getCandidates().getRecentMessages())
                        .summaries(command.getCandidates().getSessionSummaries())
                        .build())
                .memoryPack(command.getMemoryPack() == null ? List.of() : command.getMemoryPack())
                .resolvedArtifacts(command.getCandidates().getArtifactCandidates())
                .artifactContent(command.getArtifactContent() == null ? List.of() : command.getArtifactContent())
                .evidencePack(command.getEvidencePack() == null ? List.of() : command.getEvidencePack())
                .userClarifications(command.getUserClarifications() == null
                        ? defaultList(command.getCandidates().getUserClarifications())
                        : command.getUserClarifications())
                .availableCapabilities(command.getCandidates().getAvailableCapabilities())
                .pendingAction(command.getCandidates().getPendingAction())
                .outputContractVersion("main-agent-action-v1")
                .tokenBudget(command.getTokenBudget())
                .failure(command.getFailure())
                .build();
    }

    private <T> List<T> defaultList(List<T> value) {
        return value == null ? List.of() : value;
    }
}

package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.ConversationViewVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewBuildCommand;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MessageCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainAgentStateViewBuilder {

    public MainAgentStateViewVO build(MainAgentStateViewBuildCommand command) {
        return MainAgentStateViewVO.builder()
                .runMeta(command.getCandidates().getRunMeta())
                .userInput(command.getCandidates().getUserInput())
                .conversation(ConversationViewVO.builder()
                        .recentMessages(mergedMessages(command))
                        .sessionTaskSummary(command.getCandidates().getSessionTaskSummary())
                        .summaries(command.getConversationSummaries() == null ? List.of() : command.getConversationSummaries())
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

    private List<MessageCandidateVO> mergedMessages(MainAgentStateViewBuildCommand command) {
        Map<String, MessageCandidateVO> merged = new LinkedHashMap<>();
        addMessages(merged, command.getCandidates().getFixedRecentMessages());
        addMessages(merged, command.getCandidates().getRecentMessages());
        addMessages(merged, command.getMaterializedMessages());
        return List.copyOf(merged.values());
    }

    private void addMessages(Map<String, MessageCandidateVO> target, List<MessageCandidateVO> messages) {
        if (messages == null) {
            return;
        }
        for (MessageCandidateVO message : messages) {
            if (message == null || message.getMessageId() == null) {
                continue;
            }
            target.putIfAbsent(message.getMessageId(), message);
        }
    }
}

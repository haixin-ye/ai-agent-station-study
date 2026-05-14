package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.adapter.repository.INodePromptRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentNodePromptEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;

import java.util.List;
import java.util.Optional;

public class RepositoryPromptContentProvider implements PromptContentProvider {

    private static final String GLOBAL_AGENT_ID = "GLOBAL";

    private final INodePromptRepository nodePromptRepository;
    private final IPayloadRepository payloadRepository;

    public RepositoryPromptContentProvider(INodePromptRepository nodePromptRepository, IPayloadRepository payloadRepository) {
        this.nodePromptRepository = nodePromptRepository;
        this.payloadRepository = payloadRepository;
    }

    @Override
    public List<String> loadRolePrompts(String agentId, String componentCode, String promptVersion) {
        List<AgentNodePromptEntity> prompts = nodePromptRepository.listEnabledPrompts(agentId, componentCode).stream()
                .filter(prompt -> promptVersion == null || promptVersion.equals(prompt.getPromptVersion()))
                .toList();
        if (prompts.isEmpty()) {
            prompts = nodePromptRepository.listEnabledPrompts(GLOBAL_AGENT_ID, componentCode).stream()
                    .filter(prompt -> promptVersion == null || promptVersion.equals(prompt.getPromptVersion()))
                    .toList();
        }
        return prompts.stream().map(this::loadContent).filter(content -> content != null && !content.isBlank()).toList();
    }

    private String loadContent(AgentNodePromptEntity prompt) {
        String contentRef = prompt.getContentRef();
        if (contentRef == null || contentRef.isBlank()) {
            return null;
        }
        Optional<AgentPayloadEntity> payload = payloadRepository.findPayload(contentRef);
        return payload.map(AgentPayloadEntity::getContent).orElse(contentRef);
    }
}

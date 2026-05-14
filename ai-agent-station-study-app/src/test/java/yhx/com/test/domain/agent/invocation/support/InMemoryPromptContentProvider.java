package yhx.com.test.domain.agent.invocation.support;

import yhx.com.domain.agent.service.prompt.PromptContentProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryPromptContentProvider implements PromptContentProvider {

    private final Map<String, List<String>> prompts = new HashMap<>();

    public InMemoryPromptContentProvider put(String componentCode, String content) {
        prompts.put(componentCode, List.of(content));
        return this;
    }

    @Override
    public List<String> loadRolePrompts(String agentId, String componentCode, String promptVersion) {
        return prompts.getOrDefault(componentCode, List.of("Role prompt for " + componentCode));
    }
}

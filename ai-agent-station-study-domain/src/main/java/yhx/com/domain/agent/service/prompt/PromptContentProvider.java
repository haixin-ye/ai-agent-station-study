package yhx.com.domain.agent.service.prompt;

import java.util.List;

public interface PromptContentProvider {

    List<String> loadRolePrompts(String agentId, String componentCode, String promptVersion);
}

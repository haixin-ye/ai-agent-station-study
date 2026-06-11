package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentCapabilityCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;

import java.util.Optional;
import java.util.Set;

public class AgentActionPermissionPolicy {

    public Optional<String> validate(AgentProfileVO profile, Set<String> effectiveCapabilities, String actionCode) {
        if (actionCode == null || actionCode.isBlank()) {
            return Optional.of("Agent action is missing.");
        }
        if (profile == null || !profile.allowsAction(actionCode)) {
            return Optional.of("Agent action is not allowed by profile: " + actionCode + ".");
        }
      String requiredCapability = requiredCapability(actionCode);
      if (isToolAction(actionCode)) {
          return hasToolCapability(effectiveCapabilities)
                  ? Optional.empty()
                  : Optional.of("Agent action " + actionCode
                  + " requires a granted tool capability but none was granted.");
      }
      if (requiredCapability != null && (effectiveCapabilities == null || !effectiveCapabilities.contains(requiredCapability))) {
          return Optional.of("Agent action " + actionCode
                  + " requires capability " + requiredCapability + " but it was not granted.");
        }
      return Optional.empty();
  }

  private boolean isToolAction(String actionCode) {
      return MainAgentActionTypeEnumVO.CALL_TOOL.code().equals(actionCode)
              || SubAgentActionTypeEnumVO.CALL_TOOL.code().equals(actionCode);
  }

  private boolean hasToolCapability(Set<String> effectiveCapabilities) {
      if (effectiveCapabilities == null || effectiveCapabilities.isEmpty()) {
          return false;
      }
      if (effectiveCapabilities.contains(AgentCapabilityCodeEnumVO.MCP_TOOL.code())) {
          return true;
      }
      return effectiveCapabilities.stream()
              .anyMatch(capability -> capability != null
                      && !AgentCapabilityCodeEnumVO.RAG.code().equals(capability)
                      && !AgentCapabilityCodeEnumVO.ASK_USER.code().equals(capability)
                      && !AgentCapabilityCodeEnumVO.COMMIT.code().equals(capability)
                      && !AgentCapabilityCodeEnumVO.FINAL.code().equals(capability)
                      && !AgentCapabilityCodeEnumVO.DELEGATE_AGENTS.code().equals(capability)
                      && !AgentCapabilityCodeEnumVO.DELEGATE_CODE_AGENT.code().equals(capability));
  }

  public Set<String> defaultEffectiveCapabilities(AgentProfileVO profile) {
        return profile == null || profile.getMaximumCapabilityCodes() == null ? Set.of() : profile.getMaximumCapabilityCodes();
    }

    private String requiredCapability(String actionCode) {
        if (MainAgentActionTypeEnumVO.FINAL.code().equals(actionCode)) {
            return AgentCapabilityCodeEnumVO.FINAL.code();
        }
        if (MainAgentActionTypeEnumVO.DELEGATE_AGENTS.code().equals(actionCode)) {
            return AgentCapabilityCodeEnumVO.DELEGATE_AGENTS.code();
        }
        if (SubAgentActionTypeEnumVO.COMMIT.code().equals(actionCode)) {
            return AgentCapabilityCodeEnumVO.COMMIT.code();
        }
        if (MainAgentActionTypeEnumVO.RETRIEVE_RAG.code().equals(actionCode)
                || SubAgentActionTypeEnumVO.RETRIEVE_RAG.code().equals(actionCode)) {
            return AgentCapabilityCodeEnumVO.RAG.code();
        }
        if (MainAgentActionTypeEnumVO.CALL_TOOL.code().equals(actionCode)
                || SubAgentActionTypeEnumVO.CALL_TOOL.code().equals(actionCode)) {
            return AgentCapabilityCodeEnumVO.MCP_TOOL.code();
        }
        if (MainAgentActionTypeEnumVO.ASK_USER.code().equals(actionCode)
                || SubAgentActionTypeEnumVO.ASK_USER.code().equals(actionCode)) {
            return AgentCapabilityCodeEnumVO.ASK_USER.code();
        }
        return null;
    }
}

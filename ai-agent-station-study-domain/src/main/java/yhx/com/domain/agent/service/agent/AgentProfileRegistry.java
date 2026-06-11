package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentCapabilityCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentProfileTypeEnumVO;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class AgentProfileRegistry {

    private final Map<AgentProfileTypeEnumVO, AgentProfileVO> profiles;

    public AgentProfileRegistry(Map<AgentProfileTypeEnumVO, AgentProfileVO> profiles) {
        this.profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
    }

    public static AgentProfileRegistry defaultRegistry() {
        Map<AgentProfileTypeEnumVO, AgentProfileVO> defaults = new EnumMap<>(AgentProfileTypeEnumVO.class);
        defaults.put(AgentProfileTypeEnumVO.MAIN_AGENT, mainAgentProfile());
        defaults.put(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT, genericSubAgentProfile());
        defaults.put(AgentProfileTypeEnumVO.CODE_AGENT_BRIDGE, codeAgentBridgeProfile());
        return new AgentProfileRegistry(defaults);
    }

    public AgentProfileVO requireProfile(AgentProfileTypeEnumVO profileType) {
        AgentProfileVO profile = profiles.get(profileType);
        if (profile == null) {
            throw new IllegalArgumentException("Agent profile is missing: " + profileType);
        }
        return profile;
    }

    private static AgentProfileVO mainAgentProfile() {
        return AgentProfileVO.builder()
                .profileType(AgentProfileTypeEnumVO.MAIN_AGENT)
                .allowedActionCodes(Set.of(
                        "FINAL",
                        "RETRIEVE_RAG",
                        "CALL_TOOL",
                        "ASK_USER",
                        "PLAN",
                        "CONTINUE",
                        "REPAIR_FINAL",
                        "FAIL",
                        "DELEGATE_AGENTS"))
                .maximumCapabilityCodes(Set.of(
                        AgentCapabilityCodeEnumVO.RAG.code(),
                        AgentCapabilityCodeEnumVO.MCP_TOOL.code(),
                        AgentCapabilityCodeEnumVO.FILE_READ.code(),
                        AgentCapabilityCodeEnumVO.FILE_WRITE.code(),
                        AgentCapabilityCodeEnumVO.ASK_USER.code(),
                        AgentCapabilityCodeEnumVO.DELEGATE_AGENTS.code(),
                        AgentCapabilityCodeEnumVO.FINAL.code()))
                .build();
    }

    private static AgentProfileVO genericSubAgentProfile() {
        return AgentProfileVO.builder()
                .profileType(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT)
                .allowedActionCodes(Set.of(
                        "CALL_TOOL",
                        "RETRIEVE_RAG",
                        "ASK_USER",
                        "CONTINUE",
                        "COMMIT",
                        "FAIL"))
                .maximumCapabilityCodes(Set.of(
                        AgentCapabilityCodeEnumVO.RAG.code(),
                        AgentCapabilityCodeEnumVO.MCP_TOOL.code(),
                        AgentCapabilityCodeEnumVO.FILE_READ.code(),
                        AgentCapabilityCodeEnumVO.FILE_WRITE.code(),
                        AgentCapabilityCodeEnumVO.ASK_USER.code(),
                        AgentCapabilityCodeEnumVO.COMMIT.code()))
                .maxLoopCount(25)
                .maxContextChars(200000)
                .maxSingleToolResultChars(200000)
                .build();
    }

    private static AgentProfileVO codeAgentBridgeProfile() {
        return AgentProfileVO.builder()
                .profileType(AgentProfileTypeEnumVO.CODE_AGENT_BRIDGE)
                .allowedActionCodes(Set.of())
                .maximumCapabilityCodes(Set.of())
                .build();
    }
}

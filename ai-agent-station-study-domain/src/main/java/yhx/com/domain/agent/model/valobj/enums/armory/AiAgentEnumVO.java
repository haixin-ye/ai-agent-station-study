package yhx.com.domain.agent.model.valobj.enums.armory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum AiAgentEnumVO {

    AI_CLIENT_API("AI Client API", "api", "ai_client_api_", "ai_client_api_data_list", "aiClientApiLoadDataStrategy"),
    AI_CLIENT_MODEL("AI Client Model", "model", "ai_client_model_", "ai_client_model_data_list", "aiClientModelLoadDataStrategy"),
    AI_CLIENT_SYSTEM_PROMPT("AI Client System Prompt", "prompt", "ai_client_system_prompt_", "ai_client_system_prompt_data_list", "aiClientSystemPromptLoadDataStrategy"),
    AI_CLIENT_TOOL_MCP("AI Client Tool MCP", "tool_mcp", "ai_client_tool_mcp_", "ai_client_tool_mcp_data_list", "aiClientToolMCPLoadDataStrategy"),
    AI_CLIENT_ADVISOR("AI Client Advisor", "advisor", "ai_client_advisor_", "ai_client_advisor_data_list", "aiClientAdvisorLoadDataStrategy"),
    AI_CLIENT("AI Client", "client", "ai_client_", "ai_client_data_list", "aiClientLoadDataStrategy");

    private String name;
    private String code;
    private String beanNameTag;
    private String dataName;
    private String loadDataStrategy;

    private static final Map<String, AiAgentEnumVO> CODE_MAP = new HashMap<>();

    static {
        for (AiAgentEnumVO enumVO : AiAgentEnumVO.values()) {
            CODE_MAP.put(enumVO.getCode(), enumVO);
        }
    }

    public static AiAgentEnumVO getByCode(String code) {
        if (code == null) {
            return null;
        }

        AiAgentEnumVO result = CODE_MAP.get(code);
        if (result == null) {
            throw new RuntimeException("code value " + code + " not exist!");
        }
        return result;
    }

    public String getBeanName(String id) {
        return beanNameTag + id;
    }
}

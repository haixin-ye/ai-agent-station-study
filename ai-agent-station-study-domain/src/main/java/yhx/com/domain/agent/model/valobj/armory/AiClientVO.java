package yhx.com.domain.agent.model.valobj.armory;

import yhx.com.domain.agent.model.valobj.enums.armory.AiAgentEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI client configuration value object.
 *
 * @author yhx
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientVO {

    private String clientId;

    private String clientName;

    private String description;

    private String modelId;

    private List<String> promptIdList;

    private List<String> mcpIdList;

    private List<String> advisorIdList;

    public String getModelBeanName() {
        return AiAgentEnumVO.AI_CLIENT_MODEL.getBeanName(modelId);
    }

    public List<String> getMcpBeanNameList() {
        List<String> mcpBeanNameList = new ArrayList<>();
        for (String mcpId : mcpIdList) {
            mcpBeanNameList.add(AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getBeanName(mcpId));
        }
        return mcpBeanNameList;
    }

    public List<String> getAdvisorBeanNameList() {
        List<String> advisorBeanNameList = new ArrayList<>();
        for (String advisorId : advisorIdList) {
            advisorBeanNameList.add(AiAgentEnumVO.AI_CLIENT_ADVISOR.getBeanName(advisorId));
        }
        return advisorBeanNameList;
    }

}

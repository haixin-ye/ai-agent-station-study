package yhx.com.domain.agent.model.valobj.armory;

import yhx.com.domain.agent.model.valobj.enums.armory.AiAgentEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI瀹㈡埛绔厤缃紝鍊煎璞? *
 * @author yhx
 * 2025/6/27 18:51
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientVO {

    /**
     * 瀹㈡埛绔疘D
     */
    private String clientId;

    /**
     * 瀹㈡埛绔悕绉?     */
    private String clientName;

    /**
     * 鎻忚堪
     */
    private String description;

    /**
     * 鍏ㄥ眬鍞竴妯″瀷ID
     */
    private String modelId;

    /**
     * Prompt ID List
     */
    private List<String> promptIdList;

    /**
     * MCP ID List
     */
    private List<String> mcpIdList;

    /**
     * 椤鹃棶ID List
     */
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



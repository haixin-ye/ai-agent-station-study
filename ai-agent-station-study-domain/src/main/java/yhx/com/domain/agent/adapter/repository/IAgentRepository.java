package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.valobj.armory.AiAgentClientFlowConfigVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientAdvisorVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientApiVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientModelVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientSystemPromptVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientToolMcpVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientVO;

import java.util.List;
import java.util.Map;

/**
 * AiAgent 仓储接口
 *
 * @author yhx
 * 2025/6/27 16:48
 */
public interface IAgentRepository {

    List<AiClientApiVO> queryAiClientApiVOListByClientIds(List<String> clientIdList);

    List<AiClientModelVO> AiClientModelVOByClientIds(List<String> clientIdList);

    List<AiClientToolMcpVO> AiClientToolMcpVOByClientIds(List<String> clientIdList);

    List<AiClientSystemPromptVO> AiClientSystemPromptVOByClientIds(List<String> clientIdList);

    Map<String, AiClientSystemPromptVO> queryAiClientSystemPromptMapByClientIds(List<String> clientIdList);

    List<AiClientAdvisorVO> AiClientAdvisorVOByClientIds(List<String> clientIdList);

    List<AiClientVO> AiClientVOByClientIds(List<String> clientIdList);

    List<AiClientApiVO> queryAiClientApiVOListByModelIds(List<String> modelIdList);

    List<AiClientModelVO> AiClientModelVOByModelIds(List<String> modelIdList);

    Map<String, AiAgentClientFlowConfigVO> queryAiAgentClientFlowConfig(String aiAgentId);

}

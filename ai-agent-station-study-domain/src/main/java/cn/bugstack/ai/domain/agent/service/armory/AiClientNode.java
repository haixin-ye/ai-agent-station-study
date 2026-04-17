package cn.bugstack.ai.domain.agent.service.armory;

import cn.bugstack.ai.domain.agent.model.entity.ArmoryCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientSystemPromptVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.ai.domain.agent.service.armory.support.RecordingToolCallback;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds ChatClient beans with prompts, advisors, and wrapped MCP callbacks.
 */
@Slf4j
@Service
public class AiClientNode extends AbstractArmorySupport {

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter,
                             DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent build client node: {}", JSON.toJSONString(requestParameter));

        List<AiClientVO> aiClientList = dynamicContext.getValue(dataName());
        if (aiClientList == null || aiClientList.isEmpty()) {
            return router(requestParameter, dynamicContext);
        }

        Map<String, AiClientSystemPromptVO> systemPromptMap =
                dynamicContext.getValue(AiAgentEnumVO.AI_CLIENT_SYSTEM_PROMPT.getDataName());

        for (AiClientVO aiClientVO : aiClientList) {
            StringBuilder defaultSystem = new StringBuilder("Ai 智能体\r\n");
            for (String promptId : aiClientVO.getPromptIdList()) {
                AiClientSystemPromptVO prompt = systemPromptMap.get(promptId);
                if (prompt != null) {
                    defaultSystem.append(prompt.getPromptContent());
                }
            }

            OpenAiChatModel chatModel = getBean(aiClientVO.getModelBeanName());

            List<McpSyncClient> mcpSyncClients = new ArrayList<>();
            for (String mcpBeanName : aiClientVO.getMcpBeanNameList()) {
                mcpSyncClients.add(getBean(mcpBeanName));
            }

            List<Advisor> advisors = new ArrayList<>();
            for (String advisorBeanName : aiClientVO.getAdvisorBeanNameList()) {
                advisors.add(getBean(advisorBeanName));
            }
            advisors.sort(Comparator.comparingInt(Advisor::getOrder).thenComparing(Advisor::getName));
            Advisor[] advisorArray = advisors.toArray(new Advisor[]{});

            ToolCallback[] baseToolCallbacks = new SyncMcpToolCallbackProvider(mcpSyncClients.toArray(new McpSyncClient[]{}))
                    .getToolCallbacks();
            ToolCallback[] recordingToolCallbacks = java.util.Arrays.stream(baseToolCallbacks)
                    .map(RecordingToolCallback::new)
                    .toArray(ToolCallback[]::new);

            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultSystem(defaultSystem.toString())
                    .defaultToolCallbacks(recordingToolCallbacks)
                    .defaultAdvisors(advisorArray)
                    .build();

            registerBean(beanName(aiClientVO.getClientId()), ChatClient.class, chatClient);
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(
            ArmoryCommandEntity requestParameter,
            DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return defaultStrategyHandler;
    }

    @Override
    protected String beanName(String id) {
        return AiAgentEnumVO.AI_CLIENT.getBeanName(id);
    }

    @Override
    protected String dataName() {
        return AiAgentEnumVO.AI_CLIENT.getDataName();
    }
}

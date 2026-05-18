package yhx.com.infrastructure.adapter.port;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import yhx.com.domain.agent.adapter.port.INodeClientPort;
import yhx.com.domain.agent.model.valobj.enums.armory.AiAgentEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientResponse;

import java.util.Map;

@Component
public class SpringAiNodeClientAdapter implements INodeClientPort {

    private final ApplicationContext applicationContext;

    public SpringAiNodeClientAdapter(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public NodeClientResponse call(NodeClientRequest request) {
        validate(request);
        long startedAt = System.currentTimeMillis();
        ChatClient chatClient = resolveChatClient(request.getModelCode());
        String rawOutput = chatClient.prompt()
                .user(request.getPrompt())
                .call()
                .content();
        return NodeClientResponse.builder()
                .rawOutput(rawOutput)
                .modelName(request.getModelCode())
                .latencyMs(System.currentTimeMillis() - startedAt)
                .build();
    }

    private ChatClient resolveChatClient(String modelCode) {
        if (StringUtils.hasText(modelCode)) {
            if (applicationContext.containsBean(modelCode)) {
                return applicationContext.getBean(modelCode, ChatClient.class);
            }
            String armoryBeanName = AiAgentEnumVO.AI_CLIENT.getBeanName(modelCode);
            if (applicationContext.containsBean(armoryBeanName)) {
                return applicationContext.getBean(armoryBeanName, ChatClient.class);
            }
        }
        Map<String, ChatClient> clients = applicationContext.getBeansOfType(ChatClient.class);
        if (clients.size() == 1) {
            return clients.values().iterator().next();
        }
        throw new IllegalStateException("ChatClient bean is not available for modelCode=" + modelCode);
    }

    private void validate(NodeClientRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("NodeClientRequest is required.");
        }
        if (!StringUtils.hasText(request.getPrompt())) {
            throw new IllegalArgumentException("NodeClientRequest.prompt is required.");
        }
    }
}

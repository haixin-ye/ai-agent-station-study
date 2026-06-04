package yhx.com.infrastructure.adapter.port;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import yhx.com.domain.agent.adapter.port.INodeClientPort;
import yhx.com.domain.agent.adapter.repository.IModelRuntimeRepository;
import yhx.com.domain.agent.model.entity.modelruntime.AgentModelApiEntity;
import yhx.com.domain.agent.model.entity.modelruntime.AgentModelProfileEntity;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpringAiNodeClientAdapter implements INodeClientPort {

    private final IModelRuntimeRepository modelRuntimeRepository;

    public SpringAiNodeClientAdapter(IModelRuntimeRepository modelRuntimeRepository) {
        this.modelRuntimeRepository = modelRuntimeRepository;
    }

    @Override
    public NodeClientResponse call(NodeClientRequest request) {
        validate(request);
        long startedAt = System.currentTimeMillis();
        AgentModelProfileEntity modelProfile = resolveModelProfile(request.getModelCode());
        AgentModelApiEntity api = resolveApi(modelProfile.getApiId());
        if (NodeInvocationModeEnumVO.FUNCTION_CALL.equals(invocationMode(request))) {
            String rawResponse = callFunctionModeRaw(api, modelProfile, request);
            return NodeClientResponse.builder()
                    .rawOutput(SpringAiNodeFunctionCallSupport.rawText(rawResponse))
                    .functionCall(SpringAiNodeFunctionCallSupport.extractRequiredFunctionCall(rawResponse))
                    .modelName(modelProfile.getModelName())
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .build();
        }
        ChatClient chatClient = buildCleanChatClient(api, modelProfile, request);
        String rawOutput = chatClient.prompt()
                .user(request.getPrompt())
                .call()
                .content();
        return NodeClientResponse.builder()
                .rawOutput(rawOutput)
                .modelName(modelProfile.getModelName())
                .latencyMs(System.currentTimeMillis() - startedAt)
                .build();
    }

    private String callFunctionModeRaw(AgentModelApiEntity api,
                                       AgentModelProfileEntity modelProfile,
                                       NodeClientRequest request) {
        Map<String, Object> payload = functionModePayload(modelProfile, request);
        return RestClient.builder()
                .baseUrl(api.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + api.getApiKey())
                .build()
                .post()
                .uri(api.getCompletionsPath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    private Map<String, Object> functionModePayload(AgentModelProfileEntity modelProfile, NodeClientRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelProfile.getModelName());
        payload.put("messages", List.of(Map.of(
                "role", "user",
                "content", request.getPrompt()
        )));
        Double temperature = request.getTemperature() == null
                ? modelProfile.getDefaultTemperature()
                : request.getTemperature();
        if (temperature != null) {
            payload.put("temperature", temperature);
        }
        Integer maxOutputTokens = request.getMaxOutputTokens() == null
                ? modelProfile.getDefaultMaxOutputTokens()
                : request.getMaxOutputTokens();
        if (maxOutputTokens != null) {
            payload.put("max_tokens", maxOutputTokens);
        }
        payload.put("tools", SpringAiNodeFunctionCallSupport.toOpenAiFunctionToolPayloads(request.getFunctionSpecs()));
        payload.put("tool_choice", "required");
        payload.put("parallel_tool_calls", false);
        return payload;
    }

    private AgentModelProfileEntity resolveModelProfile(String modelProfileId) {
        if (!StringUtils.hasText(modelProfileId)) {
            throw new IllegalArgumentException("NodeClientRequest.modelCode(modelProfileId) is required.");
        }
        return modelRuntimeRepository.findActiveModelProfile(modelProfileId)
                .orElseThrow(() -> new IllegalStateException("Active model profile is not configured: " + modelProfileId));
    }

    private AgentModelApiEntity resolveApi(String apiId) {
        return modelRuntimeRepository.findActiveApi(apiId)
                .orElseThrow(() -> new IllegalStateException("Active model api is not configured: " + apiId));
    }

    private ChatClient buildCleanChatClient(AgentModelApiEntity api,
                                            AgentModelProfileEntity modelProfile,
                                            NodeClientRequest request) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(api.getBaseUrl())
                .apiKey(api.getApiKey())
                .completionsPath(api.getCompletionsPath())
                .embeddingsPath(api.getEmbeddingsPath())
                .build();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(modelProfile.getModelName());
        Double temperature = request.getTemperature() == null
                ? modelProfile.getDefaultTemperature()
                : request.getTemperature();
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        Integer maxOutputTokens = request.getMaxOutputTokens() == null
                ? modelProfile.getDefaultMaxOutputTokens()
                : request.getMaxOutputTokens();
        if (maxOutputTokens != null) {
            optionsBuilder.maxTokens(maxOutputTokens);
        }
        if (NodeInvocationModeEnumVO.FUNCTION_CALL.equals(invocationMode(request))) {
            optionsBuilder.tools(SpringAiNodeFunctionCallSupport.toOpenAiFunctionTools(request.getFunctionSpecs()));
            optionsBuilder.toolChoice("required");
            optionsBuilder.parallelToolCalls(false);
            optionsBuilder.internalToolExecutionEnabled(false);
        }
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private void validate(NodeClientRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("NodeClientRequest is required.");
        }
        if (!StringUtils.hasText(request.getPrompt())) {
            throw new IllegalArgumentException("NodeClientRequest.prompt is required.");
        }
    }

    private NodeInvocationModeEnumVO invocationMode(NodeClientRequest request) {
        return request.getInvocationMode() == null ? NodeInvocationModeEnumVO.TEXT_JSON : request.getInvocationMode();
    }
}

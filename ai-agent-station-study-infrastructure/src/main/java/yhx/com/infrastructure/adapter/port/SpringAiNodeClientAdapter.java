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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpringAiNodeClientAdapter implements INodeClientPort {

    private static final int MAX_TRANSIENT_IO_ATTEMPTS = 2;
    private static final long TRANSIENT_IO_RETRY_BACKOFF_MILLIS = 220L;

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
            String rawResponse = callWithTransientIoRetry(() -> callFunctionModeRaw(api, modelProfile, request));
            return NodeClientResponse.builder()
                    .rawOutput(SpringAiNodeFunctionCallSupport.rawText(rawResponse))
                    .functionCall(SpringAiNodeFunctionCallSupport.extractRequiredFunctionCall(rawResponse))
                    .modelName(modelProfile.getModelName())
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .build();
        }
        String rawOutput = callWithTransientIoRetry(() -> {
            ChatClient chatClient = buildCleanChatClient(api, modelProfile, request);
            ChatClient.ChatClientRequestSpec prompt = chatClient.prompt();
            if (StringUtils.hasText(request.getSystemPrompt())) {
                prompt.system(request.getSystemPrompt());
            }
            return prompt.user(userPrompt(request)).call().content();
        });
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
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", userPrompt(request)));
        payload.put("messages", messages);
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
        if (!StringUtils.hasText(request.getPrompt()) && !StringUtils.hasText(request.getUserPrompt())) {
            throw new IllegalArgumentException("NodeClientRequest user prompt is required.");
        }
    }

    private String userPrompt(NodeClientRequest request) {
        return StringUtils.hasText(request.getUserPrompt()) ? request.getUserPrompt() : request.getPrompt();
    }

    private NodeInvocationModeEnumVO invocationMode(NodeClientRequest request) {
        return request.getInvocationMode() == null ? NodeInvocationModeEnumVO.TEXT_JSON : request.getInvocationMode();
    }

    private <T> T callWithTransientIoRetry(ModelCall<T> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_TRANSIENT_IO_ATTEMPTS; attempt++) {
            try {
                return call.execute();
            } catch (RuntimeException e) {
                last = e;
                if (attempt >= MAX_TRANSIENT_IO_ATTEMPTS || !isTransientIoFailure(e)) {
                    throw e;
                }
                sleepBeforeRetry();
            }
        }
        throw last == null ? new IllegalStateException("Model call failed without exception.") : last;
    }

    private boolean isTransientIoFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException || current instanceof UncheckedIOException) {
                return true;
            }
            String message = current.getMessage();
            if (StringUtils.hasText(message)) {
                String normalized = message.toLowerCase();
                if (normalized.contains("goaway")
                        || normalized.contains("connection reset")
                        || normalized.contains("broken pipe")
                        || normalized.contains("closed channel")
                        || normalized.contains("connection closed")
                        || normalized.contains("connection prematurely closed")
                        || normalized.contains("read timed out")
                        || normalized.contains("i/o error")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(TRANSIENT_IO_RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying transient model I/O failure.", e);
        }
    }

    @FunctionalInterface
    private interface ModelCall<T> {
        T execute();
    }
}

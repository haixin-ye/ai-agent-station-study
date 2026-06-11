package yhx.com.infrastructure.adapter.port;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IModelRuntimeRepository;
import yhx.com.domain.agent.model.entity.modelruntime.AgentModelApiEntity;
import yhx.com.domain.agent.model.entity.modelruntime.AgentModelProfileEntity;
import yhx.com.domain.agent.model.entity.modelruntime.AgentNodeModelBindingEntity;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientResponse;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class SpringAiNodeClientAdapterFunctionCallHttpTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>();

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handleChatCompletion);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void function_call_mode_sends_tools_schema_and_extracts_provider_tool_call() {
        responseBody.set("""
                {
                  "id":"chatcmpl-test",
                  "object":"chat.completion",
                  "created":1780000000,
                  "model":"fake-chat",
                  "choices":[
                    {
                      "index":0,
                      "message":{
                        "role":"assistant",
                        "content":"",
                        "tool_calls":[
                          {
                            "id":"call-1",
                            "type":"function",
                            "function":{
                              "name":"main_final_answer",
                              "arguments":"{\\\"content\\\":\\\"ok\\\"}"
                            }
                          }
                        ]
                      },
                      "finish_reason":"abort"
                    }
                  ],
                  "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}
                }
                """);
        SpringAiNodeClientAdapter adapter = new SpringAiNodeClientAdapter(repository());

        NodeClientResponse response = adapter.call(NodeClientRequest.builder()
                .runId("run-1")
                .componentCode("MAIN_AGENT")
                .modelCode("profile-1")
                .prompt("choose a function")
                .invocationMode(NodeInvocationModeEnumVO.FUNCTION_CALL)
                .functionSpecs(List.of(NodeFunctionSpecVO.builder()
                        .name("main_final_answer")
                        .description("Return final answer.")
                        .parameterSchema(Map.of(
                                "type", "object",
                                "properties", Map.of("content", Map.of("type", "string")),
                                "required", List.of("content")
                        ))
                        .strict(true)
                        .build()))
                .build());

        Assert.assertNotNull(response.getFunctionCall());
        Assert.assertEquals("main_final_answer", response.getFunctionCall().getName());
        Assert.assertEquals("ok", response.getFunctionCall().getArguments().get("content"));
        Assert.assertTrue(lastRequestBody.get().contains("\"tools\""));
        Assert.assertTrue(lastRequestBody.get().contains("\"main_final_answer\""));
        Assert.assertTrue(lastRequestBody.get().contains("\"tool_choice\":\"required\""));
    }

    @Test
    public void function_call_mode_extracts_tool_call_from_data_prefixed_response() {
        responseBody.set("""
                data: {"id":"chatcmpl-test","object":"chat.completion","created":1780000000,"model":"fake-chat","choices":[{"index":0,"message":{"role":"assistant","content":"","tool_calls":[{"id":"call-1","type":"function","function":{"name":"main_final_answer","arguments":"{\\\"content\\\":\\\"ok\\\"}"}}]},"finish_reason":"abort"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}

                data: [DONE]
                """);
        SpringAiNodeClientAdapter adapter = new SpringAiNodeClientAdapter(repository());

        NodeClientResponse response = adapter.call(NodeClientRequest.builder()
                .runId("run-1")
                .componentCode("MAIN_AGENT")
                .modelCode("profile-1")
                .prompt("choose a function")
                .invocationMode(NodeInvocationModeEnumVO.FUNCTION_CALL)
                .functionSpecs(List.of(NodeFunctionSpecVO.builder()
                        .name("main_final_answer")
                        .description("Return final answer.")
                        .parameterSchema(Map.of(
                                "type", "object",
                                "properties", Map.of("content", Map.of("type", "string")),
                                "required", List.of("content")
                        ))
                        .strict(true)
                        .build()))
                .build());

        Assert.assertNotNull(response.getFunctionCall());
        Assert.assertEquals("main_final_answer", response.getFunctionCall().getName());
        Assert.assertEquals("ok", response.getFunctionCall().getArguments().get("content"));
    }

    @Test
    public void text_json_mode_keeps_plain_content_flow() {
        responseBody.set("""
                {
                  "id":"chatcmpl-test",
                  "object":"chat.completion",
                  "created":1780000000,
                  "model":"fake-chat",
                  "choices":[
                    {
                      "index":0,
                      "message":{
                        "role":"assistant",
                        "content":"{\\\"action\\\":\\\"FINAL\\\",\\\"stateDelta\\\":{\\\"finalAnswerCandidate\\\":{\\\"content\\\":\\\"ok\\\"}}}"
                      },
                      "finish_reason":"stop"
                    }
                  ],
                  "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}
                }
                """);
        SpringAiNodeClientAdapter adapter = new SpringAiNodeClientAdapter(repository());

        NodeClientResponse response = adapter.call(NodeClientRequest.builder()
                .runId("run-1")
                .componentCode("MAIN_AGENT")
                .modelCode("profile-1")
                .prompt("return json")
                .invocationMode(NodeInvocationModeEnumVO.TEXT_JSON)
                .build());

        Assert.assertNull(response.getFunctionCall());
        Assert.assertTrue(response.getRawOutput().contains("\"action\":\"FINAL\""));
        Assert.assertFalse(lastRequestBody.get().contains("\"tools\""));
    }

    private void handleChatCompletion(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            lastRequestBody.set(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
        byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private IModelRuntimeRepository repository() {
        return new IModelRuntimeRepository() {
            @Override
            public Optional<AgentNodeModelBindingEntity> findActiveBindingByNodeCode(String nodeCode) {
                return Optional.empty();
            }

            @Override
            public Optional<AgentModelProfileEntity> findActiveModelProfile(String modelProfileId) {
                return Optional.of(AgentModelProfileEntity.builder()
                        .modelProfileId(modelProfileId)
                        .apiId("api-1")
                        .modelName("fake-chat")
                        .defaultTemperature(0.1)
                        .defaultMaxOutputTokens(128)
                        .enabled(true)
                        .build());
            }

            @Override
            public Optional<AgentModelApiEntity> findActiveApi(String apiId) {
                return Optional.of(AgentModelApiEntity.builder()
                        .apiId(apiId)
                        .provider("openai-compatible")
                        .baseUrl(baseUrl)
                        .apiKey("test-key")
                        .completionsPath("/v1/chat/completions")
                        .embeddingsPath("/v1/embeddings")
                        .enabled(true)
                        .build());
            }

            @Override
            public List<AgentNodeModelBindingEntity> listActiveBindings() {
                return List.of();
            }
        };
    }
}

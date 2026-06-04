package yhx.com.infrastructure.adapter.port;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.api.OpenAiApi;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionCallVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO;

import java.util.List;
import java.util.Map;

public class SpringAiNodeFunctionCallSupportTest {

    @Test
    public void converts_node_function_spec_to_openai_function_tool_without_callback_execution() {
        List<OpenAiApi.FunctionTool> tools = SpringAiNodeFunctionCallSupport.toOpenAiFunctionTools(List.of(
                NodeFunctionSpecVO.builder()
                        .name("main_call_tool")
                        .description("Declare a Runtime tool action.")
                        .parameterSchema(Map.of(
                                "type", "object",
                                "properties", Map.of("toolName", Map.of("type", "string")),
                                "required", List.of("toolName")
                        ))
                        .strict(true)
                        .build()
        ));

        Assert.assertEquals(1, tools.size());
        Assert.assertEquals("main_call_tool", tools.get(0).getFunction().getName());
        Assert.assertEquals(Boolean.TRUE, tools.get(0).getFunction().getStrict());
        Assert.assertEquals("object", tools.get(0).getFunction().getParameters().get("type"));
    }

    @Test
    public void extracts_first_provider_tool_call_as_node_function_call() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(
                "",
                Map.of(),
                List.of(new AssistantMessage.ToolCall("call-1", "function", "main_final_answer", "{\"content\":\"ok\"}"))
        ))));

        NodeFunctionCallVO call = SpringAiNodeFunctionCallSupport.extractRequiredFunctionCall(response);

        Assert.assertEquals("main_final_answer", call.getName());
        Assert.assertEquals("ok", call.getArguments().get("content"));
        Assert.assertEquals("{\"content\":\"ok\"}", call.getRawArgumentsJson());
    }

    @Test
    public void extracts_provider_tool_call_from_data_prefixed_raw_response() {
        String rawResponse = """
                data: {
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
                  ]
                }

                data: [DONE]
                """;

        NodeFunctionCallVO call = SpringAiNodeFunctionCallSupport.extractRequiredFunctionCall(rawResponse);

        Assert.assertEquals("main_final_answer", call.getName());
        Assert.assertEquals("ok", call.getArguments().get("content"));
        Assert.assertEquals("", SpringAiNodeFunctionCallSupport.rawText(rawResponse));
    }

    @Test
    public void extracts_provider_tool_call_from_delta_raw_response() {
        String rawResponse = """
                {
                  "id":"chatcmpl-test",
                  "object":"chat.completion.chunk",
                  "created":1780000000,
                  "model":"fake-chat",
                  "choices":[
                    {
                      "index":0,
                      "delta":{
                        "role":"assistant",
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
                      "finish_reason":"tool_calls"
                    }
                  ]
                }
                """;

        NodeFunctionCallVO call = SpringAiNodeFunctionCallSupport.extractRequiredFunctionCall(rawResponse);

        Assert.assertEquals("main_final_answer", call.getName());
        Assert.assertEquals("ok", call.getArguments().get("content"));
    }

    @Test
    public void missing_raw_provider_tool_call_includes_response_preview() {
        String rawResponse = """
                {
                  "choices":[
                    {
                      "message":{
                        "role":"assistant",
                        "content":"plain answer"
                      },
                      "finish_reason":"stop"
                    }
                  ]
                }
                """;

        IllegalStateException error = Assert.assertThrows(IllegalStateException.class,
                () -> SpringAiNodeFunctionCallSupport.extractRequiredFunctionCall(rawResponse));

        Assert.assertTrue(error.getMessage().contains("FUNCTION_CALL_EXPECTED_BUT_MISSING"));
        Assert.assertTrue(error.getMessage().contains("plain answer"));
    }

    @Test
    public void missing_provider_tool_call_fails_explicitly() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("plain text"))));

        IllegalStateException error = Assert.assertThrows(IllegalStateException.class,
                () -> SpringAiNodeFunctionCallSupport.extractRequiredFunctionCall(response));

        Assert.assertTrue(error.getMessage().contains("FUNCTION_CALL_EXPECTED_BUT_MISSING"));
    }
}

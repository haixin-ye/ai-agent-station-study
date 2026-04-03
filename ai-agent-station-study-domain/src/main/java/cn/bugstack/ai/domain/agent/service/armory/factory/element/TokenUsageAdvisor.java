package cn.bugstack.ai.domain.agent.service.armory.factory.element;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.TokenUsageAccumulator;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emit token usage to SSE after every chat client call.
 */
public class TokenUsageAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageAdvisor.class);

    private static final String KEY_EMITTER = "token_stat_emitter";
    private static final String KEY_SESSION_ID = "token_stat_session_id";
    private static final String KEY_CLIENT_ID = "token_stat_client_id";
    private static final String KEY_CLIENT_TYPE = "token_stat_client_type";
    private static final String KEY_STEP = "token_stat_step";
    private static final String KEY_ACCUMULATOR = "token_stat_accumulator";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        emitTokenUsage(response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return BaseAdvisor.super.adviseStream(chatClientRequest, streamAdvisorChain);
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    private void emitTokenUsage(ChatClientResponse response) {
        try {
            Map<String, Object> ctx = response.context();
            if (ctx == null) {
                return;
            }

            TokenUsage usage = extractUsage(response);
            TokenUsageAccumulator accumulator = getAccumulator(ctx.get(KEY_ACCUMULATOR));
            if (accumulator != null) {
                accumulator.add(usage.inputTokens, usage.outputTokens, usage.totalTokens);
            }

            ResponseBodyEmitter emitter = ctx.get(KEY_EMITTER) instanceof ResponseBodyEmitter e ? e : null;
            if (emitter == null) {
                return;
            }

            String sessionId = toStringValue(ctx.get(KEY_SESSION_ID));
            String clientId = toStringValue(ctx.get(KEY_CLIENT_ID));
            String clientType = toStringValue(ctx.get(KEY_CLIENT_TYPE));
            Integer step = toInteger(ctx.get(KEY_STEP));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientId", clientId);
            payload.put("clientType", clientType);
            payload.put("inputTokens", usage.inputTokens);
            payload.put("outputTokens", usage.outputTokens);
            payload.put("totalTokens", usage.totalTokens);
            payload.put("usageMissing", usage.usageMissing);

            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createTokenClientUsageResult(
                    step, JSON.toJSONString(payload), sessionId
            );
            emitter.send("data: " + JSON.toJSONString(result) + "\n\n");
        } catch (IOException e) {
            log.warn("emit token usage SSE failed: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("emit token usage failed: {}", e.getMessage());
        }
    }

    private TokenUsage extractUsage(ChatClientResponse response) {
        try {
            Object chatResponse = response.chatResponse();
            Object metadata = invoke(chatResponse, "getMetadata");
            Object usageObj = invoke(metadata, "getUsage");
            long input = firstLong(usageObj, "getPromptTokens", "getInputTokens");
            long output = firstLong(usageObj, "getCompletionTokens", "getOutputTokens");
            long total = firstLong(usageObj, "getTotalTokens");
            if (total <= 0) {
                total = Math.max(input, 0L) + Math.max(output, 0L);
            }
            boolean usageMissing = input == 0L && output == 0L && total == 0L;
            return new TokenUsage(Math.max(input, 0L), Math.max(output, 0L), Math.max(total, 0L), usageMissing);
        } catch (Exception e) {
            return new TokenUsage(0L, 0L, 0L, true);
        }
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private long firstLong(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = invoke(target, methodName);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        return 0L;
    }

    private String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private TokenUsageAccumulator getAccumulator(Object value) {
        if (value instanceof TokenUsageAccumulator accumulator) {
            return accumulator;
        }
        return null;
    }

    private record TokenUsage(long inputTokens, long outputTokens, long totalTokens, boolean usageMissing) {
    }
}

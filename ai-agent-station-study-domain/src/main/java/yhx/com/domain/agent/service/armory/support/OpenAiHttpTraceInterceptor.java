package yhx.com.domain.agent.service.armory.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Logs raw OpenAI-compatible request/response bodies so we can verify whether
 * the upstream actually returns protocol-level tool_calls or only plain text.
 */
public class OpenAiHttpTraceInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger TRACE_LOG = LoggerFactory.getLogger("OPENAI_HTTP_TRACE");
    private static final int DASHSCOPE_MAX_INPUT_TOKENS = 131072;
    private static final String DASHSCOPE_HOST = "dashscope.aliyuncs.com";

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        byte[] outboundBody = applyDashScopeTokenBudget(request, body);
        String requestBody = new String(outboundBody, StandardCharsets.UTF_8);
        ClientHttpResponse response = execution.execute(request, outboundBody);
        byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());
        String responseText = new String(responseBody, StandardCharsets.UTF_8);
        int rawStatusCode = response.getRawStatusCode();
        String statusText = response.getStatusText();
        TRACE_LOG.info("OpenAI HTTP request | method={} | uri={} | body={}",
                request.getMethod(), request.getURI(), requestBody);
        TRACE_LOG.info("OpenAI HTTP response | method={} | uri={} | rawStatus={} | statusText={} | body={}",
                request.getMethod(), request.getURI(), rawStatusCode, statusText, responseText);
        return response;
    }

    static byte[] applyDashScopeTokenBudget(HttpRequest request, byte[] body) {
        if (body == null || body.length == 0) {
            return body;
        }
        try {
            String requestBody = new String(body, StandardCharsets.UTF_8);
            JSONObject payload = JSON.parseObject(requestBody);
            if (payload == null || payload.containsKey("max_input_tokens")) {
                return body;
            }
            String model = payload.getString("model");
            if (!isDashScopeQwenRequest(request, model)) {
                return body;
            }
            payload.put("max_input_tokens", DASHSCOPE_MAX_INPUT_TOKENS);
            return payload.toJSONString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return body;
        }
    }

    private static boolean isDashScopeQwenRequest(HttpRequest request, String model) {
        String host = request == null || request.getURI() == null ? "" : request.getURI().getHost();
        if (host != null && host.contains(DASHSCOPE_HOST)) {
            return true;
        }
        return model != null && model.toLowerCase().startsWith("qwen");
    }
}

package cn.bugstack.ai.domain.agent.service.armory.support;

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

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String requestBody = new String(body, StandardCharsets.UTF_8);
        ClientHttpResponse response = execution.execute(request, body);
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
}

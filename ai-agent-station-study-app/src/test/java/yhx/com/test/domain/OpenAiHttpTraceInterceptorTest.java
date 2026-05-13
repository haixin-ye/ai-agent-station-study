package yhx.com.test.domain;

import yhx.com.domain.agent.service.armory.support.OpenAiHttpTraceInterceptor;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class OpenAiHttpTraceInterceptorTest {

    @Test
    public void testDashScopeQwenRequestAddsMaxInputTokens() throws Exception {
        OpenAiHttpTraceInterceptor interceptor = new OpenAiHttpTraceInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest();
        request.setURI(new URI("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"));
        byte[] body = "{\"model\":\"qwen-plus\",\"messages\":[]}".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> capturedBody = new AtomicReference<>();

        interceptor.intercept(request, body, (httpRequest, outboundBody) -> {
            capturedBody.set(new String(outboundBody, StandardCharsets.UTF_8));
            return new MockClientHttpResponse("{}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        });

        Assert.assertTrue(capturedBody.get().contains("\"max_input_tokens\":131072"));
    }

    @Test
    public void testOpenAiRequestKeepsBodyUnchanged() throws Exception {
        OpenAiHttpTraceInterceptor interceptor = new OpenAiHttpTraceInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest();
        request.setURI(new URI("https://api.openai.com/v1/chat/completions"));
        byte[] body = "{\"model\":\"gpt-4.1-mini\",\"messages\":[]}".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> capturedBody = new AtomicReference<>();

        interceptor.intercept(request, body, (httpRequest, outboundBody) -> {
            capturedBody.set(new String(outboundBody, StandardCharsets.UTF_8));
            return new MockClientHttpResponse("{}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        });

        Assert.assertEquals(new String(body, StandardCharsets.UTF_8), capturedBody.get());
    }
}

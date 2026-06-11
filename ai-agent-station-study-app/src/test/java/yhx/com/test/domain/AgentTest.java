package yhx.com.test.domain;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Agent assembly smoke tests.
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AgentTest {

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Test
    public void testOpenAiApi() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("/v1/embeddings")
                .build();
        log.info("OpenAiApi created: {}", openAiApi);
    }

    @Test
    public void testChatModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("/v1/embeddings")
                .build();
        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .build();
        log.info("Chat model created: {}", openAiChatModel);
    }

    @Test
    public void testChatClient() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("/v1/embeddings")
                .build();
        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .build();
        ChatClient chatClient = ChatClient.builder(openAiChatModel).build();
        log.info("Chat client built: {}", chatClient);
    }

}

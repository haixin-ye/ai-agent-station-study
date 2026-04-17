package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.armory.factory.element.RagAnswerAdvisor;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RagAnswerAdvisorTest {

    @Test
    public void test_adviseCall_shouldPreservePromptOptionsWhenInjectingContext() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        Mockito.when(vectorStore.similaritySearch(Mockito.any(SearchRequest.class)))
                .thenReturn(List.of(new Document("doc-1")));

        RagAnswerAdvisor advisor = new RagAnswerAdvisor(vectorStore, SearchRequest.builder().build());
        CapturingCallAdvisorChain chain = new CapturingCallAdvisorChain(chatClientResponse("ok"));

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("gpt-4.1-mini")
                .build();
        Prompt prompt = new Prompt(List.of(new UserMessage("list files")), options);
        ChatClientRequest request = new ChatClientRequest(prompt, new HashMap<>(Map.of("qa_query", "list files")));

        advisor.adviseCall(request, chain);

        Assert.assertNotNull(chain.capturedRequest);
        Assert.assertSame(options, chain.capturedRequest.prompt().getOptions());
        Assert.assertTrue(chain.capturedRequest.context().containsKey("question_answer_context"));
    }

    private static ChatClientResponse chatClientResponse(String text) {
        return new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))),
                new HashMap<>()
        );
    }

    private static class CapturingCallAdvisorChain implements CallAdvisorChain {

        private final ChatClientResponse response;
        private ChatClientRequest capturedRequest;

        private CapturingCallAdvisorChain(ChatClientResponse response) {
            this.response = response;
        }

        @Override
        public ChatClientResponse nextCall(ChatClientRequest chatClientRequest) {
            this.capturedRequest = chatClientRequest;
            return response;
        }

        @Override
        public List<CallAdvisor> getCallAdvisors() {
            return List.of();
        }
    }
}

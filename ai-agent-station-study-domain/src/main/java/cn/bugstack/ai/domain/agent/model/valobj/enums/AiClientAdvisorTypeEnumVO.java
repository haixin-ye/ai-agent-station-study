package cn.bugstack.ai.domain.agent.model.valobj.enums;

import cn.bugstack.ai.domain.agent.model.valobj.AiClientAdvisorVO;
import cn.bugstack.ai.domain.agent.service.armory.factory.element.PromptInjectionSanitizerAdvisor;
import cn.bugstack.ai.domain.agent.service.armory.factory.element.RagAnswerAdvisor;
import cn.bugstack.ai.domain.agent.service.armory.factory.element.TokenUsageAdvisor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Advisor 类型策略枚举。
 * 说明：新增 chatModelProvider 参数是为了让需要“额外模型”的 Advisor
 * （如 PromptInjectionSanitizer）在创建时按 BeanName 动态获取模型实例。
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum AiClientAdvisorTypeEnumVO {

    CHAT_MEMORY("ChatMemory", "chat memory") {
        @Override
        public Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO,
                                     VectorStore vectorStore,
                                     Function<String, OpenAiChatModel> chatModelProvider) {
            AiClientAdvisorVO.ChatMemory chatMemory = aiClientAdvisorVO.getChatMemory();
            int maxMessages = chatMemory == null ? 5 : chatMemory.getMaxMessages();
            return PromptChatMemoryAdvisor.builder(
                    MessageWindowChatMemory.builder()
                            .maxMessages(maxMessages)
                            .build()
            ).build();
        }
    },

    RAG_ANSWER("RagAnswer", "rag answer") {
        @Override
        public Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO,
                                     VectorStore vectorStore,
                                     Function<String, OpenAiChatModel> chatModelProvider) {
            AiClientAdvisorVO.RagAnswer ragAnswer = aiClientAdvisorVO.getRagAnswer();
            int topK = ragAnswer == null ? 4 : ragAnswer.getTopK();
            String filterExpression = ragAnswer == null ? null : ragAnswer.getFilterExpression();
            return new RagAnswerAdvisor(vectorStore, SearchRequest.builder()
                    .topK(topK)
                    .filterExpression(filterExpression)
                    .build());
        }
    },

    TOKEN_USAGE("TokenUsage", "token usage") {
        @Override
        public Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO,
                                     VectorStore vectorStore,
                                     Function<String, OpenAiChatModel> chatModelProvider) {
            return new TokenUsageAdvisor();
        }
    },

    PROMPT_INJECTION_SANITIZER("PromptInjectionSanitizer", "sanitize prompt injection then safeguard") {
        @Override
        public Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO,
                                     VectorStore vectorStore,
                                     Function<String, OpenAiChatModel> chatModelProvider) {
            AiClientAdvisorVO.PromptInjectionSanitizer config = aiClientAdvisorVO.getPromptInjectionSanitizer();
            if (config == null || isBlank(config.getSanitizeModelBeanName())) {
                throw new RuntimeException("Prompt注入检测需要提供model名称");
            }

            OpenAiChatModel sanitizeModel = chatModelProvider.apply(config.getSanitizeModelBeanName());
            if (sanitizeModel == null) {
                throw new RuntimeException("Prompt注入检测 model bean not found: " + config.getSanitizeModelBeanName());
            }

            int order = aiClientAdvisorVO.getOrderNum() == null ? 0 : aiClientAdvisorVO.getOrderNum();
            long timeoutMs = config.getSanitizeTimeoutMs() == null ? 1500L : config.getSanitizeTimeoutMs();

            return new PromptInjectionSanitizerAdvisor(
                    sanitizeModel,
                    config.getSanitizePromptTemplate(),
                    timeoutMs,
                    config.getSafeGuardWords(),
                    config.getRejectMessage(),
                    order
            );
        }
    };

    private String code;
    private String info;

    private static final Map<String, AiClientAdvisorTypeEnumVO> CODE_MAP = new HashMap<>();

    static {
        for (AiClientAdvisorTypeEnumVO enumVO : values()) {
            CODE_MAP.put(enumVO.getCode(), enumVO);
        }
    }

    public abstract Advisor createAdvisor(AiClientAdvisorVO aiClientAdvisorVO,
                                          VectorStore vectorStore,
                                          Function<String, OpenAiChatModel> chatModelProvider);

    public static AiClientAdvisorTypeEnumVO getByCode(String code) {
        AiClientAdvisorTypeEnumVO enumVO = CODE_MAP.get(code);
        if (enumVO == null) {
            throw new RuntimeException("err! advisorType " + code + " not exist!");
        }
        return enumVO;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package yhx.com.domain.agent.model.valobj.armory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Advisor configuration value object parsed from ai_client_advisor and ext_param.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientAdvisorVO {

    private String advisorId;
    private String advisorName;
    private String advisorType;
    private Integer orderNum;

    private ChatMemory chatMemory;
    private RagAnswer ragAnswer;
    private PromptInjectionSanitizer promptInjectionSanitizer;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatMemory {
        private int maxMessages;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RagAnswer {
        private int topK = 4;
        private String filterExpression;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PromptInjectionSanitizer {
        private String sanitizeModelBeanName;
        private String sanitizePromptTemplate;
        private Long sanitizeTimeoutMs;
        private List<String> safeGuardWords;
        private String rejectMessage;
    }
}

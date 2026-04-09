package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 顾问配置值对象（由 ai_client_advisor + ext_param 解析而来）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientAdvisorVO {

    /** 顾问ID */
    private String advisorId;
    /** 顾问名称 */
    private String advisorName;
    /** 顾问类型编码 */
    private String advisorType;
    /** 执行顺序 */
    private Integer orderNum;

    /** ChatMemory 扩展配置 */
    private ChatMemory chatMemory;
    /** RagAnswer 扩展配置 */
    private RagAnswer ragAnswer;
    /** Prompt 注入清洗扩展配置 */
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
        /** 轻量清洗模型的 BeanName（必填） */
        private String sanitizeModelBeanName;
        /** 清洗提示词模板（可选） */
        private String sanitizePromptTemplate;
        /** 清洗超时（毫秒） */
        private Long sanitizeTimeoutMs;
        /** SafeGuard 敏感词（可选） */
        private List<String> safeGuardWords;
        /** 拒绝时返回文案（可选） */
        private String rejectMessage;
    }
}

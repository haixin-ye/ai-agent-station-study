package yhx.com.domain.agent.model.valobj.armory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 椤鹃棶閰嶇疆鍊煎璞★紙鐢?ai_client_advisor + ext_param 瑙ｆ瀽鑰屾潵锛夈€?
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientAdvisorVO {

    /** 椤鹃棶ID */
    private String advisorId;
    /** 椤鹃棶鍚嶇О */
    private String advisorName;
    /** 椤鹃棶绫诲瀷缂栫爜 */
    private String advisorType;
    /** 鎵ц椤哄簭 */
    private Integer orderNum;

    /** ChatMemory 鎵╁睍閰嶇疆 */
    private ChatMemory chatMemory;
    /** RagAnswer 鎵╁睍閰嶇疆 */
    private RagAnswer ragAnswer;
    /** Prompt 娉ㄥ叆娓呮礂鎵╁睍閰嶇疆 */
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
        /** 杞婚噺娓呮礂妯″瀷鐨?BeanName锛堝繀濉級 */
        private String sanitizeModelBeanName;
        /** 娓呮礂鎻愮ず璇嶆ā鏉匡紙鍙€夛級 */
        private String sanitizePromptTemplate;
        /** 娓呮礂瓒呮椂锛堟绉掞級 */
        private Long sanitizeTimeoutMs;
        /** SafeGuard 鏁忔劅璇嶏紙鍙€夛級 */
        private List<String> safeGuardWords;
        /** 鎷掔粷鏃惰繑鍥炴枃妗堬紙鍙€夛級 */
        private String rejectMessage;
    }
}


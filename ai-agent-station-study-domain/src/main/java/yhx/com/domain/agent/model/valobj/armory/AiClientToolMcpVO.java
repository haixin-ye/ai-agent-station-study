package yhx.com.domain.agent.model.valobj.armory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP瀹㈡埛绔厤缃紝鍊煎璞?
 *
 * @author yhx
 * 2025/6/27 18:29
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientToolMcpVO {

    /**
     * MCP ID
     */
    private String mcpId;

    /**
     * MCP鍚嶇О
     */
    private String mcpName;

    /**
     * 浼犺緭绫诲瀷(sse/stdio)
     */
    private String transportType;

    /**
     * 浼犺緭閰嶇疆(sse/stdio)
     */
    private String transportConfig;

    /**
     * 璇锋眰瓒呮椂鏃堕棿(鍒嗛挓)
     */
    private Integer requestTimeout;

    /**
     * 浼犺緭閰嶇疆 - sse
     */
    private TransportConfigSse transportConfigSse;

    /**
     * 浼犺緭閰嶇疆 - stdio
     */
    private TransportConfigStdio transportConfigStdio;

    /**
     * 宸ュ叿璋冪敤绛栫暐锛堜粠 transport_config.policy 瑙ｆ瀽锛夈€?
     */
    private ToolPolicy toolPolicy;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TransportConfigSse {
        private String baseUri;
        private String sseEndpoint;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TransportConfigStdio {

        private Map<String, Stdio> stdio;

        @Data
        public static class Stdio {
            private String command;
            private List<String> args;
            private Map<String, String> env;
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolPolicy {
        /**
         * 鍙傛暟鏍￠獙锛氬繀濉弬鏁板悕鍒楄〃銆?
         */
        private List<String> requiredArgs;

        /**
         * 鍙傛暟绫诲瀷绾︽潫锛坅rg -> type锛夈€?
         */
        private Map<String, String> argTypes;

        /**
         * 榛樿鍙傛暟锛坅rg -> value锛夈€?
         */
        private Map<String, String> defaultArgs;

        /**
         * 鍏佽璋冪敤鏉′欢锛堜緥濡?NEED_FILE_EVIDENCE锛夈€?
         */
        private List<String> allowedWhen;

        /**
         * 閲嶈瘯绛栫暐銆?
         */
        private RetryPolicy retryPolicy;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RetryPolicy {
        private Integer maxRetry;
    }

}


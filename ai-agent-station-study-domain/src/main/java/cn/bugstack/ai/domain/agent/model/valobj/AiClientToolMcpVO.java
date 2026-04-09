package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP客户端配置，值对象
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
     * MCP名称
     */
    private String mcpName;

    /**
     * 传输类型(sse/stdio)
     */
    private String transportType;

    /**
     * 传输配置(sse/stdio)
     */
    private String transportConfig;

    /**
     * 请求超时时间(分钟)
     */
    private Integer requestTimeout;

    /**
     * 传输配置 - sse
     */
    private TransportConfigSse transportConfigSse;

    /**
     * 传输配置 - stdio
     */
    private TransportConfigStdio transportConfigStdio;

    /**
     * 工具调用策略（从 transport_config.policy 解析）。
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
         * 参数校验：必填参数名列表。
         */
        private List<String> requiredArgs;

        /**
         * 参数类型约束（arg -> type）。
         */
        private Map<String, String> argTypes;

        /**
         * 默认参数（arg -> value）。
         */
        private Map<String, String> defaultArgs;

        /**
         * 允许调用条件（例如 NEED_FILE_EVIDENCE）。
         */
        private List<String> allowedWhen;

        /**
         * 重试策略。
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

package yhx.com.domain.agent.model.valobj.armory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP client configuration value object.
 *
 * @author yhx
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientToolMcpVO {

    private String mcpId;

    private String mcpName;

    private String transportType;

    private String transportConfig;

    private Integer requestTimeout;

    private TransportConfigSse transportConfigSse;

    private TransportConfigStdio transportConfigStdio;

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
        private List<String> requiredArgs;
        private Map<String, String> argTypes;
        private Map<String, String> defaultArgs;
        private List<String> allowedWhen;
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

package cn.bugstack.ai.domain.agent.service.armory.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Wraps Spring AI ToolCallback and records the real callback request/response.
 */
@Slf4j
public class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public RecordingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition() == null ? "" : getToolDefinition().name();
        log.info("MCP callback invoke start | toolName={} | request={}", toolName, toolInput);
        try {
            String response = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            ToolCallCaptureHolder.record(toolName, toolInput, response, true, "", "");
            log.info("MCP callback invoke success | toolName={} | response={}", toolName, response);
            return response;
        } catch (RuntimeException e) {
            ToolCallCaptureHolder.record(toolName, toolInput, "", false,
                    e.getClass().getSimpleName(), e.getMessage());
            log.error("MCP callback invoke failed | toolName={} | errorType={} | errorMessage={}",
                    toolName, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }
}

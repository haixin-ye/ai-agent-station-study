package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeCommandVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationRequestVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;
import yhx.com.domain.agent.service.tool.port.McpToolInvokerPort;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ToolRuntime {

    private static final int TOOL_RESULT_TEXT_LIMIT = 500;
    private static final int TOOL_ARGUMENT_VALUE_LIMIT = 500;

    private final McpToolInvokerPort mcpToolInvokerPort;
    private final ToolReceiptCapture receiptCapture;
    private final ToolFailureMapper failureMapper;
    private final IToolRepository toolRepository;

    public ToolRuntime(McpToolInvokerPort mcpToolInvokerPort,
                       ToolReceiptCapture receiptCapture,
                       ToolFailureMapper failureMapper,
                       IToolRepository toolRepository) {
        this.mcpToolInvokerPort = mcpToolInvokerPort;
        this.receiptCapture = receiptCapture;
        this.failureMapper = failureMapper;
        this.toolRepository = toolRepository;
    }

    public ToolInvocationResultVO invoke(ToolInvocationRequestVO request) {
        String validationFailure = validateRequest(request);
        if (validationFailure != null) {
            markFailure(request, ToolCallStatusEnumVO.FAILED, validationFailure);
            return failure(request, ToolInvocationStatusEnumVO.INVALID_INTENT, validationFailure, validationFailure);
        }
        if (Boolean.TRUE.equals(request.getApprovalRequired()) && isBlank(request.getApprovalId())) {
            toolRepository.updateToolCallStatus(request.getToolCallId(), ToolCallStatusEnumVO.APPROVAL_PENDING);
            return failure(request, ToolInvocationStatusEnumVO.NEEDS_USER_ACTION, "TOOL_APPROVAL_REQUIRED", "Tool approval is required before execution.");
        }
        String schemaFailure = validateSchema(request.getMcpTool().getInputSchema(), request.getArguments());
        if (schemaFailure != null) {
            markFailure(request, ToolCallStatusEnumVO.FAILED, "TOOL_SCHEMA_ERROR");
            return failure(request, ToolInvocationStatusEnumVO.INVALID_INTENT, "TOOL_SCHEMA_ERROR", schemaFailure);
        }
        toolRepository.updateToolCallStatus(request.getToolCallId(), ToolCallStatusEnumVO.RUNNING);
        McpToolInvokeResultVO invokeResult = mcpToolInvokerPort.invoke(McpToolInvokeCommandVO.builder()
                .mcpServerCode(request.getMcpTool().getMcpServerCode())
                .toolName(request.getMcpTool().getToolName())
                .arguments(request.getArguments())
                .timeoutMs(request.getTimeoutMs())
                .build());
        String receiptRef = receiptCapture.capture(invokeResult);
        String resultContent = resultContent(invokeResult);
        ToolCallStatusEnumVO callStatus = failureMapper.callStatus(invokeResult);
        String failureCode = failureMapper.failureCode(invokeResult);
        toolRepository.saveToolReceipt(request.getToolCallId(), request.getArgumentsRef(), receiptRef, callStatus, failureCode);
        return ToolInvocationResultVO.builder()
                .status(failureMapper.invocationStatus(invokeResult))
                .toolCallId(request.getToolCallId())
                .toolInvocationId(request.getToolInvocationId())
                .receiptRef(receiptRef)
                .resultSummary(resultSummary(request, invokeResult))
                .resultContent(resultContent)
                .resultContentRef(receiptRef)
                .resultContentFormat(resultContent == null ? null : "TEXT")
                .resultTotalChars(resultContent == null ? null : resultContent.length())
                .resultTotalBytes(resultContent == null ? null : (long) resultContent.getBytes(StandardCharsets.UTF_8).length)
                .failureCode(failureCode)
                .failureMessage(invokeResult == null ? "MCP tool was not called." : invokeResult.getErrorMessage())
                .build();
    }

    private String resultSummary(ToolInvocationRequestVO request, McpToolInvokeResultVO invokeResult) {
        String resultText = resultContent(invokeResult);
        if (isBlank(resultText)) {
            return null;
        }
        if (request == null || request.getMcpTool() == null || isBlank(request.getMcpTool().getToolName())) {
            return truncate(resultText, TOOL_RESULT_TEXT_LIMIT);
        }
        return "tool=" + request.getMcpTool().getToolName()
                + ", arguments=" + compactArguments(request.getArguments())
                + ", result=" + truncate(resultText, TOOL_RESULT_TEXT_LIMIT);
    }

    private String resultContent(McpToolInvokeResultVO invokeResult) {
        if (invokeResult == null || invokeResult.getReceipt() == null) {
            return null;
        }
        Object contentText = invokeResult.getReceipt().get("contentText");
        if (contentText != null && !String.valueOf(contentText).isBlank()) {
            return String.valueOf(contentText);
        }
        Object rawResult = invokeResult.getReceipt().get("rawResult");
        return rawResult == null ? null : String.valueOf(rawResult);
    }

    private Map<String, Object> compactArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> compact = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            compact.put(entry.getKey(), compactArgumentValue(entry.getValue()));
        }
        return compact;
    }

    private Object compactArgumentValue(Object value) {
        if (value instanceof String text) {
            if (text.length() <= TOOL_ARGUMENT_VALUE_LIMIT) {
                return text;
            }
            return "[string " + text.length() + " chars]";
        }
        return value;
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        if (maxChars <= 12) {
            return value.substring(0, maxChars);
        }
        return value.substring(0, maxChars - 12) + "... (" + value.length() + " chars)";
    }

    @SuppressWarnings("unchecked")
    private String validateSchema(Map<String, Object> inputSchema, Map<String, Object> arguments) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return null;
        }
        Object required = inputSchema.get("required");
        if (!(required instanceof Collection<?> requiredFields)) {
            return null;
        }
        for (Object field : requiredFields) {
            String name = String.valueOf(field);
            if (arguments == null || !arguments.containsKey(name) || arguments.get(name) == null) {
                return "Required tool argument is missing: " + name;
            }
        }
        Object properties = inputSchema.get("properties");
        if (properties instanceof Map<?, ?> rawProperties && arguments != null) {
            Map<String, Object> typedProperties = (Map<String, Object>) rawProperties;
            for (String name : typedProperties.keySet()) {
                Object value = arguments.get(name);
                if (value == null) {
                    continue;
                }
                Object propertySpec = typedProperties.get(name);
                if (propertySpec instanceof Map<?, ?> spec) {
                    String type = spec.get("type") == null ? null : String.valueOf(spec.get("type"));
                    if ("string".equals(type) && !(value instanceof String)) {
                        return "Tool argument must be a string: " + name;
                    }
                }
            }
        }
        return null;
    }

    private String validateRequest(ToolInvocationRequestVO request) {
        if (request == null) {
            return "TOOL_INVALID_INTENT";
        }
        if (isBlank(request.getToolCallId()) || isBlank(request.getToolInvocationId())) {
            return "TOOL_INVALID_INTENT";
        }
        if (request.getMcpTool() == null || isBlank(request.getMcpTool().getMcpServerCode()) || isBlank(request.getMcpTool().getToolName())) {
            return "TOOL_NOT_FOUND";
        }
        if (mcpToolInvokerPort == null) {
            return "TOOL_INVOKER_UNAVAILABLE";
        }
        return null;
    }

    private void markFailure(ToolInvocationRequestVO request, ToolCallStatusEnumVO status, String failureCode) {
        if (request != null && !isBlank(request.getToolCallId())) {
            toolRepository.saveToolReceipt(request.getToolCallId(), request.getArgumentsRef(), null, status, failureCode);
        }
    }

    private ToolInvocationResultVO failure(ToolInvocationRequestVO request, ToolInvocationStatusEnumVO status, String failureCode, String failureMessage) {
        return ToolInvocationResultVO.builder()
                .status(status)
                .toolCallId(request == null ? null : request.getToolCallId())
                .toolInvocationId(request == null ? null : request.getToolInvocationId())
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

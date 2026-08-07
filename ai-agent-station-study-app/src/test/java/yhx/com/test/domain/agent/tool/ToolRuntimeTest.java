package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeCommandVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationRequestVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;
import yhx.com.domain.agent.service.tool.ToolFailureMapper;
import yhx.com.domain.agent.service.tool.ToolReceiptCapture;
import yhx.com.domain.agent.service.tool.ToolRuntime;
import yhx.com.domain.agent.service.tool.port.McpToolInvokerPort;

import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ToolRuntimeTest {

    @Test
    public void success_requires_real_receipt() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder()
                .called(true)
                .success(true)
                .receipt(Map.of("url", "https://example.com/post/1"))
                .latencyMs(10L)
                .build());

        ToolInvocationResultVO result = runtime.invoke(request(false, null, Map.of()));

        Assert.assertEquals(ToolInvocationStatusEnumVO.SUCCESS, result.getStatus());
        Assert.assertNotNull(result.getReceiptRef());
        Assert.assertEquals(ToolCallStatusEnumVO.SUCCEEDED, repository.toolCalls.get("tool-call-001").getStatus());
    }

    @Test
    public void result_summary_describes_invocation_without_copying_result_prefix() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder()
                .called(true)
                .success(true)
                .receipt(Map.of("contentText", "No matches found"))
                .latencyMs(10L)
                .build());

        ToolInvocationResultVO result = runtime.invoke(ToolInvocationRequestVO.builder()
                .runId("run-001")
                .toolCallId("tool-call-001")
                .toolInvocationId("tool-invocation-001")
                .mcpTool(McpToolSpecVO.builder().mcpServerCode("file-system").toolName("search_files").inputSchema(Map.of()).build())
                .arguments(Map.of("path", "E:/javaProject/ai-agent-station-study", "pattern", "04_blue_train_ticket.txt"))
                .argumentsRef("payload-args")
                .approvalRequired(false)
                .build());

        Assert.assertTrue(result.getResultSummary().contains("tool=search_files"));
        Assert.assertTrue(result.getResultSummary().contains("04_blue_train_ticket.txt"));
        Assert.assertTrue(result.getResultSummary().contains("resultChars="));
        Assert.assertFalse(result.getResultSummary().contains("No matches found"));
    }

    @Test
    public void result_content_preserves_original_tool_text() {
        ToolTestSupport.Repository repository = repository();
        String originalContent = "package demo;\npublic class Demo {}";
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder()
                .called(true)
                .success(true)
                .receipt(Map.of("contentText", originalContent))
                .latencyMs(10L)
                .build());

        ToolInvocationResultVO result = runtime.invoke(ToolInvocationRequestVO.builder()
                .runId("run-001")
                .toolCallId("tool-call-001")
                .toolInvocationId("tool-invocation-001")
                .mcpTool(McpToolSpecVO.builder().mcpServerCode("file-system").toolName("read_file").inputSchema(Map.of()).build())
                .arguments(Map.of("path", "E:/demo/Demo.java"))
                .argumentsRef("payload-args")
                .approvalRequired(false)
                .build());

        Assert.assertEquals(originalContent, result.getResultContent());
        Assert.assertEquals(Integer.valueOf(originalContent.length()), result.getResultTotalChars());
        Assert.assertEquals("TEXT", result.getResultContentFormat());
    }

    @Test
    public void result_summary_compacts_large_string_arguments_and_long_receipts() {
        ToolTestSupport.Repository repository = repository();
        String longContent = "x".repeat(2000);
        String longReceipt = "created file with payload " + "y".repeat(1000);
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder()
                .called(true)
                .success(true)
                .receipt(Map.of("contentText", longReceipt))
                .latencyMs(10L)
                .build());

        ToolInvocationResultVO result = runtime.invoke(ToolInvocationRequestVO.builder()
                .runId("run-001")
                .toolCallId("tool-call-001")
                .toolInvocationId("tool-invocation-001")
                .mcpTool(McpToolSpecVO.builder().mcpServerCode("file-system").toolName("write_file").inputSchema(Map.of()).build())
                .arguments(Map.of("path", "E:/tmp/story.md", "content", longContent))
                .argumentsRef("payload-args")
                .approvalRequired(false)
                .build());

        Assert.assertTrue(result.getResultSummary().contains("tool=write_file"));
        Assert.assertTrue(result.getResultSummary().contains("path=E:/tmp/story.md"));
        Assert.assertTrue(result.getResultSummary().contains("content=[string 2000 chars]"));
        Assert.assertFalse(result.getResultSummary().contains(longContent));
        Assert.assertTrue(result.getResultSummary().length() < 512);
    }

    @Test
    public void missing_approval_returns_needs_user_action() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder().called(true).success(true).build());

        ToolInvocationResultVO result = runtime.invoke(request(true, null, Map.of()));

        Assert.assertEquals(ToolInvocationStatusEnumVO.NEEDS_USER_ACTION, result.getStatus());
        Assert.assertEquals(ToolCallStatusEnumVO.APPROVAL_PENDING, repository.toolCalls.get("tool-call-001").getStatus());
    }

    @Test
    public void schema_validation_failure_returns_invalid_intent() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder().called(true).success(true).build());

        ToolInvocationResultVO result = runtime.invoke(request(false, null, Map.of("required", java.util.List.of("title"))));

        Assert.assertEquals(ToolInvocationStatusEnumVO.INVALID_INTENT, result.getStatus());
        Assert.assertEquals("TOOL_SCHEMA_ERROR", result.getFailureCode());
        Assert.assertNotNull(result.getSchemaHash());
        Assert.assertFalse(result.getSchemaViolations().isEmpty());
    }

    @Test
    public void valid_nested_arguments_reach_mcp_invoker() {
        ToolTestSupport.Repository repository = repository();
        AtomicInteger calls = new AtomicInteger();
        ToolRuntime runtime = runtime(repository, command -> {
            calls.incrementAndGet();
            return McpToolInvokeResultVO.builder().called(true).success(true)
                    .receipt(Map.of("contentText", "invoice-created")).build();
        });

        ToolInvocationResultVO result = runtime.invoke(invoiceRequest(validInvoiceArguments(), false));

        Assert.assertEquals(ToolInvocationStatusEnumVO.SUCCESS, result.getStatus());
        Assert.assertEquals(1, calls.get());
        Assert.assertNotNull(result.getSchemaHash());
    }

    @Test
    public void nested_schema_errors_are_rejected_before_approval_or_mcp_invocation() {
        List<Map<String, Object>> invalidArguments = List.of(
                Map.of("customer", Map.of("name", "Alice"),
                        "items", List.of(Map.of("name", "Book", "quantity", 1, "price", 10)), "currency", "CNY"),
                Map.of("customer", Map.of("name", "Alice", "taxId", "T-1"),
                        "items", List.of(Map.of("name", "Book", "quantity", "one", "price", 10)), "currency", "CNY"),
                Map.of("customer", Map.of("name", "Alice", "taxId", "T-1"),
                        "items", List.of(Map.of("name", "Book", "quantity", 1, "price", 10)), "currency", "EUR"),
                Map.of("customer", Map.of("name", "Alice", "taxId", "T-1"),
                        "items", List.of(Map.of("name", "Book", "quantity", 1, "price", 10)), "currency", "CNY", "extra", true)
        );

        for (Map<String, Object> arguments : invalidArguments) {
            ToolTestSupport.Repository repository = repository();
            AtomicInteger calls = new AtomicInteger();
            ToolRuntime runtime = runtime(repository, command -> {
                calls.incrementAndGet();
                return McpToolInvokeResultVO.builder().called(true).success(true).build();
            });

            ToolInvocationResultVO result = runtime.invoke(invoiceRequest(arguments, true));

            Assert.assertEquals(ToolInvocationStatusEnumVO.INVALID_INTENT, result.getStatus());
            Assert.assertEquals("TOOL_SCHEMA_ERROR", result.getFailureCode());
            Assert.assertEquals(0, calls.get());
            Assert.assertNotNull(result.getSchemaHash());
            Assert.assertFalse(result.getSchemaViolations().isEmpty());
            Assert.assertTrue(result.getFailureMessage().contains("schemaHash="));
        }
    }

    @Test
    public void schema_validation_diagnostics_do_not_expose_argument_values() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> {
            throw new AssertionError("Invalid arguments must not reach MCP invocation.");
        });
        String sensitiveValue = "customer-secret-token-9274";
        Map<String, Object> arguments = validInvoiceArguments();
        arguments = new java.util.LinkedHashMap<>(arguments);
        arguments.put("currency", sensitiveValue);

        ToolInvocationResultVO result = runtime.invoke(invoiceRequest(arguments, false));

        Assert.assertEquals(ToolInvocationStatusEnumVO.INVALID_INTENT, result.getStatus());
        Assert.assertFalse(result.getFailureMessage().contains(sensitiveValue));
        Assert.assertFalse(result.getSchemaViolations().toString().contains(sensitiveValue));
        Assert.assertTrue(result.getSchemaViolations().get(0).getMessage().startsWith("Schema validation failed"));
    }

    @Test
    public void schema_validation_identifies_missing_and_additional_property_paths() {
        ToolTestSupport.Repository missingRepository = repository();
        ToolInvocationResultVO missing = runtime(missingRepository, command -> {
            throw new AssertionError("Invalid arguments must not reach MCP invocation.");
        }).invoke(invoiceRequest(Map.of(
                "customer", Map.of("name", "Alice"),
                "items", List.of(Map.of("name", "Book", "quantity", 1, "price", 10)),
                "currency", "CNY"), false));

        Assert.assertTrue(missing.getSchemaViolations().stream()
                .anyMatch(item -> item.getPath().contains("taxId") && "MISSING".equals(item.getActualType())));

        ToolTestSupport.Repository extraRepository = repository();
        ToolInvocationResultVO extra = runtime(extraRepository, command -> {
            throw new AssertionError("Invalid arguments must not reach MCP invocation.");
        }).invoke(invoiceRequest(Map.of(
                "customer", Map.of("name", "Alice", "taxId", "T-1"),
                "items", List.of(Map.of("name", "Book", "quantity", 1, "price", 10)),
                "currency", "CNY",
                "unexpectedField", true), false));

        Assert.assertTrue(extra.getSchemaViolations().stream()
                .anyMatch(item -> item.getPath().contains("unexpectedField")));
    }

    @Test
    public void mcp_error_persists_failed_receipt() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder()
                .called(true)
                .success(false)
                .errorCode("MCP_TIMEOUT")
                .errorMessage("timeout")
                .build());

        ToolInvocationResultVO result = runtime.invoke(request(false, null, Map.of()));

        Assert.assertEquals(ToolInvocationStatusEnumVO.FAILED, result.getStatus());
        Assert.assertNotNull(result.getReceiptRef());
        Assert.assertEquals(ToolCallStatusEnumVO.FAILED, repository.toolCalls.get("tool-call-001").getStatus());
    }

    private ToolRuntime runtime(ToolTestSupport.Repository repository, McpToolInvokerPort invoker) {
        return new ToolRuntime(invoker, new ToolReceiptCapture(repository), new ToolFailureMapper(), repository);
    }

    private ToolInvocationRequestVO request(boolean approvalRequired, String approvalId, Map<String, Object> schema) {
        return ToolInvocationRequestVO.builder()
                .runId("run-001")
                .toolCallId("tool-call-001")
                .toolInvocationId("tool-invocation-001")
                .mcpTool(McpToolSpecVO.builder().mcpServerCode("server").toolName("tool").inputSchema(schema).build())
                .arguments(Map.of())
                .argumentsRef("payload-args")
                .approvalRequired(approvalRequired)
                .approvalId(approvalId)
                .build();
    }

    private ToolInvocationRequestVO invoiceRequest(Map<String, Object> arguments, boolean approvalRequired) {
        return ToolInvocationRequestVO.builder()
                .runId("run-001")
                .toolCallId("tool-call-001")
                .toolInvocationId("tool-invocation-001")
                .mcpTool(McpToolSpecVO.builder()
                        .mcpServerCode("invoice-server")
                        .toolName("generate_invoice")
                        .inputSchema(invoiceSchema())
                        .build())
                .arguments(arguments)
                .argumentsRef("payload-args")
                .approvalRequired(approvalRequired)
                .build();
    }

    private Map<String, Object> validInvoiceArguments() {
        return Map.of(
                "customer", Map.of("name", "Alice", "taxId", "T-1"),
                "items", List.of(Map.of("name", "Book", "quantity", 1, "price", 10)),
                "currency", "CNY");
    }

    private Map<String, Object> invoiceSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "customer", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "taxId", Map.of("type", "string")),
                                "required", List.of("name", "taxId")),
                        "items", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "properties", Map.of(
                                                "name", Map.of("type", "string"),
                                                "quantity", Map.of("type", "number"),
                                                "price", Map.of("type", "number")),
                                        "required", List.of("name", "quantity", "price"))),
                        "currency", Map.of("type", "string", "enum", List.of("CNY", "USD"))),
                "required", List.of("customer", "items", "currency"));
    }

    private ToolTestSupport.Repository repository() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        repository.createToolCall(ToolCallEntity.builder()
                .toolCallId("tool-call-001")
                .toolInvocationId("tool-invocation-001")
                .runId("run-001")
                .status(ToolCallStatusEnumVO.CREATED)
                .build());
        return repository;
    }
}

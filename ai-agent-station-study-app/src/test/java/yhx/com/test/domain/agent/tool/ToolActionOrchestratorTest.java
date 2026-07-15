package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionEffectStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ApprovalPolicyEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.McpTransportTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.RequiredPermissionEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionResultVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.service.tool.CapabilityRegistry;
import yhx.com.domain.agent.service.tool.McpToolRegistry;
import yhx.com.domain.agent.service.tool.PermissionEnforcer;
import yhx.com.domain.agent.service.tool.ToolActionOrchestrator;
import yhx.com.domain.agent.service.tool.ToolApprovalKeyGenerator;
import yhx.com.domain.agent.service.tool.ToolApprovalService;
import yhx.com.domain.agent.service.tool.ToolArgumentMaterializer;
import yhx.com.domain.agent.service.tool.ToolEvidenceConverter;
import yhx.com.domain.agent.service.tool.ToolFailureMapper;
import yhx.com.domain.agent.service.tool.ToolInvocationRequestBuilder;
import yhx.com.domain.agent.service.tool.ToolReceiptCapture;
import yhx.com.domain.agent.service.tool.ToolRuntime;
import yhx.com.domain.agent.service.tool.ToolTranscriptRecorder;
import yhx.com.domain.agent.service.tool.ToolVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ToolActionOrchestratorTest {

    @Test
    public void approval_required_returns_waiting_user() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolActionOrchestrator orchestrator = orchestrator(repository, PermissionModeEnumVO.ASK_USER, command -> {
            throw new AssertionError("tool must not be invoked before approval");
        });

        ToolActionResultVO result = orchestrator.handleToolAction(command());

        Assert.assertEquals(ToolActionStatusEnumVO.WAITING_USER, result.getStatus());
        Assert.assertEquals("pending-tool-1", result.getPendingInputId());
    }

    @Test
    public void approved_tool_invokes_tool_runtime() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        AtomicInteger calls = new AtomicInteger();
        ToolActionOrchestrator orchestrator = orchestrator(repository, PermissionModeEnumVO.ALLOW, command -> {
            calls.incrementAndGet();
            return McpToolInvokeResultVO.builder().called(true).success(true).receipt(Map.of("id", "ok")).build();
        });

        ToolActionResultVO result = orchestrator.handleToolAction(command());

        Assert.assertEquals(ToolActionStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(1, calls.get());
    }

    @Test
    public void approved_tool_can_infer_capability_from_unique_tool_name() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        AtomicInteger calls = new AtomicInteger();
        ToolActionOrchestrator orchestrator = orchestrator(repository, PermissionModeEnumVO.ALLOW, command -> {
            calls.incrementAndGet();
            return McpToolInvokeResultVO.builder().called(true).success(true).receipt(Map.of("id", "ok")).build();
        });

        ToolActionResultVO result = orchestrator.handleToolAction(commandWithoutCapabilityCode());

        Assert.assertEquals(ToolActionStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(1, calls.get());
    }

    @Test
    public void permission_denial_creates_denial_evidence_without_invocation() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolActionOrchestrator orchestrator = orchestrator(repository, PermissionModeEnumVO.DENY, command -> {
            throw new AssertionError("denied tool must not be invoked");
        });

        ToolActionResultVO result = orchestrator.handleToolAction(command());

        Assert.assertEquals(ToolActionStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(1, repository.evidence.size());
    }

    @Test
    public void successful_tool_creates_evidence_and_continues_loop() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolActionOrchestrator orchestrator = orchestrator(repository, PermissionModeEnumVO.ALLOW,
                command -> McpToolInvokeResultVO.builder()
                        .called(true)
                        .success(true)
                        .receipt(Map.of("contentText", "project structure: ai-agent-station-study-domain"))
                        .build());

        ToolActionResultVO result = orchestrator.handleToolAction(command());

        Assert.assertEquals(ToolActionStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertFalse(result.getEvidenceIds().isEmpty());
        Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED, result.getActionEffectStatus());
        Assert.assertTrue(repository.evidence.get(0).getSummary().contains("ai-agent-station-study-domain"));
        Assert.assertEquals("project structure: ai-agent-station-study-domain", result.getEvidence().get(0).getContent());
        Assert.assertEquals(2, repository.transcripts.size());
    }

    @Test
    public void successful_invocation_without_persisted_receipt_is_gated_as_verification_failure() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository() {
            @Override
            public String savePayload(AgentPayloadEntity payload) {
                if (payload.getPayloadType() == PayloadTypeEnumVO.TOOL_RECEIPT) {
                    return null;
                }
                return super.savePayload(payload);
            }
        };
        ToolActionOrchestrator orchestrator = orchestrator(repository, PermissionModeEnumVO.ALLOW,
                command -> McpToolInvokeResultVO.builder()
                        .called(true)
                        .success(true)
                        .receipt(Map.of("contentText", "UNVERIFIED_PRIVATE_RESULT"))
                        .build());

        ToolActionResultVO result = orchestrator.handleToolAction(command());

        Assert.assertEquals(ToolActionStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_FAILED, result.getActionEffectStatus());
        Assert.assertTrue(result.getMessage().contains("receipt"));
        Assert.assertEquals(1, result.getEvidence().size());
        Assert.assertTrue(result.getEvidence().get(0).getContent().contains("TOOL_RECEIPT_MISSING"));
        Assert.assertFalse(result.getEvidence().get(0).getContent().contains("UNVERIFIED_PRIVATE_RESULT"));
        Assert.assertFalse(repository.evidence.get(0).getSummary().startsWith("Tool action succeeded"));
    }

    @Test
    public void missing_verification_result_is_gated_as_tool_failed() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolVerifier missingVerifier = new ToolVerifier(repository, repository) {
            @Override
            public VerificationResultVO verify(yhx.com.domain.agent.model.valobj.tool.ToolInvocationRequestVO request,
                                               yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO invocationResult) {
                return null;
            }
        };
        ToolActionOrchestrator orchestrator = orchestrator(repository, PermissionModeEnumVO.ALLOW,
                command -> McpToolInvokeResultVO.builder()
                        .called(true)
                        .success(true)
                        .receipt(Map.of("contentText", "UNVERIFIED_RESULT"))
                        .build(), missingVerifier);

        ToolActionResultVO result = orchestrator.handleToolAction(command());

        Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_FAILED, result.getActionEffectStatus());
        Assert.assertTrue(result.getEvidence().get(0).getContent().contains("TOOL_VERIFICATION_MISSING"));
        Assert.assertFalse(result.getEvidence().get(0).getContent().contains("UNVERIFIED_RESULT"));
    }

    @Test
    public void invocation_failure_remains_tool_failed_without_exposing_receipt_content() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolActionOrchestrator orchestrator = orchestrator(repository, PermissionModeEnumVO.ALLOW,
                command -> McpToolInvokeResultVO.builder()
                        .called(true)
                        .success(false)
                        .errorCode("MCP_TIMEOUT")
                        .errorMessage("timed out")
                        .receipt(Map.of("contentText", "UNTRUSTED_PARTIAL_RESULT"))
                        .build());

        ToolActionResultVO result = orchestrator.handleToolAction(command());

        Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_FAILED, result.getActionEffectStatus());
        Assert.assertTrue(result.getEvidence().get(0).getContent().contains("MCP_TIMEOUT"));
        Assert.assertFalse(result.getEvidence().get(0).getContent().contains("UNTRUSTED_PARTIAL_RESULT"));
    }

    @Test
    public void capability_code_uses_canonical_mcp_tool_name_over_llm_tool_name() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        AtomicInteger calls = new AtomicInteger();
        ToolActionOrchestrator orchestrator = fileWriteOrchestrator(repository, command -> {
            calls.incrementAndGet();
            Assert.assertEquals("write_file", command.getToolName());
            return McpToolInvokeResultVO.builder().called(true).success(true).receipt(Map.of("contentText", "created")).build();
        });

        ToolActionResultVO result = orchestrator.handleToolAction(ToolActionCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .loopIndex(1)
                .capabilityCode("file_system_create_file")
                .toolName("create_file")
                .goal("create a new file")
                .arguments(Map.of("path", "E:/javaProject/ai-agent-station-study/tmp.txt", "content", "hello"))
                .build());

        Assert.assertEquals(ToolActionStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(1, calls.get());
    }

    @Test
    public void write_file_evidence_summary_is_bounded_for_database_column() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        String longContent = "x".repeat(4000);
        ToolActionOrchestrator orchestrator = fileWriteOrchestrator(repository,
                command -> McpToolInvokeResultVO.builder()
                        .called(true)
                        .success(true)
                        .receipt(Map.of("contentText", "Successfully wrote " + "y".repeat(1000)))
                        .build());

        ToolActionResultVO result = orchestrator.handleToolAction(ToolActionCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .loopIndex(1)
                .capabilityCode("file_system_create_file")
                .toolName("create_file")
                .goal("create a new file")
                .arguments(Map.of("path", "E:/javaProject/ai-agent-station-study/tmp.txt", "content", longContent))
                .build());

        Assert.assertEquals(ToolActionStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(1, repository.evidence.size());
        String summary = repository.evidence.get(0).getSummary();
        Assert.assertTrue(summary.length() <= 500);
        Assert.assertFalse(summary.contains(longContent));
        Assert.assertTrue(summary.contains("content=[string 4000 chars]"));
    }

    private ToolActionOrchestrator orchestrator(ToolTestSupport.Repository repository,
                                                PermissionModeEnumVO permissionMode,
                                                yhx.com.domain.agent.service.tool.port.McpToolInvokerPort invoker) {
        return orchestrator(repository, permissionMode, invoker, new ToolVerifier(repository, repository));
    }

    private ToolActionOrchestrator orchestrator(ToolTestSupport.Repository repository,
                                                PermissionModeEnumVO permissionMode,
                                                yhx.com.domain.agent.service.tool.port.McpToolInvokerPort invoker,
                                                ToolVerifier verifier) {
        CapabilityRegistry capabilityRegistry = new CapabilityRegistry(List.of(CapabilitySpecVO.builder()
                .capabilityCode("publish")
                .mcpServerCode("server")
                .toolName("tool")
                .permissionMode(permissionMode)
                .approvalPolicy(ApprovalPolicyEnumVO.NEVER)
                .enabled(true)
                .build()));
        McpToolRegistry mcpToolRegistry = new McpToolRegistry(List.of(McpToolSpecVO.builder()
                .mcpServerCode("server")
                .toolName("tool")
                .transportType(McpTransportTypeEnumVO.UNKNOWN)
                .inputSchema(Map.of())
                .build()));
        ToolArgumentMaterializer materializer = new ToolArgumentMaterializer(repository, repository, repository);
        ToolApprovalService approvalService = new ToolApprovalService(repository, repository, new ToolTestSupport.FakeUserInteractionManager());
        ToolInvocationRequestBuilder requestBuilder = new ToolInvocationRequestBuilder(capabilityRegistry, mcpToolRegistry,
                materializer, new PermissionEnforcer(), approvalService, new ToolApprovalKeyGenerator(), repository, repository);
        ToolRuntime runtime = new ToolRuntime(invoker, new ToolReceiptCapture(repository), new ToolFailureMapper(), repository);
        return new ToolActionOrchestrator(requestBuilder, runtime, verifier,
                new ToolEvidenceConverter(repository), new ToolTranscriptRecorder(repository, repository));
    }

    private ToolActionOrchestrator fileWriteOrchestrator(ToolTestSupport.Repository repository,
                                                         yhx.com.domain.agent.service.tool.port.McpToolInvokerPort invoker) {
        CapabilityRegistry capabilityRegistry = new CapabilityRegistry(List.of(CapabilitySpecVO.builder()
                .capabilityCode("file_system_create_file")
                .mcpServerCode("file-system")
                .toolName("write_file")
                .requiredPermission(RequiredPermissionEnumVO.WORKSPACE_WRITE)
                .permissionMode(PermissionModeEnumVO.ALLOW)
                .approvalPolicy(ApprovalPolicyEnumVO.NEVER)
                .riskLevel("HIGH")
                .enabled(true)
                .build()));
        McpToolRegistry mcpToolRegistry = new McpToolRegistry(List.of(McpToolSpecVO.builder()
                .mcpServerCode("file-system")
                .toolName("write_file")
                .transportType(McpTransportTypeEnumVO.UNKNOWN)
                .inputSchema(Map.of())
                .build()));
        ToolArgumentMaterializer materializer = new ToolArgumentMaterializer(repository, repository, repository);
        ToolApprovalService approvalService = new ToolApprovalService(repository, repository, new ToolTestSupport.FakeUserInteractionManager());
        ToolInvocationRequestBuilder requestBuilder = new ToolInvocationRequestBuilder(capabilityRegistry, mcpToolRegistry,
                materializer, new PermissionEnforcer(), approvalService, new ToolApprovalKeyGenerator(), repository, repository);
        ToolRuntime runtime = new ToolRuntime(invoker, new ToolReceiptCapture(repository), new ToolFailureMapper(), repository);
        return new ToolActionOrchestrator(requestBuilder, runtime, new ToolVerifier(repository, repository),
                new ToolEvidenceConverter(repository), new ToolTranscriptRecorder(repository, repository));
    }

    private ToolActionCommandVO command() {
        return ToolActionCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .loopIndex(1)
                .capabilityCode("publish")
                .toolName("tool")
                .goal("publish content")
                .arguments(Map.of("title", "Hello"))
                .build();
    }

    private ToolActionCommandVO commandWithoutCapabilityCode() {
        return ToolActionCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .loopIndex(1)
                .toolName("tool")
                .goal("publish content")
                .arguments(Map.of("title", "Hello"))
                .build();
    }
}

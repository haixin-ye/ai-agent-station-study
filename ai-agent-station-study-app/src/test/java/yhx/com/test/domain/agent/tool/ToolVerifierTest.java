package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationRequestVO;
import yhx.com.domain.agent.service.tool.ToolVerifier;

public class ToolVerifierTest {

    @Test
    public void no_tool_call_fails_tool_not_called() {
        ToolVerifier verifier = new ToolVerifier(new ToolTestSupport.Repository(), new ToolTestSupport.Repository());

        VerificationResultVO result = verifier.verify(ToolInvocationRequestVO.builder().toolCallId("missing").build(), null);

        Assert.assertEquals("FAILED", result.getStatus());
        Assert.assertEquals("TOOL_NOT_CALLED", result.getFailureCode());
    }

    @Test
    public void success_without_receipt_fails_receipt_missing() {
        ToolTestSupport.Repository repository = repository(ToolCallStatusEnumVO.SUCCEEDED, null);
        ToolVerifier verifier = new ToolVerifier(repository, repository);

        VerificationResultVO result = verifier.verify(request(), null);

        Assert.assertEquals("TOOL_RECEIPT_MISSING", result.getFailureCode());
    }

    @Test
    public void real_receipt_passes_execution_proof() {
        ToolTestSupport.Repository repository = repository(ToolCallStatusEnumVO.SUCCEEDED, "payload-receipt");
        ToolVerifier verifier = new ToolVerifier(repository, repository);

        VerificationResultVO result = verifier.verify(request(), null);

        Assert.assertEquals("PASSED", result.getStatus());
    }

    @Test
    public void business_completion_is_not_checked() {
        ToolTestSupport.Repository repository = repository(ToolCallStatusEnumVO.SUCCEEDED, "payload-receipt");
        ToolVerifier verifier = new ToolVerifier(repository, repository);

        VerificationResultVO result = verifier.verify(request(), null);

        Assert.assertEquals("PASSED", result.getStatus());
        Assert.assertEquals(1, repository.verifications.size());
    }

    private ToolInvocationRequestVO request() {
        return ToolInvocationRequestVO.builder()
                .toolCallId("tool-call-001")
                .approvalRequired(false)
                .build();
    }

    private ToolTestSupport.Repository repository(ToolCallStatusEnumVO status, String receiptRef) {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        repository.createToolCall(ToolCallEntity.builder()
                .toolCallId("tool-call-001")
                .toolInvocationId("tool-invocation-001")
                .runId("run-001")
                .status(status)
                .receiptRef(receiptRef)
                .build());
        return repository;
    }
}

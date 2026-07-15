package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolEvidenceCreationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationBuildResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolSchemaViolationVO;
import yhx.com.domain.agent.service.evidence.EvidenceCandidatePreselector;
import yhx.com.domain.agent.service.evidence.EvidencePackBuilder;
import yhx.com.domain.agent.service.tool.ToolEvidenceConverter;

import java.util.List;

public class ToolEvidenceConverterTest {

    @Test
    public void schema_failure_exposes_all_safe_violations_to_working_state_evidence() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolInvocationResultVO result = ToolInvocationResultVO.builder()
                .status(ToolInvocationStatusEnumVO.INVALID_INTENT)
                .toolCallId("tool-call-1")
                .failureCode("TOOL_SCHEMA_ERROR")
                .failureMessage("TOOL_SCHEMA_ERROR path=$/customer/taxId")
                .schemaHash("schema-hash-1")
                .schemaViolations(List.of(
                        violation("$/customer/taxId", "required", "MISSING"),
                        violation("$/currency", "enum", "STRING")))
                .build();

        VerificationResultVO verification = VerificationResultVO.builder()
                .status("FAILED")
                .failureCode("TOOL_SCHEMA_ERROR")
                .detail("Tool arguments failed schema validation.")
                .build();
        ToolEvidenceCreationResultVO evidence = new ToolEvidenceConverter(repository)
                .createVerificationFailureEvidencePack("run-1", result, verification);

        Assert.assertEquals(1, evidence.getEvidence().size());
        Assert.assertEquals("JSON", evidence.getEvidence().get(0).getContentFormat());
        Assert.assertTrue(evidence.getEvidence().get(0).getContent().contains("$/customer/taxId"));
        Assert.assertTrue(evidence.getEvidence().get(0).getContent().contains("$/currency"));
        Assert.assertTrue(evidence.getEvidence().get(0).getContent().contains("schema-hash-1"));
        Assert.assertTrue(repository.evidence.get(0).getSummary().length() <= 500);
    }

    @Test
    public void denial_evidence_bounds_summary_before_persistence_and_state_projection() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolInvocationBuildResultVO result = ToolInvocationBuildResultVO.builder()
                .toolCallId("tool-call-2")
                .failureCode("TOOL_DENIED")
                .failureMessage("x".repeat(1200))
                .build();

        ToolEvidenceCreationResultVO evidence = new ToolEvidenceConverter(repository)
                .createDenialEvidencePack("run-2", result);

        Assert.assertTrue(repository.evidence.get(0).getSummary().length() <= 500);
        Assert.assertTrue(evidence.getEvidence().get(0).getSummary().length() <= 500);
    }

    @Test
    public void verification_failure_preserves_safe_schema_diagnostics_without_unverified_result_content() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolInvocationResultVO invocation = ToolInvocationResultVO.builder()
                .status(ToolInvocationStatusEnumVO.INVALID_INTENT)
                .toolCallId("tool-call-3")
                .failureCode("TOOL_SCHEMA_ERROR")
                .resultContent("UNVERIFIED_TOOL_CONTENT")
                .schemaHash("schema-hash-3")
                .schemaViolations(List.of(violation("$/customer/taxId", "required", "MISSING")))
                .build();
        VerificationResultVO verification = VerificationResultVO.builder()
                .status("FAILED")
                .failureCode("TOOL_SCHEMA_ERROR")
                .detail("Tool runtime reported failure.")
                .build();

        ToolEvidenceCreationResultVO evidence = new ToolEvidenceConverter(repository)
                .createVerificationFailureEvidencePack("run-3", invocation, verification);

        String content = evidence.getEvidence().get(0).getContent();
        Assert.assertTrue(content.contains("schema-hash-3"));
        Assert.assertTrue(content.contains("$/customer/taxId"));
        Assert.assertFalse(content.contains("UNVERIFIED_TOOL_CONTENT"));
        Assert.assertNull(evidence.getEvidence().get(0).getContentRef());
    }

    @Test
    public void verification_failure_uses_failed_code_when_verification_exists_without_failure_code() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolInvocationResultVO invocation = ToolInvocationResultVO.builder()
                .status(ToolInvocationStatusEnumVO.SUCCESS)
                .toolCallId("tool-call-4")
                .build();
        VerificationResultVO verification = VerificationResultVO.builder()
                .status("FAILED")
                .detail("Verification rejected the execution proof.")
                .build();

        ToolEvidenceCreationResultVO evidence = new ToolEvidenceConverter(repository)
                .createVerificationFailureEvidencePack("run-4", invocation, verification);

        Assert.assertTrue(evidence.getEvidence().get(0).getContent().contains("TOOL_VERIFICATION_FAILED"));
        Assert.assertFalse(evidence.getEvidence().get(0).getContent().contains("TOOL_VERIFICATION_MISSING"));
    }

    @Test
    public void verification_failure_persists_safe_diagnostics_for_context_recovery() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        ToolInvocationResultVO invocation = ToolInvocationResultVO.builder()
                .status(ToolInvocationStatusEnumVO.SUCCESS)
                .toolCallId("tool-call-5")
                .resultContent("UNVERIFIED_TOOL_CONTENT")
                .build();
        VerificationResultVO verification = VerificationResultVO.builder()
                .status("FAILED")
                .failureCode("TOOL_RECEIPT_MISSING")
                .detail("Receipt is missing.")
                .build();

        ToolEvidenceCreationResultVO evidence = new ToolEvidenceConverter(repository, repository)
                .createVerificationFailureEvidencePack("run-5", invocation, verification);

        String contentRef = evidence.getEvidence().get(0).getContentRef();
        Assert.assertNotNull(contentRef);
        Assert.assertTrue(repository.findContent(contentRef).orElseThrow().contains("TOOL_RECEIPT_MISSING"));
        Assert.assertFalse(repository.findContent(contentRef).orElseThrow().contains("UNVERIFIED_TOOL_CONTENT"));

        var recovered = new EvidencePackBuilder().buildFromCandidates(
                new EvidenceCandidatePreselector(repository).select("receipt", repository.evidence, 10));
        Assert.assertEquals("TOOL_RECEIPT_MISSING", recovered.get(0).getMetadata().get("failureCode"));
        Assert.assertEquals("FAILED", recovered.get(0).getMetadata().get("verificationStatus"));
        Assert.assertTrue(recovered.get(0).getContent().contains("Receipt is missing."));
    }

    private ToolSchemaViolationVO violation(String path, String keyword, String actualType) {
        return ToolSchemaViolationVO.builder()
                .path(path)
                .keyword(keyword)
                .expectedConstraint("expected")
                .actualType(actualType)
                .message("Schema validation failed.")
                .build();
    }
}

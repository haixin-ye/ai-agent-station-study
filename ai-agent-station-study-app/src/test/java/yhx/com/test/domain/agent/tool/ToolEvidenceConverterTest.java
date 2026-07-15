package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.tool.ToolEvidenceCreationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationBuildResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolSchemaViolationVO;
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

        ToolEvidenceCreationResultVO evidence = new ToolEvidenceConverter(repository)
                .createInvocationEvidencePack("run-1", result);

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

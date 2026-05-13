package yhx.com.test.domain.agent.contract;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.contract.AgentNodeContract;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.enums.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.ContextLevelEnumVO;
import yhx.com.domain.agent.model.valobj.enums.ContextPlannerStatusEnumVO;
import yhx.com.domain.agent.service.contract.ContractRegistry;
import yhx.com.domain.agent.service.contract.ContractValidator;

public class ContextPlannerContractTest {

    @Test
    public void test_contextEnums_canResolveByCode() {
        Assert.assertEquals(ContextPlannerStatusEnumVO.READY, ContextPlannerStatusEnumVO.ofCode("READY").orElse(null));
        Assert.assertEquals(ContextLevelEnumVO.FULL_TEXT, ContextLevelEnumVO.ofCode("FULL_TEXT").orElse(null));
        Assert.assertFalse(ContextPlannerStatusEnumVO.ofCode("DONE").isPresent());
        Assert.assertFalse(ContextLevelEnumVO.ofCode("ALL_TEXT").isPresent());
    }

    @Test
    public void test_contractRegistry_hasContextPlannerContract() {
        AgentNodeContract contract = ContractRegistry.defaultRegistry()
                .getRequired(AgentComponentCodeEnumVO.CONTEXT_PLANNER);

        Assert.assertEquals("ContextPlannerOutputContract", contract.getName());
        Assert.assertEquals("context-planner-output-v1", contract.getVersion());
    }

    @Test
    public void test_contractValidator_acceptsReadyContextPlan() {
        String raw = "{"
                + "\"status\":\"READY\","
                + "\"selectedMessages\":[{\"messageId\":\"msg_001\",\"useLevel\":\"SUMMARY_ONLY\"}],"
                + "\"selectedArtifacts\":[{\"artifactId\":\"art_001\",\"useLevel\":\"METADATA_ONLY\"}]"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateContextPlannerOutput(raw);

        Assert.assertTrue(result.isPassed());
        Assert.assertTrue(result.getViolations().isEmpty());
    }

    @Test
    public void test_contractValidator_rejectsInvalidContextLevel() {
        String raw = "{"
                + "\"status\":\"READY\","
                + "\"selectedArtifacts\":[{\"artifactId\":\"art_001\",\"useLevel\":\"ALL_TEXT\"}]"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateContextPlannerOutput(raw);

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("INVALID_CONTEXT_LEVEL", result.getViolations().get(0).getCode());
    }
}

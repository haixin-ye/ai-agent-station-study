package yhx.com.test.domain.agent.contract;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.contract.AgentNodeContract;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextPlannerStatusEnumVO;
import yhx.com.domain.agent.service.prompt.ContextPlannerPromptBuilder;
import yhx.com.domain.agent.service.prompt.OutputContractPromptRenderer;
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
                + "\"selectedContext\":[{\"sourceType\":\"ARTIFACT\",\"sourceId\":\"art_001\","
                + "\"useLevel\":\"METADATA_ONLY\",\"reason\":\"Requested artifact.\"}]"
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
                + "\"selectedContext\":[{\"sourceType\":\"ARTIFACT\",\"sourceId\":\"art_001\","
                + "\"useLevel\":\"ALL_TEXT\",\"reason\":\"Requested artifact.\"}]"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateContextPlannerOutput(raw);

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("INVALID_CONTEXT_LEVEL", result.getViolations().get(0).getCode());
    }

    @Test
    public void test_contextPlannerPrompt_usesSourceIdContractAndRagRules() {
        String componentPrompt = new ContextPlannerPromptBuilder().build().stream()
                .map(layer -> layer.getContent())
                .reduce("", (left, right) -> left + "\n" + right);
        String outputContract = new OutputContractPromptRenderer().renderContextPlannerOutputContract();
        String prompt = componentPrompt + "\n" + outputContract;

        Assert.assertTrue(prompt.contains("sourceId must be summaryId"));
        Assert.assertTrue(prompt.contains("sourceId=memoryId"));
        Assert.assertTrue(prompt.contains("sourceId=candidateId or chunkId"));
        Assert.assertTrue(prompt.contains("SUMMARY_ONLY"));
        Assert.assertTrue(prompt.contains("RAG_FILE_CHUNK"));
        Assert.assertTrue(prompt.contains("RAG_CODE_FILE_SUMMARY"));
        Assert.assertTrue(prompt.contains("RAG_CODE_CHUNK"));
        Assert.assertFalse(prompt.contains("\"summaryId\":\"turn-summary-1\""));
        Assert.assertFalse(prompt.contains("\"contentRef\""));
        Assert.assertFalse(prompt.contains("budgetIssue"));
    }
}


package yhx.com.test.domain.agent.contract;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.contract.AgentNodeContract;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.contract.RawOutputParseResult;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.service.contract.ContractRegistry;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.contract.RawOutputParser;
import yhx.com.domain.agent.service.contract.StateDeltaScopeRules;

public class MainAgentActionContractTest {

    @Test
    public void test_mainAgentActionEnum_canResolveByCode() {
        Assert.assertEquals(MainAgentActionTypeEnumVO.FINAL, MainAgentActionTypeEnumVO.ofCode("FINAL").orElse(null));
        Assert.assertFalse(MainAgentActionTypeEnumVO.ofCode("CREATE_ARTIFACT").isPresent());
        Assert.assertFalse(MainAgentActionTypeEnumVO.ofCode("UPDATE_ARTIFACT").isPresent());
        Assert.assertFalse(MainAgentActionTypeEnumVO.ofCode("UNKNOWN").isPresent());
    }

    @Test
    public void test_contractRegistry_hasMainAgentContract() {
        AgentNodeContract contract = ContractRegistry.defaultRegistry()
                .getRequired(AgentComponentCodeEnumVO.MAIN_AGENT);

        Assert.assertEquals("MainAgentActionContract", contract.getName());
        Assert.assertEquals("main-agent-action-v1", contract.getVersion());
    }

    @Test
    public void test_stateDeltaScopeRules_rejectUnexpectedFields() {
        Assert.assertTrue(StateDeltaScopeRules.isAllowed("FINAL", "finalAnswerCandidate"));
        Assert.assertFalse(StateDeltaScopeRules.isAllowed("FINAL", "artifactDraft"));
        Assert.assertFalse(StateDeltaScopeRules.isAllowed("CREATE_ARTIFACT", "artifactDraft"));
        Assert.assertFalse(StateDeltaScopeRules.isAllowed("UPDATE_ARTIFACT", "artifactPatch"));
        Assert.assertFalse(StateDeltaScopeRules.isAllowed("FINAL", "runStatus"));
        Assert.assertTrue(StateDeltaScopeRules.isRuntimeOwnedField("runStatus"));
    }

    @Test
    public void test_rawOutputParser_extractsSingleJsonObjectFromCodeFence() {
        RawOutputParseResult result = RawOutputParser.defaultParser()
                .parse("```json\n{\"action\":\"FINAL\"}\n```");

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("FINAL", result.getJsonObject().getString("action"));
    }

    @Test
    public void test_rawOutputParser_repairs_missing_trailing_object_brace() {
        RawOutputParseResult result = RawOutputParser.defaultParser()
                .parse("{\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"ok\"}}");

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("ok", result.getJsonObject()
                .getJSONObject("stateDelta")
                .getJSONObject("finalAnswerCandidate")
                .getString("content"));
    }

    @Test
    public void test_contractValidator_acceptsValidFinalAction() {
        String raw = "{"
                + "\"action\":\"FINAL\","
                + "\"content\":{\"text\":\"hello\"},"
                + "\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"hello\"}}"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateMainAgentAction(raw);

        Assert.assertTrue(result.isPassed());
        Assert.assertTrue(result.getViolations().isEmpty());
    }

    @Test
    public void test_contractValidator_rejectsLifecycleFieldInNodeOutput() {
        String raw = "{"
                + "\"action\":\"FINAL\","
                + "\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"hello\"}},"
                + "\"runStatus\":\"COMPLETED\""
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateMainAgentAction(raw);

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("FORBIDDEN_RUNTIME_FIELD", result.getViolations().get(0).getCode());
    }

    @Test
    public void test_contractValidator_rejectsRemovedArtifactActions() {
        String raw = "{"
                + "\"action\":\"UPDATE_ARTIFACT\","
                + "\"stateDelta\":{\"artifactPatch\":{\"artifactId\":\"artifact-1\",\"content\":\"new\"}}"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateMainAgentAction(raw);

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("INVALID_ACTION", result.getViolations().get(0).getCode());
    }

    @Test
    public void test_contractValidator_acceptsFailedNotebookStepStatus() {
        String raw = "{"
                + "\"perUpdate\":{\"mode\":\"PER\",\"stepUpdates\":[{\"stepId\":\"s1\",\"status\":\"FAILED\"}]},"
                + "\"action\":\"FINAL\","
                + "\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"The tool failed, so I cannot complete it.\"}}"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateMainAgentAction(raw);

        Assert.assertTrue(result.isPassed());
    }

    @Test
    public void test_contractValidator_rejectsInvalidNotebookStepStatus() {
        String raw = "{"
                + "\"perUpdate\":{\"mode\":\"PER\",\"stepUpdates\":[{\"stepId\":\"s1\",\"status\":\"MAYBE_DONE\"}]},"
                + "\"action\":\"FINAL\","
                + "\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"hello\"}}"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateMainAgentAction(raw);

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("INVALID_NOTEBOOK_STEP_STATUS", result.getViolations().get(0).getCode());
    }
}


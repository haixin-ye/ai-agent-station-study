package yhx.com.test.domain.agent.contract;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.service.contract.ContractValidator;

public class MainAgentDelegateAgentsContractTest {

    @Test
    public void main_agent_action_enum_exposes_delegate_agents_but_not_code_agent() {
        Assert.assertEquals(MainAgentActionTypeEnumVO.DELEGATE_AGENTS,
                MainAgentActionTypeEnumVO.ofCode("DELEGATE_AGENTS").orElse(null));
        Assert.assertTrue(MainAgentActionTypeEnumVO.ofCode("DELEGATE_CODE_AGENT").isEmpty());
    }

    @Test
    public void validator_accepts_valid_delegate_agents_request() {
        ContractValidationResult result = ContractValidator.defaultValidator().validateMainAgentAction(validRequest());

        Assert.assertTrue(result.isPassed());
    }

    @Test
    public void validator_rejects_invalid_delegate_wait_mode() {
        String raw = validRequest().replace("\"WAIT_ALL\"", "\"WAIT_ANY\"");

        ContractValidationResult result = ContractValidator.defaultValidator().validateMainAgentAction(raw);

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("INVALID_DELEGATE_WAIT_MODE", result.getViolations().get(0).getCode());
    }

    @Test
    public void validator_rejects_delegate_request_without_tasks() {
        String raw = "{"
                + "\"action\":\"DELEGATE_AGENTS\","
                + "\"stateDelta\":{\"delegateAgentsRequest\":{\"waitMode\":\"WAIT_ALL\",\"tasks\":[]}}"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator().validateMainAgentAction(raw);

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("MISSING_DELEGATE_TASKS", result.getViolations().get(0).getCode());
    }

    @Test
    public void validator_rejects_delegate_task_without_objective() {
        String raw = "{"
                + "\"action\":\"DELEGATE_AGENTS\","
                + "\"stateDelta\":{\"delegateAgentsRequest\":{\"waitMode\":\"WAIT_ALL\",\"tasks\":[{\"taskId\":\"t1\",\"name\":\"reader\"}]}}"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator().validateMainAgentAction(raw);

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("MISSING_DELEGATE_TASK_OBJECTIVE", result.getViolations().get(0).getCode());
    }

    private String validRequest() {
        return "{"
                + "\"action\":\"DELEGATE_AGENTS\","
                + "\"stateDelta\":{\"delegateAgentsRequest\":{\"waitMode\":\"WAIT_ALL\",\"tasks\":[{"
                + "\"taskId\":\"t1\","
                + "\"name\":\"reader\","
                + "\"objective\":\"Read the assigned material and commit findings.\""
                + "}]}}"
                + "}";
    }
}

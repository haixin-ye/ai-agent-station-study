package yhx.com.test.domain.agent.harness;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.contract.AgentNodeContract;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.service.contract.ContractRegistry;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.invocation.NodeOutputMapper;
import yhx.com.domain.agent.service.prompt.GenericSubAgentPromptBuilder;
import yhx.com.domain.agent.service.prompt.OutputContractPromptRenderer;

public class SubAgentActionContractTest {

    @Test
    public void generic_sub_agent_prompt_requires_complete_user_readable_work_product_in_commit_result() {
        String prompt = new GenericSubAgentPromptBuilder().build().stream()
                .map(layer -> layer.getContent() == null ? "" : layer.getContent())
                .reduce("", (left, right) -> left + "\n" + right);

        Assert.assertTrue(prompt.contains("complete required work product in commit.result"));
        Assert.assertTrue(prompt.contains("detail is a concise work note"));
        Assert.assertFalse(prompt.contains("Put the short conclusion in result"));

        String contract = new OutputContractPromptRenderer().renderSubAgentActionContract();
        Assert.assertTrue(contract.contains("complete required work product"));
        Assert.assertFalse(contract.contains("Put the short conclusion in result"));
    }

    @Test
    public void registry_has_generic_sub_agent_contract() {
        AgentNodeContract contract = ContractRegistry.defaultRegistry()
                .getRequired(AgentComponentCodeEnumVO.GENERIC_SUB_AGENT);

        Assert.assertEquals("SubAgentActionContract", contract.getName());
        Assert.assertEquals("generic-sub-agent-action-v1", contract.getVersion());
    }

    @Test
    public void sub_agent_action_enum_can_resolve_commit_but_not_final() {
        Assert.assertEquals(SubAgentActionTypeEnumVO.COMMIT, SubAgentActionTypeEnumVO.ofCode("COMMIT").orElse(null));
        Assert.assertTrue(SubAgentActionTypeEnumVO.ofCode("FINAL").isEmpty());
    }

    @Test
    public void validator_accepts_valid_commit() {
        String raw = "{"
                + "\"action\":\"COMMIT\","
                + "\"commit\":{\"taskId\":\"task-1\",\"status\":\"SUCCESS\",\"result\":\"done\"}"
                + "}";

        ContractValidationResult result = ContractValidator.defaultValidator().validateSubAgentAction(raw);

        Assert.assertTrue(result.isPassed());
    }

    @Test
    public void validator_rejects_final_action() {
        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateSubAgentAction("{\"action\":\"FINAL\",\"commit\":{\"taskId\":\"task-1\",\"status\":\"SUCCESS\",\"result\":\"done\"}}");

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("INVALID_SUB_AGENT_ACTION", result.getViolations().get(0).getCode());
    }

    @Test
    public void validator_rejects_commit_without_task_id() {
        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateSubAgentAction("{\"action\":\"COMMIT\",\"commit\":{\"status\":\"SUCCESS\",\"result\":\"done\"}}");

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("MISSING_COMMIT_TASK_ID", result.getViolations().get(0).getCode());
    }

    @Test
    public void validator_rejects_commit_without_status() {
        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateSubAgentAction("{\"action\":\"COMMIT\",\"commit\":{\"taskId\":\"task-1\",\"result\":\"done\"}}");

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("MISSING_COMMIT_STATUS", result.getViolations().get(0).getCode());
    }

    @Test
    public void validator_rejects_commit_without_result() {
        ContractValidationResult result = ContractValidator.defaultValidator()
                .validateSubAgentAction("{\"action\":\"COMMIT\",\"commit\":{\"taskId\":\"task-1\",\"status\":\"SUCCESS\"}}");

        Assert.assertFalse(result.isPassed());
        Assert.assertEquals("MISSING_COMMIT_RESULT", result.getViolations().get(0).getCode());
    }

    @Test
    public void mapper_maps_generic_sub_agent_action() {
        JSONObject jsonObject = JSONObject.parseObject("{"
                + "\"action\":\"COMMIT\","
                + "\"commit\":{\"taskId\":\"task-1\",\"status\":\"SUCCESS\",\"result\":\"done\"}"
                + "}");

        Object mapped = new NodeOutputMapper().map(
                AgentComponentCodeEnumVO.GENERIC_SUB_AGENT.name(),
                "generic-sub-agent-action-v1",
                jsonObject);

        Assert.assertTrue(mapped instanceof SubAgentActionVO);
        SubAgentActionVO action = (SubAgentActionVO) mapped;
        Assert.assertEquals("COMMIT", action.getAction());
        Assert.assertEquals("task-1", action.getCommit().getTaskId());
    }
}

package yhx.com.test.domain.agent.contract;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.service.contract.ContractRegistry;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.prompt.OutputContractPromptRenderer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContractPromptAlignmentTest {

    private final ContractValidator validator = ContractValidator.defaultValidator();
    private final OutputContractPromptRenderer renderer = new OutputContractPromptRenderer();

    @Test
    public void every_active_llm_component_renders_its_registered_contract_version() {
        activeContracts().forEach((component, version) -> {
            String prompt = renderer.renderFor(component.name(), version);
            Assert.assertTrue(component + " prompt must expose " + version,
                    prompt.contains("Required contract version: " + version));
        });
    }

    @Test
    public void every_json_example_embedded_in_an_output_contract_passes_that_contract_validator() {
        activeContracts().forEach((component, version) -> renderer.renderFor(component.name(), version).lines()
                .map(String::trim)
                .filter(line -> line.startsWith("{") && line.endsWith("}"))
                .forEach(example -> assertPassed(component, example)));
        assertPassed(AgentComponentCodeEnumVO.MAIN_AGENT,
                "{\"taskUpdate\":{\"lastDecision\":\"ready\"},\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"done\"}}}");
        assertPassed(AgentComponentCodeEnumVO.FINAL_REPAIR,
                "{\"action\":\"REPAIR_FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"fixed\"}}}");
        assertPassed(AgentComponentCodeEnumVO.FINAL_RESPONSE_GUARD,
                "{\"status\":\"PASSED\",\"finalContent\":\"done\",\"failureCode\":null,\"detail\":\"Safe.\"}");
    }

    @Test
    public void component_validators_reject_outputs_that_violate_the_exposed_contract() {
        assertFailed(AgentComponentCodeEnumVO.GENERIC_SUB_AGENT,
                "{\"action\":\"COMMIT\",\"commit\":{\"taskId\":\"s1\",\"status\":\"SUCCESS\"}}");
        assertFailed(AgentComponentCodeEnumVO.FINAL_REPAIR,
                "{\"action\":\"REPAIR_FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{}}}");
        assertFailed(AgentComponentCodeEnumVO.MEMORY_EXTRACTOR,
                "{\"memories\":[{\"memoryType\":\"USER_PREFERENCE\",\"summary\":\"x\",\"score\":0.8,\"reason\":\"x\"}]}");
        assertFailed(AgentComponentCodeEnumVO.RAG_ASSET_ANALYZER,
                "{\"title\":\"Source\",\"summary\":\"Summary\",\"language\":\"en\",\"keySymbols\":[]}");
        assertFailed(AgentComponentCodeEnumVO.CONTEXT_PLANNER,
                "{\"status\":\"CONTEXT_OVER_BUDGET\",\"selectedContext\":[]}");
        assertFailed(AgentComponentCodeEnumVO.RAG_VERIFIER,
                "{\"status\":\"PASSED\",\"failureCode\":\"STALE_FAILURE\",\"detail\":\"Grounded.\"}");
        assertFailed(AgentComponentCodeEnumVO.FINAL_RESPONSE_GUARD,
                "{\"status\":\"PASSED\",\"finalContent\":\"done\",\"failureCode\":\"STALE_FAILURE\",\"detail\":\"Safe.\"}");
        assertFailed(AgentComponentCodeEnumVO.TURN_SUMMARY,
                "{\"summary\":\"summary\",\"intent\":\"ask\",\"topics\":[],\"entities\":[\"not-an-object\"],\"artifactRefs\":[],\"importanceScore\":0.5,\"requiresLongTermExtraction\":false}");
        assertFailed(AgentComponentCodeEnumVO.MAIN_AGENT,
                "{\"taskUpdate\":{},\"action\":\"DELEGATE_AGENTS\",\"stateDelta\":{\"delegateAgentsRequest\":{\"waitMode\":\"WAIT_ALL\",\"tasks\":[{\"taskId\":\"s1\",\"name\":\"worker\",\"objective\":\"Work\",\"requiredOutput\":\"Result\",\"requestedCapabilities\":[\"FILE_READ\"]}]}}}");
    }

    @Test
    public void repair_prompt_reuses_the_exact_original_contract() {
        activeContracts().forEach((component, version) -> {
            String repair = renderer.renderRepairContract(component.name(), version);
            Assert.assertTrue(component + " repair prompt must include " + version,
                    repair.contains("Required contract version: " + version));
        });
    }

    private void assertPassed(AgentComponentCodeEnumVO component, String raw) {
        ContractValidationResult result = validator.validateComponentOutput(component.name(), raw);
        Assert.assertTrue(component + " should pass but got " + result.getViolations(), result.isPassed());
    }

    private void assertFailed(AgentComponentCodeEnumVO component, String raw) {
        Assert.assertFalse(component + " should fail", validator.validateComponentOutput(component.name(), raw).isPassed());
    }

    private Map<AgentComponentCodeEnumVO, String> activeContracts() {
        Map<AgentComponentCodeEnumVO, String> contracts = new LinkedHashMap<>();
        ContractRegistry registry = ContractRegistry.defaultRegistry();
        for (AgentComponentCodeEnumVO component : List.of(
                AgentComponentCodeEnumVO.CONTEXT_PLANNER,
                AgentComponentCodeEnumVO.MAIN_AGENT,
                AgentComponentCodeEnumVO.GENERIC_SUB_AGENT,
                AgentComponentCodeEnumVO.RAG_VERIFIER,
                AgentComponentCodeEnumVO.TOOL_VERIFIER,
                AgentComponentCodeEnumVO.FINAL_RESPONSE_GUARD,
                AgentComponentCodeEnumVO.FINAL_REPAIR,
                AgentComponentCodeEnumVO.TURN_SUMMARY,
                AgentComponentCodeEnumVO.MEMORY_EXTRACTOR,
                AgentComponentCodeEnumVO.SESSION_TASK_SUMMARY,
                AgentComponentCodeEnumVO.MEMORY_GOVERNANCE,
                AgentComponentCodeEnumVO.CONVERSATION_ROLLUP,
                AgentComponentCodeEnumVO.RAG_ASSET_ANALYZER)) {
            contracts.put(component, registry.getRequired(component).getVersion());
        }
        return contracts;
    }
}

package yhx.com.test.domain.agent.invocation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptAssemblyCommand;
import yhx.com.domain.agent.service.invocation.NodeFunctionSpecRegistry;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.test.domain.agent.invocation.support.InMemoryPromptContentProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PromptContractBoundaryTest {

    @Test
    public void db_role_prompt_is_preserved_but_java_owns_main_agent_v2_contract() {
        String prompt = promptWithRole("Role from DB.");

        Assert.assertTrue(prompt.contains("Role from DB."));
        Assert.assertTrue(prompt.contains("Required contract version: main-agent-action-v2"));
        Assert.assertTrue(prompt.contains("taskUpdate"));
        Assert.assertTrue(prompt.contains("stateDelta field by action"));
    }

    @Test
    public void main_agent_prompt_describes_runtime_owned_execution_as_a_positive_workflow() {
        String prompt = promptWithRole("Role from DB.");

        Assert.assertTrue(prompt.contains("Request external operations through CALL_TOOL"));
        Assert.assertTrue(prompt.contains("Runtime handles deterministic approval"));
        Assert.assertTrue(prompt.contains("Use ASK_USER when a missing user decision genuinely blocks a safe next action."));
    }

    @Test
    public void main_agent_prompt_selects_a_planning_profile_for_the_first_loop() {
        String prompt = promptWithRoleAndStage("Role from DB.", "PLANNING");

        Assert.assertTrue(prompt.contains("first task decision"));
        Assert.assertTrue(prompt.contains("Identify every requested deliverable"));
        Assert.assertTrue(prompt.contains("smallest useful plan"));
        Assert.assertTrue(prompt.contains("Choose exactly one first action"));
        Assert.assertTrue(prompt.contains("READY_TO_DELIVER"));
        Assert.assertTrue(prompt.contains("A content-only child uses [\"COMMIT\"]"));
        Assert.assertTrue(prompt.contains("requestedCapabilities array of Runtime permission codes"));
    }

    @Test
    public void main_agent_prompt_places_required_capabilities_on_each_delegated_task() {
        String prompt = promptWithRoleAndStage("Role from DB.", "PLANNING");

        Assert.assertTrue(prompt.contains("stateDelta.delegateAgentsRequest.tasks[i].requestedCapabilities"));
        Assert.assertTrue(prompt.contains("non-empty requestedCapabilities array"));
        Assert.assertTrue(prompt.contains("Include COMMIT in every task"));
        Assert.assertFalse(prompt.contains("requestedCapabilities is optional"));
    }

    @Test
    public void main_agent_prompt_selects_an_execution_profile_for_later_loops() {
        String prompt = promptWithRoleAndStage("Role from DB.", "EXECUTING");

        Assert.assertTrue(prompt.contains("newest loopTimeline record"));
        Assert.assertTrue(prompt.contains("actual Runtime outcome"));
        Assert.assertTrue(prompt.contains("Update the affected steps"));
        Assert.assertTrue(prompt.contains("record a planRevision"));
        Assert.assertTrue(prompt.contains("every requested external side effect has matching successful evidence"));
    }

    @Test
    public void main_agent_prompt_selects_a_delivery_profile_after_work_is_complete() {
        String prompt = promptWithRoleAndStage("Role from DB.", "DELIVERING");

        Assert.assertTrue(prompt.contains("Compose a complete answer to the original request"));
        Assert.assertTrue(prompt.contains("clear transitions and labels"));
        Assert.assertTrue(prompt.contains("actual materialized content"));
        Assert.assertTrue(prompt.contains("every requested deliverable"));
        Assert.assertTrue(prompt.contains("The normal action in this stage is FINAL"));
        Assert.assertTrue(prompt.contains("finalAnswerCandidate"));
        Assert.assertTrue(prompt.contains("stateDelta.finalAnswerCandidate.format"));
    }

    @Test
    public void main_agent_prompt_uses_the_canonical_run_context_envelope() {
        String prompt = promptWithRole("{\"runBaseContext\":{\"userInput\":\"publish this\"},\"loopTimeline\":[]}");

        Assert.assertTrue(prompt.contains("Read runBaseContext.userInput first"));
        Assert.assertTrue(prompt.contains("selectedSessionContext"));
        Assert.assertTrue(prompt.contains("\"runBaseContext\""));
        Assert.assertTrue(prompt.contains("\"loopTimeline\""));
        Assert.assertTrue(prompt.contains("payloadManifest"));
        Assert.assertTrue(prompt.contains("activePayloads"));
        Assert.assertTrue(prompt.contains("runtimeControl"));
        Assert.assertTrue(prompt.contains("availability is AVAILABLE"));
        Assert.assertTrue(prompt.contains("configured but not currently healthy"));
    }

    @Test
    public void removed_fragmented_notebook_contract_is_not_reintroduced() {
        String prompt = promptWithRole("Role from DB.");

        Assert.assertFalse(prompt.contains("perUpdate"));
        Assert.assertFalse(prompt.contains("MainAgentNotebook"));
        Assert.assertFalse(prompt.contains("previousLoopOutcome"));
        Assert.assertFalse(prompt.contains("main-agent-action-v1"));
        Assert.assertFalse(prompt.contains("\"action\":\"PLAN\""));
        Assert.assertFalse(prompt.contains("\"action\":\"REPAIR_FINAL\""));
    }

    @Test
    public void function_call_mode_uses_the_same_v2_main_agent_contract() {
        PromptAssembler assembler = new PromptAssembler(new InMemoryPromptContentProvider()
                .put(AgentComponentCodeEnumVO.MAIN_AGENT.name(), "Role from DB."));
        String prompt = assembler.assemble(PromptAssemblyCommand.builder()
                .runId("run-1")
                .agentId("agent-1")
                .componentCode(AgentComponentCodeEnumVO.MAIN_AGENT.name())
                .contractVersion("main-agent-action-v2")
                .promptVersion("v2")
                .invocationMode(NodeInvocationModeEnumVO.FUNCTION_CALL)
                .functionSpecs(NodeFunctionSpecRegistry.defaultRegistry()
                        .resolveMainAgent(yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO.EXECUTING))
                .inputView("{\"runBaseContext\":{\"userInput\":\"publish this\"}}")
                .metadata(Map.of("mainAgentStage", "EXECUTING"))
                .build()).assembledPrompt();

        Assert.assertTrue(prompt.contains("Call exactly one function"));
        Assert.assertTrue(prompt.contains("main_call_tool"));
        Assert.assertTrue(prompt.contains("complete assistant response"));
        Assert.assertFalse(prompt.contains("Do not output raw JSON text"));
        Assert.assertTrue(prompt.contains("taskUpdate"));
    }

    @Test
    public void prompt_state_view_serialization_does_not_emit_fastjson_ref_fragments() {
        PromptAssembler assembler = new PromptAssembler(new InMemoryPromptContentProvider()
                .put(AgentComponentCodeEnumVO.MAIN_AGENT.name(), "Role from DB."));
        List<Map<String, Object>> sharedEvidence = List.of(Map.of(
                "evidenceId", "evidence-success",
                "summary", "Tool action succeeded."
        ));
        Map<String, Object> stateView = new LinkedHashMap<>();
        stateView.put("runBaseContext", Map.of("userInput", "publish this"));
        stateView.put("loopTimeline", List.of(Map.of(
                "mainOutput", Map.of("action", "CALL_TOOL"),
                "runtimeOutcome", Map.of(
                        "status", "CONTINUE_LOOP",
                        "evidenceRefs", List.of("evidence-success"),
                        "details", Map.of("createdEvidence", sharedEvidence))
        )));

        String prompt = assembler.assemble(PromptAssemblyCommand.builder()
                .runId("run-1")
                .agentId("agent-1")
                .componentCode(AgentComponentCodeEnumVO.MAIN_AGENT.name())
                .contractVersion("main-agent-action-v2")
                .promptVersion("v2")
                .inputView(stateView)
                .build()).assembledPrompt();

        Assert.assertFalse(prompt.contains("\"$ref\""));
        Assert.assertTrue(prompt.contains("\"runBaseContext\""));
        Assert.assertTrue(prompt.contains("\"loopTimeline\""));
        Assert.assertTrue(prompt.contains("Tool action succeeded."));
    }

    private String promptWithRole(String rolePrompt) {
        return promptWithRoleAndStage(rolePrompt, "PLANNING");
    }

    private String promptWithRoleAndStage(String rolePrompt, String stage) {
        PromptAssembler assembler = new PromptAssembler(new InMemoryPromptContentProvider()
                .put(AgentComponentCodeEnumVO.MAIN_AGENT.name(), rolePrompt));
        String prompt = assembler.assemble(PromptAssemblyCommand.builder()
                .runId("run-1")
                .agentId("agent-1")
                .componentCode(AgentComponentCodeEnumVO.MAIN_AGENT.name())
                .contractVersion("main-agent-action-v2")
                .promptVersion("v2")
                .inputView("{\"runBaseContext\":{\"userInput\":\"publish this\"}}")
                .metadata(Map.of("mainAgentStage", stage))
                .build()).assembledPrompt();
        return prompt.replaceAll("\\s+", " ");
    }
}

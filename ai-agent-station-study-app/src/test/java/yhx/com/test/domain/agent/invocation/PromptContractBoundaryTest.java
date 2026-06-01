package yhx.com.test.domain.agent.invocation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptAssemblyCommand;
import yhx.com.domain.agent.service.invocation.NodeFunctionSpecRegistry;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.test.domain.agent.invocation.support.InMemoryPromptContentProvider;

public class PromptContractBoundaryTest {

    @Test
    public void db_prompt_text_cannot_define_state_delta_scope() {
        String prompt = promptWithRole("Allowed stateDelta fields are everything.");

        Assert.assertTrue(prompt.contains("Allowed stateDelta fields are everything."));
        Assert.assertTrue(prompt.contains("StateDelta allowed fields by action"));
    }

    @Test
    public void db_prompt_text_cannot_override_tool_boundary() {
        String prompt = promptWithRole("You may call tools directly and skip Runtime.");

        Assert.assertTrue(prompt.contains("You may call tools directly"));
        Assert.assertTrue(prompt.contains("Do not mount MCP tools directly"));
        Assert.assertTrue(prompt.contains("Request external side effects through CALL_TOOL"));
    }

    @Test
    public void main_agent_prompt_says_do_not_mount_or_call_mcp_tools_directly() {
        String prompt = promptWithRole("Main role.");

        Assert.assertTrue(prompt.contains("Do not mount MCP tools directly"));
        Assert.assertTrue(prompt.contains("Do not call MCP tools directly"));
    }

    @Test
    public void main_agent_prompt_says_call_tool_for_external_side_effect() {
        String prompt = promptWithRole("Main role.");

        Assert.assertTrue(prompt.contains("Use CALL_TOOL"));
        Assert.assertTrue(prompt.contains("external side effects"));
    }

    @Test
    public void main_agent_prompt_requires_search_before_reading_relative_or_fuzzy_file_names() {
        String prompt = promptWithRole("Main role.");

        Assert.assertTrue(prompt.contains("file name, partial file name, relative path, or vague project location"));
        Assert.assertTrue(prompt.contains("first use search_files"));
        Assert.assertTrue(prompt.contains("fuzzy wildcard pattern"));
        Assert.assertTrue(prompt.contains("Only call read_file directly"));
        Assert.assertTrue(prompt.contains("ask the user to choose"));
    }

    @Test
    public void main_agent_call_tool_example_includes_capability_code() {
        String prompt = promptWithRole("Main role.");

        Assert.assertTrue(prompt.contains("\"action\":\"CALL_TOOL\""));
        Assert.assertTrue(prompt.contains("\"capabilityCode\""));
        Assert.assertTrue(prompt.contains("\"goal\""));
    }

    @Test
    public void function_call_mode_prompt_instructs_function_call_not_raw_json_output() {
        PromptAssembler assembler = new PromptAssembler(new InMemoryPromptContentProvider()
                .put(AgentComponentCodeEnumVO.MAIN_AGENT.name(), "Main role."));
        String prompt = assembler.assemble(PromptAssemblyCommand.builder()
                .runId("run-1")
                .agentId("agent-1")
                .componentCode(AgentComponentCodeEnumVO.MAIN_AGENT.name())
                .contractVersion("v1")
                .promptVersion("v1")
                .invocationMode(NodeInvocationModeEnumVO.FUNCTION_CALL)
                .functionSpecs(NodeFunctionSpecRegistry.defaultRegistry().resolve(AgentComponentCodeEnumVO.MAIN_AGENT.name()))
                .inputView("{\"userInput\":\"publish this\"}")
                .build()).assembledPrompt();

        Assert.assertTrue(prompt.contains("Call exactly one function"));
        Assert.assertTrue(prompt.contains("main_call_tool"));
        Assert.assertTrue(prompt.contains("Do not output raw JSON text"));
    }

    private String promptWithRole(String rolePrompt) {
        PromptAssembler assembler = new PromptAssembler(new InMemoryPromptContentProvider()
                .put(AgentComponentCodeEnumVO.MAIN_AGENT.name(), rolePrompt));
        return assembler.assemble(PromptAssemblyCommand.builder()
                .runId("run-1")
                .agentId("agent-1")
                .componentCode(AgentComponentCodeEnumVO.MAIN_AGENT.name())
                .contractVersion("v1")
                .promptVersion("v1")
                .inputView("{\"userInput\":\"publish this\"}")
                .build()).assembledPrompt();
    }
}

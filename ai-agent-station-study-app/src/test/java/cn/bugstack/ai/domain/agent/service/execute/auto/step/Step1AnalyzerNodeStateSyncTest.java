package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.NextRoundDirectiveVO;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.entity.TaskBoardItemVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.NextRoundDirectiveTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

public class Step1AnalyzerNodeStateSyncTest {

    @Test
    public void should_create_master_plan_and_current_round_on_first_sync() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("search and write", 3);

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .planId("plan-1")
                .round(1)
                .taskGoal("search weather")
                .toolRequired(true)
                .toolName("baidu-search")
                .toolPurpose("collect facts")
                .expectedOutput("search summary")
                .completionHint("search completed")
                .build();

        Step1AnalyzerNode.syncStructuredPlanningState(context, plan);

        Assert.assertNotNull(context.getMasterPlan());
        Assert.assertEquals(1, context.getMasterPlan().getMainSteps().size());
        CurrentRoundTaskVO currentRound = context.getCurrentRound();
        Assert.assertEquals("step-1", currentRound.getCurrentStepId());
        Assert.assertEquals("search weather", currentRound.getRoundTask());
        Assert.assertEquals(1, currentRound.getSuggestedTools().size());
        Assert.assertTrue(context.getTaskBoard().containsKey("step-1"));
        Assert.assertNotNull(context.getRoundArchive().get(1).getNode1PlanSnapshot());
    }

    @Test
    public void should_reuse_same_step_id_when_replan_same_step() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("search and write", 3);
        context.setNextRoundDirective(NextRoundDirectiveVO.builder()
                .directiveType(NextRoundDirectiveTypeEnumVO.REPLAN_SAME_STEP)
                .targetStepId("step-search")
                .reason("missing file write")
                .build());

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .planId("plan-2")
                .round(2)
                .taskGoal("retry search and prepare write")
                .toolRequired(false)
                .completionHint("retry finished")
                .build();

        Step1AnalyzerNode.syncStructuredPlanningState(context, plan);

        Assert.assertEquals("step-search", context.getCurrentRound().getCurrentStepId());
        Assert.assertTrue(context.getTaskBoard().containsKey("step-search"));
    }

    @Test
    public void should_parse_chinese_legacy_plan_text() {
        Step1AnalyzerNode node = new Step1AnalyzerNode();
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("search and write", 3);

        String legacyText = """
                下一步：先调用 baidu-search 获取北京天气，再准备写入桌面文本

                状态：本轮需要工具，先拿到可写入的天气摘要
                """;

        StepExecutionPlanVO plan = ReflectionTestUtils.invokeMethod(
                node,
                "parseLegacyTextPlan",
                legacyText,
                1,
                context,
                Set.of("baidu-search", "filesystem")
        );

        Assert.assertNotNull(plan);
        Assert.assertEquals("先调用 baidu-search 获取北京天气，再准备写入桌面文本", plan.getTaskGoal());
        Assert.assertTrue(plan.getToolRequired());
        Assert.assertEquals("baidu-search", plan.getToolName());
        Assert.assertEquals("本轮需要工具，先拿到可写入的天气摘要", plan.getCompletionHint());
    }

    @Test
    public void should_build_planning_prompt_with_digest_instead_of_raw_execution_history() {
        Step1AnalyzerNode node = new Step1AnalyzerNode();
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("search and write", 3);
        context.getExecutionHistory().append("round-1 raw execution detail that should not be injected verbatim");
        context.getTaskBoard().put("step-1", TaskBoardItemVO.builder()
                .stepId("step-1")
                .lastRoundTask("search weather")
                .attemptCount(2)
                .build());

        String prompt = ReflectionTestUtils.invokeMethod(
                node,
                "buildPlanningPrompt",
                2,
                3,
                "search and write",
                "search and write summary",
                "knowledge",
                "current round task",
                "latest supervision summary",
                "latest execution summary",
                "{\"round\":1}",
                Set.of("baidu-search", "filesystem"),
                context
        );

        Assert.assertNotNull(prompt);
        Assert.assertTrue(prompt.contains("\"planningDigest\""));
        Assert.assertTrue(prompt.contains("\"executionHistoryTail\""));
        Assert.assertTrue(prompt.contains("single-shot QA/RAG/explanation task"));
        Assert.assertFalse(prompt.contains("\"executionHistory\":"));
    }

    @Test
    public void should_force_node1_tool_choice_to_none_while_preserving_tool_visibility() {
        Prompt prompt = ReflectionTestUtils.invokeMethod(
                new Step1AnalyzerNode(),
                "buildPlanningRequestPrompt",
                "{\"task\":\"generate_current_round_plan\"}"
        );

        Assert.assertNotNull(prompt);
        Assert.assertEquals("{\"task\":\"generate_current_round_plan\"}", prompt.getContents());
        Assert.assertTrue(prompt.getOptions() instanceof OpenAiChatOptions);

        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
        Assert.assertEquals(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.NONE, options.getToolChoice());
        Assert.assertTrue(options.getTools() == null || options.getTools().isEmpty());
        Assert.assertTrue(options.getToolCallbacks() == null || options.getToolCallbacks().isEmpty());
    }

    @Test
    public void should_build_todo_list_for_frontend_display() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("search and write", 3);

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .planId("plan-frontend")
                .round(1)
                .taskGoal("write txt file")
                .toolRequired(false)
                .expectedOutput("txt ready")
                .completionHint("finish the file")
                .build();

        Step1AnalyzerNode.syncStructuredPlanningState(context, plan);

        String todoList = Step1AnalyzerNode.buildTodoListText(context);

        Assert.assertTrue(todoList.contains("本轮规划清单"));
        Assert.assertTrue(todoList.contains("step-1"));
        Assert.assertTrue(todoList.contains("任务"));
        Assert.assertTrue(todoList.contains("完成标准"));
    }
}

package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AcceptedResultVO;
import cn.bugstack.ai.domain.agent.model.entity.OverallStatusVO;
import cn.bugstack.ai.domain.agent.model.entity.SessionGoalVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.OverallStateEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class Step4LogExecutionSummaryNodeInputContractTest {

    @Test
    public void shouldBuildSummaryInputFromAcceptedResultsAndSessionGoal() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();

        SessionGoalVO sessionGoal = new SessionGoalVO();
        sessionGoal.setRawUserInput("Search Beijing weather and save the result to the desktop.");
        sessionGoal.setSanitizedGoal("Search Beijing weather and save the result to the desktop.");
        context.setSessionGoal(sessionGoal);

        AcceptedResultVO acceptedResult = new AcceptedResultVO();
        acceptedResult.setStepId("step-1");
        acceptedResult.setResultType("ROUND_RESULT");
        acceptedResult.setContent("Beijing weather confirmed and accepted.");
        context.setAcceptedResults(Collections.singletonList(acceptedResult));

        OverallStatusVO overallStatus = new OverallStatusVO();
        overallStatus.setState(OverallStateEnumVO.COMPLETED);
        overallStatus.setFinalDecision("FINISH_SUCCESS");
        context.setOverallStatus(overallStatus);

        String input = Step4LogExecutionSummaryNode.buildSummaryInput(context, true);
        JSONObject jsonObject = JSON.parseObject(input);

        Assert.assertEquals("Search Beijing weather and save the result to the desktop.", jsonObject.getString("rawUserInput"));
        Assert.assertEquals("Search Beijing weather and save the result to the desktop.", jsonObject.getString("sanitizedGoal"));
        Assert.assertTrue(jsonObject.getBooleanValue("completed"));
        Assert.assertTrue(jsonObject.containsKey("roundArchive"));
        Assert.assertTrue(jsonObject.containsKey("nextRoundDirective"));
        Assert.assertTrue(jsonObject.containsKey("answerPolicy"));

        JSONObject overallStatusJson = jsonObject.getJSONObject("overallStatus");
        Assert.assertEquals("COMPLETED", overallStatusJson.getString("state"));
        Assert.assertEquals("FINISH_SUCCESS", overallStatusJson.getString("finalDecision"));

        JSONObject answerPolicy = jsonObject.getJSONObject("answerPolicy");
        Assert.assertTrue(answerPolicy.getBooleanValue("mustRespectOverallStatus"));
        Assert.assertTrue(answerPolicy.getBooleanValue("mustNotUseExecutionHistoryAsTruthSource"));
        Assert.assertTrue(answerPolicy.getBooleanValue("mustBeUserFacing"));

        JSONArray acceptedResults = jsonObject.getJSONArray("acceptedResults");
        Assert.assertEquals(1, acceptedResults.size());
        Assert.assertEquals("Beijing weather confirmed and accepted.", acceptedResults.getJSONObject(0).getString("content"));
    }

    @Test
    public void shouldNotLeakUnacceptedExecutionHistoryIntoSummaryInput() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();

        SessionGoalVO sessionGoal = new SessionGoalVO();
        sessionGoal.setRawUserInput("Save Beijing weather to a text file.");
        sessionGoal.setSanitizedGoal("Save Beijing weather to a text file.");
        context.setSessionGoal(sessionGoal);
        context.setExecutionHistory(new StringBuilder("""
                === Round 2 Execution(Node2) ===
                saved to C:/fake/path/report.txt
                """));

        AcceptedResultVO acceptedResult = new AcceptedResultVO();
        acceptedResult.setStepId("step-1");
        acceptedResult.setResultType("ROUND_RESULT");
        acceptedResult.setContent("Only weather retrieval was accepted.");
        context.setAcceptedResults(Collections.singletonList(acceptedResult));

        OverallStatusVO overallStatus = new OverallStatusVO();
        overallStatus.setState(OverallStateEnumVO.RUNNING);
        overallStatus.setFinalDecision("FINISH_PARTIAL");
        context.setOverallStatus(overallStatus);

        String input = Step4LogExecutionSummaryNode.buildSummaryInput(context, false);

        Assert.assertFalse(input.contains("C:/fake/path/report.txt"));
        Assert.assertFalse(input.contains("executionHistory"));
        Assert.assertTrue(input.contains("acceptedResults"));
        Assert.assertTrue(input.contains("FINISH_PARTIAL"));
        Assert.assertTrue(input.contains("\"answerPolicy\""));
    }
}

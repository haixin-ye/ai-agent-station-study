package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.TokenUsageAccumulator;
import org.junit.Assert;
import org.junit.Test;

public class TokenUsageAccumulatorTest {

    @Test
    public void test_addAndSnapshot() {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();

        accumulator.add(120, 30, 150);
        accumulator.add(80, 20, 100);

        TokenUsageAccumulator.TokenUsageSnapshot snapshot = accumulator.snapshot();
        Assert.assertEquals(200L, snapshot.getInputTokens());
        Assert.assertEquals(50L, snapshot.getOutputTokens());
        Assert.assertEquals(250L, snapshot.getTotalTokens());
    }

    @Test
    public void test_createTokenClientUsageResult() {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createTokenClientUsageResult(
                2,
                "{\"clientId\":\"c1\",\"inputTokens\":10,\"outputTokens\":5,\"totalTokens\":15}",
                "sess-1"
        );

        Assert.assertEquals("token", result.getType());
        Assert.assertEquals("client_usage", result.getSubType());
        Assert.assertEquals(Integer.valueOf(2), result.getStep());
        Assert.assertFalse(result.getCompleted());
        Assert.assertEquals("sess-1", result.getSessionId());
    }

    @Test
    public void test_createTokenTotalUsageResult() {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createTokenTotalUsageResult(
                "{\"inputTokens\":100,\"outputTokens\":40,\"totalTokens\":140}",
                "sess-2"
        );

        Assert.assertEquals("token", result.getType());
        Assert.assertEquals("total_usage", result.getSubType());
        Assert.assertNull(result.getStep());
        Assert.assertTrue(result.getCompleted());
        Assert.assertEquals("sess-2", result.getSessionId());
    }

    @Test
    public void test_createErrorResult() {
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createErrorResult(
                "执行异常：401 - Invalid Token",
                "sess-3"
        );

        Assert.assertEquals("error", result.getType());
        Assert.assertNull(result.getSubType());
        Assert.assertNull(result.getStep());
        Assert.assertTrue(result.getCompleted());
        Assert.assertEquals("sess-3", result.getSessionId());
        Assert.assertEquals("执行异常：401 - Invalid Token", result.getContent());
    }
}

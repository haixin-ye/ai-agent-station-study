package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.execute.auto.step.Step2PrecisionExecutorNode;
import org.junit.Assert;
import org.junit.Test;

public class Step2PrecisionExecutorNodeTest {

    @Test
    public void test_buildExecutionPrompt_includesWorkspaceAndToolPathRule() {
        String prompt = Step2PrecisionExecutorNode.buildExecutionPrompt("帮我分析这个项目", "先读取代码结构");

        Assert.assertTrue(prompt.contains("E:\\javaProject\\ai-agent-station-study"));
        Assert.assertTrue(prompt.contains("search_files"));
        Assert.assertTrue(prompt.contains("必须显式传入 path"));
    }
}

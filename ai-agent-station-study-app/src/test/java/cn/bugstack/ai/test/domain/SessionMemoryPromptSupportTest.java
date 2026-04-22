package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.model.entity.SessionMemoryEntity;
import cn.bugstack.ai.domain.agent.service.execute.auto.support.SessionMemoryPromptSupport;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class SessionMemoryPromptSupportTest {

    @Test
    public void test_buildSessionHistoryPrompt_formatsChronologicalQaPairs() {
        List<SessionMemoryEntity> history = List.of(
                SessionMemoryEntity.builder()
                        .sessionId("sess-1")
                        .roundNo(1)
                        .userMessage("第一问")
                        .finalAnswer("第一答")
                        .build(),
                SessionMemoryEntity.builder()
                        .sessionId("sess-1")
                        .roundNo(2)
                        .userMessage("第二问")
                        .finalAnswer("第二答")
                        .build()
        );

        String prompt = SessionMemoryPromptSupport.buildSessionHistoryPrompt(history);

        Assert.assertTrue(prompt.contains("Session History:"));
        Assert.assertTrue(prompt.contains("[Round 1]"));
        Assert.assertTrue(prompt.contains("User: 第一问"));
        Assert.assertTrue(prompt.contains("Assistant: 第一答"));
        Assert.assertTrue(prompt.contains("[Round 2]"));
        Assert.assertTrue(prompt.contains("User: 第二问"));
        Assert.assertTrue(prompt.contains("Assistant: 第二答"));
    }

    @Test
    public void test_buildSessionHistoryPrompt_returnsEmptyWhenNoHistory() {
        Assert.assertEquals("", SessionMemoryPromptSupport.buildSessionHistoryPrompt(List.of()));
    }

    @Test
    public void test_extractLatestFinalAnswer_returnsNewestAnswerByRound() {
        List<SessionMemoryEntity> history = List.of(
                SessionMemoryEntity.builder()
                        .sessionId("sess-1")
                        .roundNo(2)
                        .finalAnswer("第二轮结果")
                        .build(),
                SessionMemoryEntity.builder()
                        .sessionId("sess-1")
                        .roundNo(1)
                        .finalAnswer("第一轮结果")
                        .build()
        );

        Assert.assertEquals("第二轮结果", SessionMemoryPromptSupport.extractLatestFinalAnswer(history));
    }
}

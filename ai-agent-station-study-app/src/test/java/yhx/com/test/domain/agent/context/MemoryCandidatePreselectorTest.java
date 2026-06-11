package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.service.memory.MemoryCandidatePreselector;

import java.math.BigDecimal;
import java.util.List;

public class MemoryCandidatePreselectorTest {

    @Test
    public void follow_up_question_selects_recent_topic_summary() {
        Assert.assertEquals(1, new MemoryCandidatePreselector().select("what about RAG", List.of(
                AgentMemoryEntity.builder().memoryId("m1").summary("RAG discussion summary").score(BigDecimal.ONE).build()), 5).size());
    }

    @Test
    public void irrelevant_memory_is_not_selected() {
        Assert.assertTrue(new MemoryCandidatePreselector().select("payment config", List.of(
                AgentMemoryEntity.builder().memoryId("m1").summary("weather topic").score(BigDecimal.ZERO).build()), 5).isEmpty());
    }

    @Test
    public void preference_memory_is_selected_when_user_request_depends_on_preference() {
        Assert.assertEquals(1, new MemoryCandidatePreselector().select("use my preference", List.of(
                AgentMemoryEntity.builder().memoryId("m1").summary("User preference: concise answers").score(BigDecimal.ZERO).build()), 5).size());
    }
}

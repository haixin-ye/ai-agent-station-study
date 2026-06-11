package yhx.com.test.domain.agent.migration;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.api.dto.agent.AgentMessageDTO;
import yhx.com.api.dto.agent.AgentUserVisibleEventDTO;

import java.nio.file.Files;

public class OldTraceOutputIsolationTest {

    @Test
    public void old_node_trace_cannot_be_final_response_source() throws Exception {
        String finalPersistence = Files.readString(MigrationTestSupport.projectRoot()
                .resolve("ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/finalresponse/FinalResponsePersistenceService.java"));

        Assert.assertFalse(finalPersistence.contains("node-trace"));
        Assert.assertFalse(finalPersistence.contains("Step1AnalyzerNode"));
        Assert.assertFalse(finalPersistence.contains("rawResult"));
    }

    @Test
    public void old_raw_result_is_not_mapped_to_assistant_message() {
        Assert.assertFalse(MigrationTestSupport.hasField(AgentMessageDTO.class, "rawResult"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentMessageDTO.class, "understanding"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentMessageDTO.class, "stepPlan"));
    }

    @Test
    public void old_node_json_is_not_emitted_as_user_visible_event() {
        Assert.assertFalse(MigrationTestSupport.hasField(AgentUserVisibleEventDTO.class, "rawResult"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentUserVisibleEventDTO.class, "todoList"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentUserVisibleEventDTO.class, "understanding"));
    }
}


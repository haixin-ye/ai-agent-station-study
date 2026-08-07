package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IRunContextRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunContextEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunLoopEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.context.UserInputVO;
import yhx.com.domain.agent.model.valobj.runtime.RunBaseContextVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.TaskLedgerVO;
import yhx.com.domain.agent.model.valobj.runtime.RunRuntimeControlVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeRecoveryCounters;
import yhx.com.domain.agent.service.runtime.RunContextStore;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RunContextStoreTest {

    @Test
    public void context_and_full_loop_records_round_trip_through_payload_references() {
        RuntimeTestSupport.InMemoryRuntimeRepository payloads = new RuntimeTestSupport.InMemoryRuntimeRepository();
        InMemoryContextRepository indexes = new InMemoryContextRepository();
        RunContextStore store = new RunContextStore(indexes, payloads);
        RunContextStateVO state = RunContextStateVO.builder()
                .mainAgentStage(MainAgentStageEnumVO.PLANNING)
                .baseContext(RunBaseContextVO.builder()
                        .runId("run-store")
                        .userInput("write a report")
                        .userClarifications(new ArrayList<>(List.of(
                                UserClarificationVO.builder()
                                        .pendingId("pending-store")
                                        .question("Which format?")
                                        .answerType("FREE_TEXT")
                                        .freeText("Markdown")
                                        .build())))
                        .build())
                .taskLedger(TaskLedgerVO.builder().version(1L).goal("write a report").build())
                .runtimeControl(RunRuntimeControlVO.builder().currentLoopIndex(0).maxLoop(10)
                        .recoveryCounters(RuntimeRecoveryCounters.initial()).build())
                .loopTimeline(new ArrayList<>())
                .build();

        store.initialize(state);
        RunLoopRecordVO loop = RunLoopRecordVO.builder()
                .runId("run-store")
                .loopIndex(0)
                .mainAgentStage(MainAgentStageEnumVO.PLANNING)
                .status("SUCCEEDED")
                .recordVersion(3L)
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        state.getLoopTimeline().add(loop);
        store.saveLoop(loop);
        state.setMainAgentStage(MainAgentStageEnumVO.EXECUTING);
        state.getBaseContext().setSelectedSessionContext(MainAgentStateViewVO.builder()
                .userInput(UserInputVO.builder().content("selected once").build())
                .build());
        state.getTaskLedger().setVersion(2L);
        store.saveContext(state);

        RunContextStateVO restored = store.load("run-store");

        Assert.assertEquals(MainAgentStageEnumVO.EXECUTING, restored.getMainAgentStage());
        Assert.assertEquals(Long.valueOf(2L), restored.getTaskLedger().getVersion());
        Assert.assertEquals("write a report", restored.getBaseContext().getUserInput());
        Assert.assertEquals("selected once", restored.getBaseContext().getSelectedSessionContext().getUserInput().getContent());
        Assert.assertEquals("Markdown",
                restored.getBaseContext().getUserClarifications().get(0).getFreeText());
        Assert.assertEquals(1, restored.getLoopTimeline().size());
        Assert.assertEquals("SUCCEEDED", restored.getLoopTimeline().get(0).getStatus());
    }

    private static class InMemoryContextRepository implements IRunContextRepository {
        private AgentRunContextEntity context;
        private final Map<Integer, AgentRunLoopEntity> loops = new LinkedHashMap<>();

        @Override
        public void createContext(AgentRunContextEntity context) {
            this.context = context;
        }

        @Override
        public boolean updateContext(AgentRunContextEntity context, long expectedVersion) {
            if (this.context == null || this.context.getContextVersion() != expectedVersion) {
                return false;
            }
            this.context = context;
            return true;
        }

        @Override
        public Optional<AgentRunContextEntity> findContext(String runId) {
            return Optional.ofNullable(context);
        }

        @Override
        public void saveLoop(AgentRunLoopEntity loop) {
            loops.put(loop.getLoopIndex(), loop);
        }

        @Override
        public Optional<AgentRunLoopEntity> findLoop(String runId, Integer loopIndex) {
            return Optional.ofNullable(loops.get(loopIndex));
        }

        @Override
        public List<AgentRunLoopEntity> listLoops(String runId) {
            return new ArrayList<>(loops.values());
        }
    }
}

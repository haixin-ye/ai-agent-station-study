package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.LoopRuntimeOutcomeVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.RunPayloadWorkingSetVO;
import yhx.com.domain.agent.service.runtime.RunPayloadProjectionPolicy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RunPayloadProjectionPolicyTest {

    @Test
    public void keeps_every_required_result_complete_and_falls_back_to_full_text_when_no_real_summary_exists() {
        CountingPayloadRepository repository = new CountingPayloadRepository(Map.of(
                "payload-tree", "x".repeat(10_000),
                "payload-file", "y".repeat(10_000)));
        RunContextStateVO state = RunContextStateVO.builder()
                .loopTimeline(List.of(
                        record(0, "payload-tree", "SUMMARY_ONLY", "Directory tree summary"),
                        record(1, "payload-file", "FULL_TEXT_REQUIRED", "File content")))
                .build();

        RunPayloadWorkingSetVO workingSet = new RunPayloadProjectionPolicy()
                .build(state, repository);

        Assert.assertEquals(2, workingSet.getPayloadManifest().size());
        Assert.assertEquals(2, repository.readCount);
        Map<?, ?> tree = (Map<?, ?>) workingSet.getActivePayloads().get("payload-tree");
        Assert.assertEquals("FULL_TEXT", tree.get("materialization"));
        Assert.assertEquals("FULL_TEXT", tree.get("summaryUnavailableFallback"));
        Assert.assertEquals(10_000, ((String) tree.get("content")).length());
        Map<?, ?> file = (Map<?, ?>) workingSet.getActivePayloads().get("payload-file");
        Assert.assertEquals("FULL_TEXT", file.get("materialization"));
        Assert.assertEquals(10_000, ((String) file.get("content")).length());
    }

    private RunLoopRecordVO record(int loopIndex,
                                   String payloadRef,
                                   String contentMode,
                                   String summary) {
        return RunLoopRecordVO.builder()
                .loopIndex(loopIndex)
                .mainOutput(MainAgentActionVO.builder().action("CALL_TOOL").build())
                .runtimeOutcome(LoopRuntimeOutcomeVO.builder()
                        .status("CONTINUE_LOOP")
                        .summary(summary)
                        .resultPayloadRef(payloadRef)
                        .details(Map.of("resultMetadata", Map.of(
                                "contentMode", contentMode,
                                "contentFormat", "TEXT",
                                "totalChars", 10_000)))
                        .build())
                .build();
    }

    private static class CountingPayloadRepository implements IPayloadRepository {
        private final Map<String, String> contents;
        private int readCount;

        private CountingPayloadRepository(Map<String, String> contents) {
            this.contents = contents;
        }

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            return payload == null ? null : payload.getPayloadId();
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            readCount++;
            String content = contents.get(payloadId);
            return content == null ? Optional.empty() : Optional.of(AgentPayloadEntity.builder()
                    .payloadId(payloadId)
                    .content(content)
                    .build());
        }
    }
}

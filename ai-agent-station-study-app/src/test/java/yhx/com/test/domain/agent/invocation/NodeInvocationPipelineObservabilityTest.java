package yhx.com.test.domain.agent.invocation;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunAuditEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.test.domain.agent.invocation.support.FakeNodeClientPort;
import yhx.com.test.domain.agent.invocation.support.InMemoryPromptContentProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NodeInvocationPipelineObservabilityTest {

    @Test
    public void main_node_attempt_persists_full_input_and_output_payloads() {
        TraceRepository traces = new TraceRepository();
        PayloadRepository payloads = new PayloadRepository();
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{\"taskUpdate\":{\"lastDecision\":\"ready\"},\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"ok\"}}}");

        NodeInvocationCommand command = NodeInvocationCommand.builder()
                .runId("run-1")
                .agentId("agent-1")
                .componentCode(AgentComponentCodeEnumVO.MAIN_AGENT.name())
                .contractVersion("main-agent-action-v2")
                .promptVersion("v1")
                .modelCode("fake")
                .inputView(Map.of("stateView", Map.of("memory", List.of(Map.of("id", "m-1")))))
                .invocationMetadata(Map.of("loopIndex", 3, "source", "dev"))
                .build();

        NodeInvocationResult result = new NodeInvocationPipeline(
                new PromptAssembler(new InMemoryPromptContentProvider()),
                client,
                null,
                new DeveloperTraceRecorder(traces, payloads))
                .invoke(command);

        Assert.assertNotNull(result);
        Map<String, Object> input = traces.payloadsByTraceType(payloads, "NODE_INPUT").get(0);
        Map<String, Object> output = traces.payloadsByTraceType(payloads, "NODE_OUTPUT").get(0);
        Assert.assertEquals(3, ((Number) input.get("loopIndex")).intValue());
        Assert.assertEquals("m-1", ((Map<?, ?>) ((Map<?, ?>) input.get("inputView")).get("stateView"))
                .get("memory") instanceof List ? ((Map<?, ?>) ((List<?>) ((Map<?, ?>) ((Map<?, ?>) input.get("inputView")).get("stateView")).get("memory")).get(0)).get("id") : null);
        Assert.assertTrue(String.valueOf(input.get("prompt")).contains("stateView"));
        Assert.assertTrue(String.valueOf(output.get("rawOutput")).contains("FINAL"));
        Assert.assertNotNull(output.get("parseResult"));
        Assert.assertNotNull(output.get("validationResult"));
        Assert.assertEquals(Boolean.TRUE, output.get("success"));
    }

    private static class TraceRepository implements IEventTraceRepository {
        private final List<AgentRunTraceEntity> traces = new ArrayList<>();

        @Override public void appendUserVisibleEvent(AgentRunEventEntity event) { }
        @Override public void appendTrace(AgentRunTraceEntity trace) { traces.add(trace); }
        @Override public void appendAudit(AgentRunAuditEntity audit) { }
        @Override public List<AgentRunEventEntity> listUserVisibleEvents(String runId, int limit) { return List.of(); }
        @Override public List<AgentRunTraceEntity> listDebugTrace(String runId, int limit) { return traces; }

        List<Map<String, Object>> payloadsByTraceType(PayloadRepository payloads, String traceType) {
            return traces.stream()
                    .filter(trace -> traceType.equals(trace.getTraceType().code()))
                    .map(trace -> (Map<String, Object>) (Map<?, ?>) JSON.parseObject(payloads.findPayload(trace.getPayloadRef()).orElseThrow().getContent(), Map.class))
                    .toList();
        }
    }

    private static class PayloadRepository implements IPayloadRepository {
        private final Map<String, AgentPayloadEntity> values = new LinkedHashMap<>();
        private int counter;

        @Override public String savePayload(AgentPayloadEntity payload) {
            String id = "payload-" + (++counter);
            payload.setPayloadId(id);
            values.put(id, payload);
            return id;
        }

        @Override public Optional<AgentPayloadEntity> findPayload(String payloadId) { return Optional.ofNullable(values.get(payloadId)); }
    }
}

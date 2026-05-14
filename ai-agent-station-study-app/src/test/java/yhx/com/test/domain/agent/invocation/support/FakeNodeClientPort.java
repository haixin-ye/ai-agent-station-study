package yhx.com.test.domain.agent.invocation.support;

import yhx.com.domain.agent.adapter.port.INodeClientPort;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class FakeNodeClientPort implements INodeClientPort {

    private final Queue<String> rawOutputs = new ArrayDeque<>();
    private final List<NodeClientRequest> requests = new ArrayList<>();

    public FakeNodeClientPort enqueue(String rawOutput) {
        rawOutputs.add(rawOutput);
        return this;
    }

    public List<NodeClientRequest> requests() {
        return requests;
    }

    @Override
    public NodeClientResponse call(NodeClientRequest request) {
        requests.add(request);
        return NodeClientResponse.builder()
                .rawOutput(rawOutputs.poll())
                .modelName("fake")
                .build();
    }
}

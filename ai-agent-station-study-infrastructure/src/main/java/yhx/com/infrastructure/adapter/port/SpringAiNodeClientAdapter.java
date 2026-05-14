package yhx.com.infrastructure.adapter.port;

import org.springframework.stereotype.Component;
import yhx.com.domain.agent.adapter.port.INodeClientPort;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientResponse;

@Component
public class SpringAiNodeClientAdapter implements INodeClientPort {

    @Override
    public NodeClientResponse call(NodeClientRequest request) {
        throw new UnsupportedOperationException("Spring AI node client is not wired yet");
    }
}

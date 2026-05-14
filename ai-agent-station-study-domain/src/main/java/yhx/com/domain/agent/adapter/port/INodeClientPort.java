package yhx.com.domain.agent.adapter.port;

import yhx.com.domain.agent.service.invocation.NodeClientRequest;
import yhx.com.domain.agent.service.invocation.NodeClientResponse;

public interface INodeClientPort {

    NodeClientResponse call(NodeClientRequest request);
}

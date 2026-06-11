package yhx.com.domain.agent.adapter.port;

import yhx.com.domain.agent.model.valobj.invocation.NodeClientRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientResponse;

public interface INodeClientPort {

    NodeClientResponse call(NodeClientRequest request);
}

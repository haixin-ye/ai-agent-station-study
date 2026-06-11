package yhx.com.domain.agent.service.runtime.port;

import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeResultVO;

public interface RagRuntimePort {

    RagRuntimeResultVO retrieve(RagRuntimeCommandVO command);
}

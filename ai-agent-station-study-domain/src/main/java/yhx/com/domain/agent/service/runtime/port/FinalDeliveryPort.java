package yhx.com.domain.agent.service.runtime.port;

import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryResultVO;

public interface FinalDeliveryPort {

    FinalDeliveryResultVO deliver(FinalDeliveryCommandVO command);
}

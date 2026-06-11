package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextVO;

import java.util.Optional;

public interface SubAgentFullContextStore {

    String save(SubAgentFullContextVO context);

    Optional<SubAgentFullContextVO> load(String snapshotRef);
}

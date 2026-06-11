package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentContinuationVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRegistrySnapshotVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;

import java.util.List;
import java.util.Optional;

public interface ParentChildRunRegistryStore {

    void saveParent(String parentRunId,
                    List<ParentChildRunRelationVO> relations,
                    List<GenericSubAgentContinuationVO> continuations);

    Optional<ParentChildRunRegistrySnapshotVO> loadParent(String parentRunId);
}

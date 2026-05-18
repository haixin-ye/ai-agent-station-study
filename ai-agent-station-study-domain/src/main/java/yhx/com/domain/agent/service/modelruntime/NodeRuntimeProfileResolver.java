package yhx.com.domain.agent.service.modelruntime;

import yhx.com.domain.agent.adapter.repository.IModelRuntimeRepository;
import yhx.com.domain.agent.model.entity.modelruntime.AgentNodeModelBindingEntity;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NodeRuntimeProfileResolver {

    private final IModelRuntimeRepository modelRuntimeRepository;

    public NodeRuntimeProfileResolver(IModelRuntimeRepository modelRuntimeRepository) {
        this.modelRuntimeRepository = modelRuntimeRepository;
    }

    public Map<String, NodeInvocationProfileVO> resolveAllActive() {
        List<AgentNodeModelBindingEntity> bindings = modelRuntimeRepository.listActiveBindings();
        return bindings.stream().collect(Collectors.toMap(
                AgentNodeModelBindingEntity::getNodeCode,
                this::toProfile,
                (first, ignored) -> first
        ));
    }

    public NodeInvocationProfileVO resolveRequired(String nodeCode) {
        return modelRuntimeRepository.findActiveBindingByNodeCode(nodeCode)
                .map(this::toProfile)
                .orElseThrow(() -> new IllegalStateException("Missing active node model binding: " + nodeCode));
    }

    private NodeInvocationProfileVO toProfile(AgentNodeModelBindingEntity binding) {
        return NodeInvocationProfileVO.builder()
                .componentCode(binding.getNodeCode())
                .modelCode(binding.getModelProfileId())
                .promptVersion(binding.getPromptVersion())
                .contractVersion(binding.getContractVersion())
                .temperature(binding.getTemperature())
                .maxOutputTokens(binding.getMaxOutputTokens())
                .maxRepairAttempts(binding.getMaxRepairAttempts())
                .build();
    }
}

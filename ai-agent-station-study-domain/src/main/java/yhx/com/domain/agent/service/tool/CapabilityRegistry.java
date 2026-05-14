package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CapabilityRegistry {

    private final Map<String, CapabilitySpecVO> capabilities = new ConcurrentHashMap<>();

    public CapabilityRegistry(List<CapabilitySpecVO> capabilitySpecs) {
        if (capabilitySpecs != null) {
            capabilitySpecs.forEach(spec -> {
                if (spec != null && spec.getCapabilityCode() != null) {
                    capabilities.put(spec.getCapabilityCode(), spec);
                }
            });
        }
    }

    public Optional<CapabilitySpecVO> findCapability(String capabilityCode) {
        CapabilitySpecVO spec = capabilities.get(capabilityCode);
        if (spec == null || !Boolean.TRUE.equals(spec.getEnabled())) {
            return Optional.empty();
        }
        return Optional.of(spec);
    }

    public CapabilitySpecVO requireCapability(String capabilityCode) {
        return findCapability(capabilityCode)
                .orElseThrow(() -> new IllegalArgumentException("Capability is missing or disabled: " + capabilityCode));
    }

    public List<CapabilitySpecVO> listEnabledCapabilities() {
        return capabilities.values().stream()
                .filter(spec -> Boolean.TRUE.equals(spec.getEnabled()))
                .toList();
    }
}

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

    public Optional<CapabilitySpecVO> findUniqueCapabilityByTool(String mcpServerCode, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        List<CapabilitySpecVO> matches = capabilities.values().stream()
                .filter(spec -> Boolean.TRUE.equals(spec.getEnabled()))
                .filter(spec -> toolName.equals(spec.getToolName()))
                .filter(spec -> mcpServerCode == null || mcpServerCode.isBlank() || mcpServerCode.equals(spec.getMcpServerCode()))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    public Optional<CapabilitySpecVO> findCapabilityByAbstractGrant(String abstractCapabilityCode, String mcpServerCode, String toolName) {
        if (abstractCapabilityCode == null || abstractCapabilityCode.isBlank() || toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        if (!"FILE_READ".equals(abstractCapabilityCode) && !"FILE_WRITE".equals(abstractCapabilityCode) && !"MCP_TOOL".equals(abstractCapabilityCode)) {
            return Optional.empty();
        }
        List<CapabilitySpecVO> matches = capabilities.values().stream()
                .filter(spec -> Boolean.TRUE.equals(spec.getEnabled()))
                .filter(spec -> toolName.equals(spec.getToolName()))
                .filter(spec -> mcpServerCode == null || mcpServerCode.isBlank() || mcpServerCode.equals(spec.getMcpServerCode()))
                .filter(spec -> matchesAbstractGrant(abstractCapabilityCode, spec))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private boolean matchesAbstractGrant(String abstractCapabilityCode, CapabilitySpecVO spec) {
        if ("MCP_TOOL".equals(abstractCapabilityCode)) {
            return true;
        }
        String capabilityCode = spec == null ? null : spec.getCapabilityCode();
        if (capabilityCode == null) {
            return false;
        }
        if ("FILE_READ".equals(abstractCapabilityCode)) {
            return capabilityCode.startsWith("file_system_read")
                    || "file_system_list_directory".equals(capabilityCode)
                    || "file_system_directory_tree".equals(capabilityCode)
                    || "file_system_search_files".equals(capabilityCode)
                    || "file_system_get_file_info".equals(capabilityCode)
                    || "file_system_list_allowed_directories".equals(capabilityCode);
        }
        if ("FILE_WRITE".equals(abstractCapabilityCode)) {
            return capabilityCode.startsWith("file_system_write")
                    || "file_system_create_file".equals(capabilityCode)
                    || "file_system_edit_file".equals(capabilityCode)
                    || "file_system_create_directory".equals(capabilityCode)
                    || "file_system_move_file".equals(capabilityCode);
        }
        return false;
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

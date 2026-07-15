package yhx.com.domain.agent.service.context;

import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ToolCapabilityExposurePolicyVO;
import yhx.com.domain.agent.model.valobj.enums.tool.McpToolAvailabilityEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.service.tool.McpToolRegistry;
import yhx.com.domain.agent.service.tool.ToolSchemaCanonicalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ToolCapabilityCandidateProjector {

    private static final Set<String> SIMPLE_KEYS = Set.of("type", "enum", "additionalProperties", "const", "minimum",
            "maximum", "minLength", "maxLength", "minItems", "maxItems", "pattern");
    private static final int MAX_COMPOSITION_BRANCHES = 4;

    private final ToolSchemaCanonicalizer canonicalizer;

    public ToolCapabilityCandidateProjector() {
        this(new ToolSchemaCanonicalizer());
    }

    public ToolCapabilityCandidateProjector(ToolSchemaCanonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public List<CapabilityCandidateVO> projectAll(List<CapabilitySpecVO> capabilities,
                                                  McpToolRegistry toolRegistry,
                                                  ToolCapabilityExposurePolicyVO policy) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        ToolCapabilityExposurePolicyVO effective = effectivePolicy(policy);
        List<CapabilitySpecVO> ordered = capabilities.stream()
                .filter(item -> item != null && Boolean.TRUE.equals(item.getEnabled()))
                .sorted(Comparator.comparing(CapabilitySpecVO::getCapabilityCode,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
        List<CapabilityCandidateVO> projected = new ArrayList<>();
        int remainingSchemaChars = effective.getMaxTotalSchemaChars();
        int remainingCapabilityChars = effective.getMaxTotalCapabilityChars();
        for (CapabilitySpecVO capability : ordered) {
            if (projected.size() >= effective.getMaxTools()) {
                break;
            }
            if (!isMcpToolCapability(capability)) {
                CapabilityCandidateVO candidate = projectGovernanceOnly(capability);
                int candidateChars = candidateChars(candidate);
                if (candidateChars <= effective.getMaxCapabilityCharsPerTool()
                        && candidateChars <= remainingCapabilityChars) {
                    projected.add(candidate);
                    remainingCapabilityChars -= candidateChars;
                }
                continue;
            }
            McpToolSpecVO tool = toolRegistry == null
                    ? null
                    : toolRegistry.findTool(capability.getMcpServerCode(), capability.getToolName()).orElse(null);
            if (!usable(tool)) {
                log.warn("[AutoAgent][CAPABILITY_SCHEMA_MISSING] serverId={}, toolName={}, capabilityCode={}",
                        capability.getMcpServerCode(), capability.getToolName(), capability.getCapabilityCode());
                continue;
            }
            if (tool.getAvailability() != McpToolAvailabilityEnumVO.AVAILABLE) {
                log.warn("[AutoAgent][CAPABILITY_TOOL_UNAVAILABLE] serverId={}, toolName={}, capabilityCode={}, availability={}",
                        capability.getMcpServerCode(), capability.getToolName(), capability.getCapabilityCode(),
                        tool.getAvailability());
                continue;
            }
            List<String> requiredPaths = requiredPaths(tool.getInputSchema() == null ? Map.of() : tool.getInputSchema());
            if (requiredPaths.size() > effective.getMaxRequiredArgumentsPerTool()) {
                log.warn("[AutoAgent][CAPABILITY_REQUIRED_ARGUMENTS_OVER_BUDGET] serverId={}, toolName={}, capabilityCode={}, requiredCount={}",
                        capability.getMcpServerCode(), capability.getToolName(), capability.getCapabilityCode(), requiredPaths.size());
                continue;
            }
            if (remainingSchemaChars <= 0) {
                continue;
            }
            int toolBudget = Math.min(effective.getMaxSchemaCharsPerTool(), remainingSchemaChars);
            CapabilityCandidateVO candidate = project(capability, tool, withSchemaBudget(effective, toolBudget));
            int schemaChars = schemaChars(candidate.getInputSchema());
            if (schemaChars > remainingSchemaChars) {
                continue;
            }
            int candidateChars = candidateChars(candidate);
            if (candidateChars > effective.getMaxCapabilityCharsPerTool()
                    || candidateChars > remainingCapabilityChars) {
                log.warn("[AutoAgent][CAPABILITY_METADATA_OVER_BUDGET] serverId={}, toolName={}, capabilityCode={}, chars={}",
                        capability.getMcpServerCode(), capability.getToolName(), capability.getCapabilityCode(), candidateChars);
                continue;
            }
            projected.add(candidate);
            remainingSchemaChars -= schemaChars;
            remainingCapabilityChars -= candidateChars;
        }
        return List.copyOf(projected);
    }

    public CapabilityCandidateVO project(CapabilitySpecVO capability,
                                         McpToolSpecVO tool,
                                         ToolCapabilityExposurePolicyVO policy) {
        if (capability == null) {
            throw new IllegalArgumentException("Capability is required.");
        }
        if (!usable(tool)) {
            throw new IllegalArgumentException("MCP tool metadata or input schema is unavailable.");
        }
        ToolCapabilityExposurePolicyVO effective = effectivePolicy(policy);
        Map<String, Object> fullSchema = tool.getInputSchema() == null ? Map.of() : tool.getInputSchema();
        List<String> requiredPaths = requiredPaths(fullSchema);
        ProjectionState state = new ProjectionState(effective.getMaxSchemaPropertiesPerTool());
        Map<String, Object> schemaProjection = fullSchema.isEmpty()
                ? Map.of("type", "object")
                : projectSchema(fullSchema, 0, effective, state);
        Map<String, Object> boundedSchema = fitSchema(schemaProjection, fullSchema, requiredPaths,
                effective.getMaxSchemaCharsPerTool(), state);
        String description = sanitize(tool.getDescription(), effective.getMaxDescriptionChars());
        String schemaHash = canonicalizer.schemaHash(fullSchema);
        String summary = genericSummary(capability);
        if (state.truncated) {
            log.info("[AutoAgent][CAPABILITY_SCHEMA_TRUNCATED] serverId={}, toolName={}, capabilityCode={}, schemaHash={}",
                    capability.getMcpServerCode(), capability.getToolName(), capability.getCapabilityCode(), schemaHash);
        }
        log.debug("[AutoAgent][CAPABILITY_PROJECTED_TO_STATE_VIEW] serverId={}, toolName={}, capabilityCode={}, schemaHash={}",
                capability.getMcpServerCode(), capability.getToolName(), capability.getCapabilityCode(), schemaHash);
        return CapabilityCandidateVO.builder()
                .capabilityCode(capability.getCapabilityCode())
                .capabilityType(capability.getCapabilityType())
                .mcpServerCode(capability.getMcpServerCode())
                .toolName(tool.getToolName())
                .description(description)
                .requiredArguments(requiredPaths)
                .inputSchema(boundedSchema)
                .schemaHash(schemaHash)
                .schemaTruncated(state.truncated)
                .requiredPermission(capability.getRequiredPermission() == null
                        ? "NONE" : capability.getRequiredPermission().code())
                .approvalPolicy(capability.getApprovalPolicy() == null
                        ? "NEVER" : capability.getApprovalPolicy().code())
                .riskLevel(capability.getRiskLevel())
                .availability(tool.getAvailability().name())
                .summary(summary)
                .enabled(capability.getEnabled())
                .build();
    }

    private CapabilityCandidateVO projectGovernanceOnly(CapabilitySpecVO capability) {
        return CapabilityCandidateVO.builder()
                .capabilityCode(capability.getCapabilityCode())
                .capabilityType(capability.getCapabilityType())
                .mcpServerCode(capability.getMcpServerCode())
                .toolName(capability.getToolName())
                .requiredPermission(capability.getRequiredPermission() == null
                        ? "NONE" : capability.getRequiredPermission().code())
                .approvalPolicy(capability.getApprovalPolicy() == null
                        ? "NEVER" : capability.getApprovalPolicy().code())
                .riskLevel(capability.getRiskLevel())
                .summary(genericSummary(capability))
                .enabled(capability.getEnabled())
                .build();
    }

    private Map<String, Object> projectSchema(Map<String, Object> schema,
                                              int depth,
                                              ToolCapabilityExposurePolicyVO policy,
                                              ProjectionState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        SIMPLE_KEYS.forEach(key -> copyIfPresent(schema, result, key));
        if (schema.get("description") != null) {
            result.put("description", sanitize(String.valueOf(schema.get("description")), policy.getMaxDescriptionChars()));
        }
        List<String> required = stringList(schema.get("required"));
        if (!required.isEmpty()) {
            result.put("required", required);
        }
        if (depth >= policy.getMaxSchemaDepth()) {
            Map<String, Object> requiredSkeleton = requiredOnlySchema(schema, new RequiredSkeletonGuard());
            requiredSkeleton.forEach(result::putIfAbsent);
            if (schema.containsKey("properties") || schema.containsKey("items")) {
                state.truncated = true;
            }
            return result;
        }
        Object propertiesValue = schema.get("properties");
        if (propertiesValue instanceof Map<?, ?> rawProperties) {
            Map<String, Object> properties = stringObjectMap(rawProperties);
            List<String> names = orderedPropertyNames(properties.keySet(), required);
            Map<String, Object> projectedProperties = new LinkedHashMap<>();
            for (String name : names) {
                boolean requiredProperty = required.contains(name);
                if (!requiredProperty && state.propertyCount >= state.maxProperties) {
                    state.truncated = true;
                    continue;
                }
                Object propertySchema = properties.get(name);
                if (propertySchema instanceof Map<?, ?> propertyMap) {
                    projectedProperties.put(name, projectSchema(stringObjectMap(propertyMap), depth + 1, policy, state));
                    state.propertyCount++;
                }
            }
            if (!projectedProperties.isEmpty()) {
                result.put("properties", projectedProperties);
            }
        }
        if (schema.get("items") instanceof Map<?, ?> items) {
            result.put("items", projectSchema(stringObjectMap(items), depth + 1, policy, state));
        }
        copyComposition(schema, result, "oneOf", depth, policy, state);
        copyComposition(schema, result, "anyOf", depth, policy, state);
        return result;
    }

    private void copyComposition(Map<String, Object> schema,
                                 Map<String, Object> result,
                                 String key,
                                 int depth,
                                 ToolCapabilityExposurePolicyVO policy,
                                 ProjectionState state) {
        if (!(schema.get(key) instanceof Collection<?> branches)) {
            return;
        }
        List<Object> projected = new ArrayList<>();
        int count = 0;
        for (Object branch : branches) {
            if (count >= MAX_COMPOSITION_BRANCHES) {
                state.truncated = true;
                break;
            }
            if (branch instanceof Map<?, ?> branchMap) {
                projected.add(projectSchema(stringObjectMap(branchMap), depth + 1, policy, state));
                count++;
            }
        }
        if (!projected.isEmpty()) {
            result.put(key, projected);
        }
    }

    private Map<String, Object> fitSchema(Map<String, Object> projection,
                                          Map<String, Object> fullSchema,
                                          List<String> requiredPaths,
                                          int maxChars,
                                          ProjectionState state) {
        Map<String, Object> bounded = deepCopy(projection);
        if (schemaChars(bounded) <= maxChars) {
            return bounded;
        }
        state.truncated = true;
        removeDescriptions(bounded);
        while (schemaChars(bounded) > maxChars && removeOneOptionalProperty(bounded)) {
            // Remove least important optional leaves until the configured budget is met.
        }
        if (schemaChars(bounded) <= maxChars) {
            return bounded;
        }
        bounded = requiredOnlySchema(fullSchema, new RequiredSkeletonGuard());
        if (schemaChars(bounded) <= maxChars) {
            return bounded;
        }
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("type", "object");
        compact.put("requiredPaths", requiredPaths);
        if (schemaChars(compact) <= maxChars) {
            return compact;
        }
        return Map.of("type", "object", "schemaTruncated", true);
    }

    private Map<String, Object> requiredOnlySchema(Map<String, Object> schema, RequiredSkeletonGuard guard) {
        Map<String, Object> result = new LinkedHashMap<>();
        copyIfPresent(schema, result, "type");
        copyIfPresent(schema, result, "enum");
        copyIfPresent(schema, result, "additionalProperties");
        List<String> required = stringList(schema.get("required"));
        if (!required.isEmpty()) {
            result.put("required", required);
        }
        if (guard.depth++ >= 32) {
            guard.depth--;
            return result;
        }
        if (schema.get("properties") instanceof Map<?, ?> rawProperties && !required.isEmpty()) {
            Map<String, Object> source = stringObjectMap(rawProperties);
            Map<String, Object> properties = new LinkedHashMap<>();
            for (String name : required) {
                if (source.get(name) instanceof Map<?, ?> child) {
                    properties.put(name, requiredOnlySchema(stringObjectMap(child), guard));
                }
            }
            if (!properties.isEmpty()) {
                result.put("properties", properties);
            }
        }
        if (schema.get("items") instanceof Map<?, ?> items) {
            result.put("items", requiredOnlySchema(stringObjectMap(items), guard));
        }
        guard.depth--;
        return result;
    }

    private List<String> requiredPaths(Map<String, Object> schema) {
        Set<String> paths = new LinkedHashSet<>();
        collectRequiredPaths(schema, "", paths, new RequiredSkeletonGuard());
        return List.copyOf(paths);
    }

    private void collectRequiredPaths(Map<String, Object> schema,
                                      String parent,
                                      Set<String> paths,
                                      RequiredSkeletonGuard guard) {
        if (guard.depth++ >= 32) {
            guard.depth--;
            return;
        }
        List<String> required = stringList(schema.get("required"));
        for (String name : required) {
            paths.add(parent.isBlank() ? name : parent + "." + name);
        }
        if (schema.get("properties") instanceof Map<?, ?> rawProperties) {
            Map<String, Object> properties = stringObjectMap(rawProperties);
            for (String name : required) {
                Object property = properties.get(name);
                if (property instanceof Map<?, ?> child) {
                    String childPath = parent.isBlank() ? name : parent + "." + name;
                    collectRequiredPaths(stringObjectMap(child), childPath, paths, guard);
                }
            }
        }
        if (schema.get("items") instanceof Map<?, ?> items) {
            collectRequiredPaths(stringObjectMap(items), parent + "[]", paths, guard);
        }
        guard.depth--;
    }

    private boolean removeOneOptionalProperty(Map<String, Object> schema) {
        if (schema.get("properties") instanceof Map<?, ?> rawProperties) {
            Map<String, Object> properties = stringObjectMap(rawProperties);
            List<String> required = stringList(schema.get("required"));
            List<String> names = new ArrayList<>(properties.keySet());
            names.sort(Comparator.reverseOrder());
            for (String name : names) {
                Object child = properties.get(name);
                if (child instanceof Map<?, ?> childMap && removeOneOptionalProperty(stringObjectMapInPlace(childMap))) {
                    return true;
                }
                if (!required.contains(name)) {
                    stringObjectMapInPlace(rawProperties).remove(name);
                    return true;
                }
            }
        }
        if (schema.get("items") instanceof Map<?, ?> items) {
            return removeOneOptionalProperty(stringObjectMapInPlace(items));
        }
        return false;
    }

    private void removeDescriptions(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = stringObjectMapInPlace(rawMap);
            map.remove("description");
            map.values().forEach(this::removeDescriptions);
        } else if (value instanceof Collection<?> values) {
            values.forEach(this::removeDescriptions);
        }
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        return stringObjectMap(castMap(canonicalizer.normalize(source)));
    }

    private String genericSummary(CapabilitySpecVO capability) {
        return String.format("tool=%s, permission=%s, approval=%s, risk=%s",
                capability.getToolName(),
                capability.getRequiredPermission() == null ? "NONE" : capability.getRequiredPermission().code(),
                capability.getApprovalPolicy() == null ? "NEVER" : capability.getApprovalPolicy().code(),
                capability.getRiskLevel());
    }

    private boolean usable(McpToolSpecVO tool) {
        return tool != null && tool.getToolName() != null && !tool.getToolName().isBlank()
                && ((tool.getInputSchema() != null && !tool.getInputSchema().isEmpty())
                || Boolean.TRUE.equals(tool.getSchemaLessAllowed()));
    }

    private boolean isMcpToolCapability(CapabilitySpecVO capability) {
        return "TOOL".equalsIgnoreCase(capability.getCapabilityType())
                && capability.getMcpServerCode() != null && !capability.getMcpServerCode().isBlank();
    }

    private ToolCapabilityExposurePolicyVO effectivePolicy(ToolCapabilityExposurePolicyVO policy) {
        return policy == null ? ToolCapabilityExposurePolicyVO.builder().build() : policy;
    }

    private ToolCapabilityExposurePolicyVO withSchemaBudget(ToolCapabilityExposurePolicyVO source, int schemaBudget) {
        return ToolCapabilityExposurePolicyVO.builder()
                .maxTools(source.getMaxTools())
                .maxDescriptionChars(source.getMaxDescriptionChars())
                .maxSchemaDepth(source.getMaxSchemaDepth())
                .maxSchemaPropertiesPerTool(source.getMaxSchemaPropertiesPerTool())
                .maxSchemaCharsPerTool(Math.max(64, schemaBudget))
                .maxTotalSchemaChars(source.getMaxTotalSchemaChars())
                .maxRequiredArgumentsPerTool(source.getMaxRequiredArgumentsPerTool())
                .maxCapabilityCharsPerTool(source.getMaxCapabilityCharsPerTool())
                .maxTotalCapabilityChars(source.getMaxTotalCapabilityChars())
                .build();
    }

    private int candidateChars(CapabilityCandidateVO candidate) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("capabilityCode", candidate.getCapabilityCode());
        view.put("capabilityType", candidate.getCapabilityType());
        view.put("mcpServerCode", candidate.getMcpServerCode());
        view.put("toolName", candidate.getToolName());
        view.put("description", candidate.getDescription());
        view.put("requiredArguments", candidate.getRequiredArguments());
        view.put("inputSchema", candidate.getInputSchema());
        view.put("schemaHash", candidate.getSchemaHash());
        view.put("schemaTruncated", candidate.getSchemaTruncated());
        view.put("requiredPermission", candidate.getRequiredPermission());
        view.put("approvalPolicy", candidate.getApprovalPolicy());
        view.put("riskLevel", candidate.getRiskLevel());
        view.put("availability", candidate.getAvailability());
        view.put("summary", candidate.getSummary());
        view.put("enabled", candidate.getEnabled());
        return canonicalizer.canonicalJson(view).length();
    }

    private int schemaChars(Map<String, Object> schema) {
        return schema == null ? 0 : canonicalizer.canonicalJson(schema).length();
    }

    private String sanitize(String value, int limit) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ").trim();
        if (sanitized.length() <= limit) {
            return sanitized;
        }
        return sanitized.substring(0, Math.max(0, limit));
    }

    private List<String> orderedPropertyNames(Set<String> names, List<String> required) {
        List<String> ordered = new ArrayList<>();
        required.stream().filter(names::contains).forEach(ordered::add);
        names.stream().filter(name -> !required.contains(name)).sorted().forEach(ordered::add);
        return ordered;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stringObjectMapInPlace(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> castMap(Object value) {
        return (Map<?, ?>) value;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private static final class ProjectionState {
        private final int maxProperties;
        private int propertyCount;
        private boolean truncated;

        private ProjectionState(int maxProperties) {
            this.maxProperties = Math.max(1, maxProperties);
        }
    }

    private static final class RequiredSkeletonGuard {
        private int depth;
    }
}

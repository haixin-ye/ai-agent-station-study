package yhx.com.domain.agent.service.tool;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import yhx.com.domain.agent.model.valobj.tool.ToolSchemaValidationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolSchemaViolationVO;

import java.util.List;
import java.util.Map;

public class NetworkntToolArgumentSchemaValidator implements ToolArgumentSchemaValidator {

    private static final int MAX_VIOLATIONS = 12;
    private static final int MAX_PATH_CHARS = 300;
    private static final int MAX_KEYWORD_CHARS = 80;
    private static final int MAX_CONSTRAINT_CHARS = 180;
    private static final int MAX_MESSAGE_CHARS = 300;

    private final ToolSchemaCanonicalizer canonicalizer;
    private final SchemaRegistry schemaRegistry;

    public NetworkntToolArgumentSchemaValidator() {
        this(new ToolSchemaCanonicalizer(),
                SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12));
    }

    public NetworkntToolArgumentSchemaValidator(ToolSchemaCanonicalizer canonicalizer,
                                                SchemaRegistry schemaRegistry) {
        this.canonicalizer = canonicalizer;
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public ToolSchemaValidationResultVO validate(Map<String, Object> schema, Map<String, Object> arguments) {
        Map<String, Object> effectiveSchema = schema == null ? Map.of() : schema;
        String schemaHash = canonicalizer.schemaHash(effectiveSchema);
        if (effectiveSchema.isEmpty()) {
            return valid(schemaHash);
        }
        try {
            Schema compiled = schemaRegistry.getSchema(canonicalizer.canonicalJson(effectiveSchema), InputFormat.JSON);
            List<com.networknt.schema.Error> errors = compiled.validate(
                    canonicalizer.canonicalJson(arguments == null ? Map.of() : arguments), InputFormat.JSON);
            if (errors == null || errors.isEmpty()) {
                return valid(schemaHash);
            }
            List<ToolSchemaViolationVO> violations = errors.stream()
                    .limit(MAX_VIOLATIONS)
                    .map(this::violation)
                    .toList();
            return ToolSchemaValidationResultVO.builder()
                    .valid(false)
                    .schemaHash(schemaHash)
                    .violations(violations)
                    .safeMessage(safeMessage(schemaHash, violations))
                    .build();
        } catch (RuntimeException e) {
            ToolSchemaViolationVO violation = ToolSchemaViolationVO.builder()
                    .path("$")
                    .keyword("schema")
                    .expectedConstraint("valid JSON Schema")
                    .actualType("SCHEMA")
                    .message("Tool schema could not be evaluated.")
                    .build();
            return ToolSchemaValidationResultVO.builder()
                    .valid(false)
                    .schemaHash(schemaHash)
                    .violations(List.of(violation))
                    .safeMessage(safeMessage(schemaHash, List.of(violation)))
                    .build();
        }
    }

    private ToolSchemaValidationResultVO valid(String schemaHash) {
        return ToolSchemaValidationResultVO.builder()
                .valid(true)
                .schemaHash(schemaHash)
                .violations(List.of())
                .build();
    }

    private ToolSchemaViolationVO violation(com.networknt.schema.Error error) {
        String path = error.getInstanceLocation() == null ? "$" : error.getInstanceLocation().toString();
        boolean missingRequired = "required".equals(error.getKeyword());
        boolean propertySpecific = missingRequired
                || "additionalProperties".equals(error.getKeyword())
                || "unevaluatedProperties".equals(error.getKeyword());
        if (propertySpecific && error.getProperty() != null && !error.getProperty().isBlank()) {
            path = path + (path.endsWith("/") ? "" : "/") + error.getProperty();
        }
        path = bounded(path, MAX_PATH_CHARS);
        String keyword = bounded(error.getKeyword(), MAX_KEYWORD_CHARS);
        String actualType = missingRequired
                ? "MISSING"
                : error.getInstanceNode() == null
                ? "MISSING"
                : error.getInstanceNode().getNodeType().name();
        return ToolSchemaViolationVO.builder()
                .path(path)
                .keyword(keyword)
                .expectedConstraint(bounded(error.getSchemaNode() == null ? null : error.getSchemaNode().toString(),
                        MAX_CONSTRAINT_CHARS))
                .actualType(actualType)
                .message(bounded("Schema validation failed at " + path + " for keyword "
                        + keyword + ".", MAX_MESSAGE_CHARS))
                .build();
    }

    private String safeMessage(String schemaHash, List<ToolSchemaViolationVO> violations) {
        if (violations == null || violations.isEmpty()) {
            return "TOOL_SCHEMA_ERROR schemaHash=" + schemaHash;
        }
        ToolSchemaViolationVO first = violations.get(0);
        return "TOOL_SCHEMA_ERROR path=" + first.getPath()
                + ", keyword=" + first.getKeyword()
                + ", expected=" + first.getExpectedConstraint()
                + ", actualType=" + first.getActualType()
                + ", schemaHash=" + schemaHash;
    }

    private String bounded(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }
}

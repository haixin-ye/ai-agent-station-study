package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.model.valobj.tool.ToolSchemaValidationResultVO;

import java.util.Map;

public interface ToolArgumentSchemaValidator {

    ToolSchemaValidationResultVO validate(Map<String, Object> schema, Map<String, Object> arguments);
}

package yhx.com.domain.agent.service.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalKeyCommandVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

public class ToolApprovalKeyGenerator {

    public String argumentsHash(Map<String, Object> arguments) {
        return sha256(JSON.toJSONString(arguments == null ? Map.of() : arguments,
                SerializerFeature.MapSortField, SerializerFeature.WriteMapNullValue));
    }

    public String approvalKey(ToolApprovalKeyCommandVO command) {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("runId", command.getRunId());
        key.put("capabilityCode", command.getCapabilityCode());
        key.put("mcpServerCode", command.getMcpServerCode());
        key.put("toolName", command.getToolName());
        key.put("argumentsHash", command.getArgumentsHash());
        key.put("requiredPermission", command.getRequiredPermission() == null ? null : command.getRequiredPermission().code());
        key.put("workspaceScope", command.getWorkspaceScope());
        key.put("destructive", Boolean.TRUE.equals(command.getDestructive()));
        return sha256(JSON.toJSONString(key, SerializerFeature.MapSortField, SerializerFeature.WriteMapNullValue));
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }
}

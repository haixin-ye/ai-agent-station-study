package yhx.com.domain.agent.service.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ToolSchemaCanonicalizer {

    private final ObjectMapper objectMapper;

    public ToolSchemaCanonicalizer() {
        this.objectMapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public Object normalize(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> normalized = new TreeMap<>();
            source.forEach((key, item) -> normalized.put(String.valueOf(key), normalize(item)));
            return normalized;
        }
        if (value instanceof Collection<?> source) {
            List<Object> normalized = new ArrayList<>();
            source.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }
        return value;
    }

    public String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(normalize(value));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize MCP tool schema.", e);
        }
    }

    public String schemaHash(Map<String, Object> schema) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson(schema == null ? Map.of() : schema).getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hash.append(String.format("%02x", item));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }
}

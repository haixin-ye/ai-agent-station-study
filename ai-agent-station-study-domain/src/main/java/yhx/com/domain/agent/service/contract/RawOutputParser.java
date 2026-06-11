package yhx.com.domain.agent.service.contract;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.model.valobj.contract.RawOutputParseResult;

public class RawOutputParser {

    public static RawOutputParser defaultParser() {
        return new RawOutputParser();
    }

    public RawOutputParseResult parse(String rawOutput) {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            return failed("EMPTY_OUTPUT", "Model output is empty.");
        }

        String normalized = repairMissingTrailingObjectBraces(normalize(rawOutput));
        if (!normalized.startsWith("{") || !normalized.endsWith("}")) {
            return failed("INVALID_JSON", "Model output must be exactly one JSON object.");
        }
        try {
            JSONObject jsonObject = JSON.parseObject(normalized);
            return RawOutputParseResult.builder()
                    .success(true)
                    .jsonObject(jsonObject)
                    .normalizedJson(normalized)
                    .build();
        } catch (Exception e) {
            String repaired = repairIllegalStringEscapes(normalized);
            if (!repaired.equals(normalized)) {
                try {
                    JSONObject jsonObject = JSON.parseObject(repaired);
                    return RawOutputParseResult.builder()
                            .success(true)
                            .jsonObject(jsonObject)
                            .normalizedJson(repaired)
                            .build();
                } catch (Exception ignored) {
                    // Keep the original parser error below; it is closer to what the model actually returned.
                }
            }
            return failed("INVALID_JSON", e.getMessage());
        }
    }

    private String normalize(String rawOutput) {
        String text = rawOutput.trim();
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1).trim();
        }
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd >= 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }
        return text;
    }

    private String repairMissingTrailingObjectBraces(String text) {
        if (text == null || text.isBlank() || !text.startsWith("{")) {
            return text;
        }
        int objectDepth = 0;
        int arrayDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                objectDepth++;
            } else if (current == '}') {
                objectDepth--;
                if (objectDepth < 0) {
                    return text;
                }
            } else if (current == '[') {
                arrayDepth++;
            } else if (current == ']') {
                arrayDepth--;
                if (arrayDepth < 0) {
                    return text;
                }
            }
        }
        if (inString || arrayDepth != 0 || objectDepth <= 0 || objectDepth > 3) {
            return text;
        }
        return text + "}".repeat(objectDepth);
    }

    private String repairIllegalStringEscapes(String text) {
        if (text == null || text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length() + 16);
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (escaped) {
                if (isValidJsonEscape(current)) {
                    builder.append('\\').append(current);
                } else if (current == ' ') {
                    builder.append("\\n");
                } else {
                    builder.append("\\\\").append(current);
                }
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
            }
            builder.append(current);
        }
        if (escaped) {
            builder.append("\\\\");
        }
        return builder.toString();
    }

    private boolean isValidJsonEscape(char value) {
        return value == '"' || value == '\\' || value == '/' || value == 'b' || value == 'f'
                || value == 'n' || value == 'r' || value == 't' || value == 'u';
    }

    private RawOutputParseResult failed(String code, String message) {
        return RawOutputParseResult.builder()
                .success(false)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }
}

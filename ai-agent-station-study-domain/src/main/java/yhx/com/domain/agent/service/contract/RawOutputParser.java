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

        String normalized = normalize(rawOutput);
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

    private RawOutputParseResult failed(String code, String message) {
        return RawOutputParseResult.builder()
                .success(false)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }
}

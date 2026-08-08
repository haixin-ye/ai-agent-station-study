package yhx.com.domain.agent.service.evaluation;

import java.util.regex.Pattern;

final class RecallEvaluationIdPolicy {

    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{5,12}");

    private RecallEvaluationIdPolicy() {
    }

    static String requireNumericId(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !NUMERIC_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a 5-12 digit string, for example 10001.");
        }
        return normalized;
    }
}

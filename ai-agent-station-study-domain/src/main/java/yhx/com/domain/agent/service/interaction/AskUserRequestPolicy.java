package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AskUserRequestPolicy {

    private static final String SINGLE_CHOICE = "SINGLE_CHOICE";
    private static final String SINGLE_CHOICE_OR_FREE_TEXT = "SINGLE_CHOICE_OR_FREE_TEXT";
    private static final String FREE_TEXT = "FREE_TEXT";
    private static final String CONFIRM = "CONFIRM";

    public String normalizeAndValidate(AskUserRequestVO request) {
        if (request == null) {
            return "AskUserRequest is missing.";
        }
        if (isBlank(request.getQuestion())) {
            return "AskUserRequest.question is required.";
        }
        if (isBlank(request.getInputMode())) {
            return "AskUserRequest.inputMode is required.";
        }
        String inputMode = request.getInputMode().trim();
        request.setInputMode(inputMode);
        if (FREE_TEXT.equals(inputMode)) {
            request.setAllowFreeText(true);
            request.setOptions(List.of());
            return null;
        }
        if (!SINGLE_CHOICE.equals(inputMode)
                && !SINGLE_CHOICE_OR_FREE_TEXT.equals(inputMode)
                && !CONFIRM.equals(inputMode)) {
            return null;
        }

        List<Map<String, Object>> options = request.getOptions() == null ? List.of() : request.getOptions();
        List<Map<String, Object>> concreteOptions = new ArrayList<>();
        List<String> invalidLabels = new ArrayList<>();
        for (Map<String, Object> option : options) {
            if (option == null) {
                continue;
            }
            if (isConcreteChoice(option)) {
                concreteOptions.add(new LinkedHashMap<>(option));
            } else {
                invalidLabels.add(optionLabel(option));
            }
        }

        if (SINGLE_CHOICE_OR_FREE_TEXT.equals(inputMode)) {
            request.setAllowFreeText(true);
            request.setOptions(concreteOptions);
            if (concreteOptions.isEmpty()) {
                request.setInputMode(FREE_TEXT);
            }
            return null;
        }

        if (concreteOptions.isEmpty()) {
            return "AskUserRequest.options must contain concrete choices for " + inputMode + ".";
        }
        if (!invalidLabels.isEmpty()) {
            return "AskUserRequest.options contain non-concrete choices: " + String.join(", ", invalidLabels) + ".";
        }
        request.setOptions(concreteOptions);
        return null;
    }

    private boolean isConcreteChoice(Map<String, Object> option) {
        String label = optionLabel(option);
        if (isBlank(label)) {
            return false;
        }
        String normalized = normalize(label);
        if (containsAny(normalized, "自由输入", "手动输入", "自定义", "自己填写", "我来填写", "其他", "其它", "以上都不是")) {
            return false;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "free text", "manual input", "custom input", "other", "others", "specify")) {
            return false;
        }
        if (looksLikeCategoryExample(normalized)) {
            return false;
        }
        Object value = option.get("value");
        if (value instanceof Map<?, ?> valueMap) {
            Object type = valueMap.get("type");
            if (type != null && String.valueOf(type).toLowerCase(Locale.ROOT).contains("category")) {
                return false;
            }
        }
        return true;
    }

    private boolean looksLikeCategoryExample(String value) {
        if (containsAny(value, "热门", "常见", "候选范围", "类别", "分类")) {
            return true;
        }
        return (value.contains("如") || value.contains("例如") || value.contains("比如"))
                && (value.contains("等") || value.contains("等等"));
    }

    private String optionLabel(Map<String, Object> option) {
        Object label = option.get("label");
        if (label != null) {
            return String.valueOf(label);
        }
        Object title = option.get("title");
        if (title != null) {
            return String.valueOf(title);
        }
        Object optionId = option.get("optionId");
        if (optionId != null) {
            return String.valueOf(optionId);
        }
        Object id = option.get("id");
        return id == null ? null : String.valueOf(id);
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

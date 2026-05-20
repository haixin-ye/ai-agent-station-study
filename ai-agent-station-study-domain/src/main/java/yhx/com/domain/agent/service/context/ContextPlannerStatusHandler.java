package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextMaterializationCommand;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.FailureVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewBuildCommand;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextPlannerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;

import java.util.List;
import java.util.Map;

public class ContextPlannerStatusHandler {

    public static final String BUILD_STATE_VIEW = "BUILD_STATE_VIEW";
    public static final String ASK_USER = "ASK_USER";
    public static final String BUILD_MINIMAL_STATE_VIEW = "BUILD_MINIMAL_STATE_VIEW";
    public static final String COMPRESS_OR_ASK = "COMPRESS_OR_ASK";
    public static final String SAFE_FAILURE = "SAFE_FAILURE";

    private final ContextMaterializer contextMaterializer;
    private final MainAgentStateViewBuilder stateViewBuilder;

    public ContextPlannerStatusHandler(ContextMaterializer contextMaterializer, MainAgentStateViewBuilder stateViewBuilder) {
        this.contextMaterializer = contextMaterializer;
        this.stateViewBuilder = stateViewBuilder;
    }

    public ContextPlannerHandlingResult handle(ContextPlannerOutputVO output, ContextCandidateBundleVO candidates) {
        ContextPlannerStatusEnumVO status = ContextPlannerStatusEnumVO.ofCode(output.getStatus()).orElse(ContextPlannerStatusEnumVO.FAILED);
        return switch (status) {
            case READY -> ready(output, candidates);
            case NO_RELEVANT_CONTEXT -> minimal(BUILD_MINIMAL_STATE_VIEW, candidates);
            case NEEDS_USER_CLARIFICATION -> alreadyAnswered(output, candidates) ? minimal(BUILD_MINIMAL_STATE_VIEW, candidates) : askUser(output);
            case CONTEXT_OVER_BUDGET -> failure(COMPRESS_OR_ASK, "CONTEXT_OVER_BUDGET", "ContextPlanner selected oversized context.");
            case FAILED -> minimal(BUILD_MINIMAL_STATE_VIEW, candidates);
        };
    }

    public ContextPlannerHandlingResult refreshWithoutPlanner(ContextCandidateBundleVO candidates) {
        if (candidates == null) {
            return failure(SAFE_FAILURE, "CONTEXT_CANDIDATES_MISSING", "Context candidates are missing.");
        }
        return ContextPlannerHandlingResult.builder()
                .nextStep(BUILD_MINIMAL_STATE_VIEW)
                .stateView(stateViewBuilder.build(MainAgentStateViewBuildCommand.builder()
                        .candidates(candidates)
                        .artifactContent(List.of())
                        .memoryPack(List.of())
                        .evidencePack(materializeEvidence(candidates))
                        .tokenBudget(candidates.getTokenBudget())
                        .build()))
                .effectiveSelections(List.of())
                .build();
    }

    private ContextPlannerHandlingResult ready(ContextPlannerOutputVO output, ContextCandidateBundleVO candidates) {
        List<ContextSelectionVO> selections = toSelections(output);
        return ContextPlannerHandlingResult.builder()
                .nextStep(BUILD_STATE_VIEW)
                .effectiveSelections(selections)
                .stateView(contextMaterializer.materialize(ContextMaterializationCommand.builder()
                        .candidates(candidates)
                        .plannerOutput(output)
                        .forcedSelections(selections)
                        .tokenBudget(candidates.getTokenBudget())
                        .build()))
                .build();
    }

    private ContextPlannerHandlingResult minimal(String nextStep, ContextCandidateBundleVO candidates) {
        return ContextPlannerHandlingResult.builder()
                .nextStep(nextStep)
                .stateView(stateViewBuilder.build(MainAgentStateViewBuildCommand.builder()
                        .candidates(candidates)
                        .artifactContent(List.of())
                        .memoryPack(List.of())
                        .evidencePack(List.of())
                        .tokenBudget(candidates.getTokenBudget())
                        .build()))
                .effectiveSelections(List.of())
                .build();
    }

    private ContextPlannerHandlingResult askUser(ContextPlannerOutputVO output) {
        Map<String, Object> request = output.getClarificationRequest();
        return ContextPlannerHandlingResult.builder()
                .nextStep(ASK_USER)
                .askUserRequest(AskUserRequestVO.builder()
                        .question(stringValue(request, "question"))
                        .inputMode(stringValue(request, "inputMode"))
                        .allowFreeText(Boolean.TRUE.equals(request == null ? null : request.get("allowFreeText")))
                        .options(options(request))
                        .build())
                .effectiveSelections(List.of())
                .build();
    }

    private boolean alreadyAnswered(ContextPlannerOutputVO output, ContextCandidateBundleVO candidates) {
        Map<String, Object> request = output == null ? null : output.getClarificationRequest();
        String question = normalize(stringValue(request, "question"));
        if (question.isBlank() || candidates == null || candidates.getUserClarifications() == null) {
            return false;
        }
        return candidates.getUserClarifications().stream()
                .anyMatch(clarification -> answeredSameQuestion(question, clarification));
    }

    private boolean answeredSameQuestion(String normalizedQuestion, UserClarificationVO clarification) {
        if (clarification == null || !normalizedQuestion.equals(normalize(clarification.getQuestion()))) {
            return false;
        }
        return clarification.getValue() != null
                || notBlank(clarification.getSelectedOptionId())
                || notBlank(clarification.getFreeText());
    }

    private ContextPlannerHandlingResult failure(String nextStep, String code, String message) {
        return ContextPlannerHandlingResult.builder()
                .nextStep(nextStep)
                .failure(FailureVO.builder().failureCode(code).message(message).build())
                .effectiveSelections(List.of())
                .build();
    }

    private List<MaterializedEvidenceVO> materializeEvidence(ContextCandidateBundleVO candidates) {
        if (candidates.getEvidenceCandidates() == null || candidates.getEvidenceCandidates().isEmpty()) {
            return List.of();
        }
        return candidates.getEvidenceCandidates().stream()
                .map(evidence -> MaterializedEvidenceVO.builder()
                        .evidenceId(evidence.getEvidenceId())
                        .evidenceType(evidence.getEvidenceType())
                        .sourceRef(evidence.getSourceRef())
                        .summary(truncate(evidence.getSummary(), candidates))
                        .boundedSnippet(truncate(evidence.getSummary(), candidates))
                        .build())
                .toList();
    }

    private String truncate(String value, ContextCandidateBundleVO candidates) {
        if (value == null) {
            return null;
        }
        int maxChars = candidates.getTokenBudget() == null || candidates.getTokenBudget().getMaxEvidenceSummaryChars() == null
                ? 800
                : candidates.getTokenBudget().getMaxEvidenceSummaryChars();
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private List<ContextSelectionVO> toSelections(ContextPlannerOutputVO output) {
        if (output.getSelectedContext() == null) {
            return List.of();
        }
        return output.getSelectedContext().stream().map(item -> ContextSelectionVO.builder()
                .sourceType(stringValue(item, "sourceType"))
                .sourceId(firstNonBlank(stringValue(item, "sourceId"), stringValue(item, "artifactId"), stringValue(item, "memoryId"), stringValue(item, "evidenceId"), stringValue(item, "messageId")))
                .contextLevel(ContextLevelEnumVO.ofCode(firstNonBlank(stringValue(item, "contextLevel"), stringValue(item, "useLevel"))).orElse(ContextLevelEnumVO.SUMMARY_ONLY))
                .priority(intValue(item, "priority"))
                .confidence(doubleValue(item, "confidence"))
                .reason(stringValue(item, "reason"))
                .build()).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> options(Map<String, Object> request) {
        Object value = request == null ? null : request.get("options");
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private String stringValue(Map<String, Object> item, String key) {
        if (item == null || item.get(key) == null) {
            return null;
        }
        return String.valueOf(item.get(key));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Integer intValue(Map<String, Object> item, String key) {
        Object value = item == null ? null : item.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private Double doubleValue(Map<String, Object> item, String key) {
        Object value = item == null ? null : item.get(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

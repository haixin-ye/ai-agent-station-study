package yhx.com.domain.agent.service.memory;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TurnSummaryRecallPreselector {

    private static final Pattern ASCII_TOKEN = Pattern.compile("[A-Za-z0-9_\\-]{2,}");
    private static final Pattern CJK_RUN = Pattern.compile("[\\p{IsHan}]{2,}");

    private final IPayloadRepository payloadRepository;

    public TurnSummaryRecallPreselector(IPayloadRepository payloadRepository) {
        this.payloadRepository = payloadRepository;
    }

    public List<SummaryCandidateVO> select(String userInput,
                                           List<AgentTurnSummaryEntity> summaries,
                                           Set<String> excludedSummaryIds,
                                           int limit) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        Set<String> excluded = excludedSummaryIds == null ? Set.of() : excludedSummaryIds;
        return summaries.stream()
                .filter(summary -> summary.getSummaryId() != null && !excluded.contains(summary.getSummaryId()))
                .map(summary -> toCandidate(userInput, summary))
                .filter(candidate -> candidate.getRelevanceScore() != null && candidate.getRelevanceScore() > 0.0)
                .sorted(Comparator.comparing(SummaryCandidateVO::getRelevanceScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SummaryCandidateVO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(limit, 0))
                .toList();
    }

    private SummaryCandidateVO toCandidate(String userInput, AgentTurnSummaryEntity summary) {
        String summaryText = loadSummaryText(summary);
        List<String> artifactRefs = parseStringList(summary.getArtifactRefsJson());
        double score = overlapScore(userInput, summaryText + " " + nullToEmpty(summary.getIntent()) + " " + nullToEmpty(summary.getTopicsJson()));
        if (!artifactRefs.isEmpty() && containsReferenceWord(userInput)) {
            score += 2.5;
        }
        if (summary.getImportanceScore() != null) {
            score += Math.min(1.5, summary.getImportanceScore().doubleValue());
        }
        score += recencyScore(summary.getCreatedAt());
        return SummaryCandidateVO.builder()
                .summaryId(summary.getSummaryId())
                .turnId(summary.getTurnId())
                .summary(summaryText)
                .summaryRef(summary.getSummaryRef())
                .artifactRefs(artifactRefs)
                .relevanceScore(score)
                .createdAt(summary.getCreatedAt())
                .build();
    }

    private String loadSummaryText(AgentTurnSummaryEntity summary) {
        if (payloadRepository == null || summary.getSummaryRef() == null || summary.getSummaryRef().isBlank()) {
            return null;
        }
        return payloadRepository.findPayload(summary.getSummaryRef())
                .map(payload -> firstNonBlank(payload.getContent(), payload.getPreview()))
                .orElse(null);
    }

    private double overlapScore(String userInput, String target) {
        Set<String> queryTokens = tokens(userInput);
        Set<String> targetTokens = tokens(target);
        if (queryTokens.isEmpty() || targetTokens.isEmpty()) {
            return 0.0;
        }
        double score = 0.0;
        for (String token : queryTokens) {
            if (targetTokens.contains(token)) {
                score += token.length() >= 4 ? 1.5 : 1.0;
            }
        }
        return score;
    }

    private Set<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String normalized = text.toLowerCase();
        Set<String> tokens = new LinkedHashSet<>();
        Matcher asciiMatcher = ASCII_TOKEN.matcher(normalized);
        while (asciiMatcher.find()) {
            tokens.add(asciiMatcher.group());
        }
        Matcher cjkMatcher = CJK_RUN.matcher(normalized);
        while (cjkMatcher.find()) {
            String value = cjkMatcher.group();
            for (int i = 0; i < value.length() - 1; i++) {
                tokens.add(value.substring(i, i + 2));
            }
        }
        return tokens;
    }

    private boolean containsReferenceWord(String value) {
        if (value == null) {
            return false;
        }
        String text = value.toLowerCase();
        return text.contains("previous")
                || text.contains("last")
                || text.contains("that")
                || text.contains("article")
                || text.contains("draft")
                || text.contains("上次")
                || text.contains("之前")
                || text.contains("刚才")
                || text.contains("那篇")
                || text.contains("文章")
                || text.contains("草稿")
                || text.contains("方案");
    }

    private double recencyScore(LocalDateTime createdAt) {
        if (createdAt == null) {
            return 0.0;
        }
        long hours = Math.max(1, Duration.between(createdAt, LocalDateTime.now()).toHours());
        return Math.max(0.0, 1.0 - Math.min(1.0, hours / (24.0 * 14)));
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = JSON.parseArray(json, String.class);
            return values == null ? List.of() : new ArrayList<>(values);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

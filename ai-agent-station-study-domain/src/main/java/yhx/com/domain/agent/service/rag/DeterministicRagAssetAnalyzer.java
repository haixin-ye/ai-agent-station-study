package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.model.valobj.rag.RagAssetAnalysisResultVO;

public class DeterministicRagAssetAnalyzer implements RagAssetAnalyzer {

    @Override
    public RagAssetAnalysisResultVO analyzeDocument(String sourceName, String sourceType, String text) {
        return RagAssetAnalysisResultVO.builder()
                .title(sourceName)
                .summary(summarize(text))
                .retrievalText(indexText(sourceName, sourceType, text))
                .language(language(sourceName))
                .build();
    }

    @Override
    public RagAssetAnalysisResultVO analyzeChunk(String sourceName, String sourceType, String text) {
        return RagAssetAnalysisResultVO.builder()
                .summary(summarize(text))
                .retrievalText(text)
                .language(language(sourceName))
                .build();
    }

    private String indexText(String sourceName, String sourceType, String text) {
        return "sourceType: " + sourceType
                + "\nsourceName: " + sourceName
                + "\nsummary: " + summarize(text)
                + "\ncontent:\n" + preview(text, 4000);
    }

    private String summarize(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return preview(normalized, 300);
    }

    private String preview(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private String language(String sourceName) {
        if (sourceName == null) {
            return null;
        }
        String lower = sourceName.toLowerCase();
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".py")) {
            return "python";
        }
        if (lower.endsWith(".js")) {
            return "javascript";
        }
        if (lower.endsWith(".ts")) {
            return "typescript";
        }
        if (lower.endsWith(".md")) {
            return "markdown";
        }
        return null;
    }
}

package yhx.com.domain.agent.service.rag.runtime;

import yhx.com.domain.agent.model.valobj.rag.RagHitVO;

public class RagEvidenceSnippetPolicy {

    private static final int DEFAULT_MAX_CHARS = 1200;

    public String summarizeHit(RagHitVO hit, int maxChars) {
        if (hit == null) {
            return null;
        }
        String title = hit.getTitle() == null ? "" : hit.getTitle().trim();
        String snippet = boundedSnippet(hit.getChunkText(), maxChars);
        if (snippet == null || snippet.isBlank()) {
            return null;
        }
        if (title.isBlank()) {
            return snippet;
        }
        return title + ": " + snippet;
    }

    public String boundedSnippet(String chunkText, int maxChars) {
        if (chunkText == null || chunkText.isBlank()) {
            return null;
        }
        String normalized = chunkText.strip();
        int limit = maxChars <= 0 ? DEFAULT_MAX_CHARS : maxChars;
        return normalized.substring(0, Math.min(limit, normalized.length()));
    }
}

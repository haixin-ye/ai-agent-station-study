package yhx.com.domain.agent.service.rag;

import java.util.ArrayList;
import java.util.List;

public class RagParagraphChunker {

    private static final int DEFAULT_MAX_CHARS = 1600;
    private static final int DEFAULT_OVERLAP_CHARS = 160;

    private final int maxChars;
    private final int overlapChars;

    public RagParagraphChunker() {
        this(DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS);
    }

    public RagParagraphChunker(int maxChars, int overlapChars) {
        this.maxChars = Math.max(80, maxChars);
        this.overlapChars = Math.max(0, Math.min(overlapChars, this.maxChars / 2));
    }

    public List<String> split(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            appendParagraph(chunks, paragraph.trim());
        }
        return chunks;
    }

    private void appendParagraph(List<String> chunks, String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            return;
        }
        if (paragraph.length() <= maxChars) {
            chunks.add(paragraph);
            return;
        }
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + maxChars, paragraph.length());
            chunks.add(paragraph.substring(start, end).trim());
            if (end >= paragraph.length()) {
                break;
            }
            start = Math.max(end - overlapChars, start + 1);
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}

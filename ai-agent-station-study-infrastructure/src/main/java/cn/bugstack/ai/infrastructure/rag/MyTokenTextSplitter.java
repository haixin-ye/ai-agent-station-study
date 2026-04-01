package cn.bugstack.ai.infrastructure.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom token text splitter with percentage overlap.
 *
 * @author yhx
 */
@Component
public class MyTokenTextSplitter extends TokenTextSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final double DEFAULT_OVERLAP_RATIO = 0.15D;

    public MyTokenTextSplitter() {
        super(DEFAULT_CHUNK_SIZE, 100, 5, 10000, true);
    }

    public List<Document> split(Document document) {
        return splitWithPercentageOverlap(document, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_RATIO);
    }

    public List<Document> splitWithPercentageOverlap(Document document, int chunkSize, double overlapRatio) {
        if (overlapRatio < 0 || overlapRatio >= 1) {
            throw new IllegalArgumentException("overlapRatio must be in [0, 1).");
        }

        String content = document.getText();
        Map<String, Object> metadata = document.getMetadata();

        List<Document> chunks = new ArrayList<>();
        int overlapSize = (int) (chunkSize * overlapRatio);
        int step = chunkSize - overlapSize;

        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            String chunkContent = content.substring(start, end);
            chunks.add(new Document(chunkContent, metadata));

            if (end == content.length()) {
                break;
            }
            start += step;
        }
        return chunks;
    }
}

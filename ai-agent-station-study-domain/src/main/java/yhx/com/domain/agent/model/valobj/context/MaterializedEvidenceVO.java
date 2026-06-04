package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterializedEvidenceVO {

    private String evidenceId;
    private String evidenceType;
    private String sourceRef;
    private String summary;
    private String boundedSnippet;
    private String content;
    private String contentRef;
    private String contentFormat;
    private Boolean truncated;
    private Integer totalChars;
    private Long totalBytes;
    private Long sequence;
    private Integer sourceLoopIndex;
    private String sourceWorkId;
    private LocalDateTime createdAt;
    private Map<String, Object> metadata;
}

package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactCandidateVO {

    private String artifactId;
    private String artifactType;
    private String title;
    private String summary;
    private List<String> aliases;
    private String contentRef;
    private Integer tokenCount;
    private Integer version;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMentionedAt;
    private Double recencyScore;
    private Double aliasScore;
    private Double titleScore;
    private Double totalScore;
    private String sourceChannel;
    private Double sourceScore;
    private List<String> reasons;
    private List<ArtifactChunkVO> matchedChunks;
}

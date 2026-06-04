package yhx.com.domain.agent.model.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagCodeSymbolEntity {

    private String symbolId;
    private String codeFileId;
    private String documentId;
    private String symbolName;
    private String symbolKind;
    private Integer startLine;
    private Integer endLine;
    private String summary;
    private String contentRef;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

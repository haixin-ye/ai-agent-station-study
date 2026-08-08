package yhx.com.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagFileUploadResultDTO {

    private List<UploadedDocument> documents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadedDocument {
        private String documentId;
        private String sourceName;
        private String title;
        private String summary;
        private String status;
        private Integer chunkCount;
    }
}

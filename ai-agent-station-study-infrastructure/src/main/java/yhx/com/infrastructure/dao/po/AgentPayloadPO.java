package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentPayloadPO {

    private Long id;
    private String payloadId;
    private String payloadType;
    private String storageType;
    private String content;
    private String contentPath;
    private String contentSha256;
    private String preview;
    private Integer compressed;
    private Integer encrypted;
    private LocalDateTime createdAt;
}

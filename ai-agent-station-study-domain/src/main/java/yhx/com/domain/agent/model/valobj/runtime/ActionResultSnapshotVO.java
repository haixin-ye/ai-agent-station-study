package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResultSnapshotVO {

    private String status;
    private String message;
    private String content;
    private String contentRef;
    private String contentFormat;
    private Boolean truncated;
    private Integer totalChars;
    private Long totalBytes;
    private Map<String, Object> raw;
}

package yhx.com.domain.agent.model.valobj.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedMemoryVO {

    private String memoryType;
    private String summary;
    private String content;
    private String recallText;
    private BigDecimal score;
    private String reason;
}

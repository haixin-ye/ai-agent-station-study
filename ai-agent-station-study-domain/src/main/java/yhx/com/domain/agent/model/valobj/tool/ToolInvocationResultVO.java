package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolResultContentModeEnumVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationResultVO {

    private ToolInvocationStatusEnumVO status;
    private String toolCallId;
    private String toolInvocationId;
    private String receiptRef;
    private String resultSummary;
    private String resultContent;
    private String resultContentRef;
    private ToolResultContentModeEnumVO resultContentMode;
    private String resultContentFormat;
    private Integer resultTotalChars;
    private Long resultTotalBytes;
    private String failureCode;
    private String failureMessage;
    private String schemaHash;
    private List<ToolSchemaViolationVO> schemaViolations;
}

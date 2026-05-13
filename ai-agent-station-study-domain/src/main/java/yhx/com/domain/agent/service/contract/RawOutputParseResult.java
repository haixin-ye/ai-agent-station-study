package yhx.com.domain.agent.service.contract;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawOutputParseResult {

    private boolean success;
    private JSONObject jsonObject;
    private String normalizedJson;
    private String errorCode;
    private String errorMessage;
}

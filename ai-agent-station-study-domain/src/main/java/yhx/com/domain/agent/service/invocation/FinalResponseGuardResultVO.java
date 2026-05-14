package yhx.com.domain.agent.service.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalResponseGuardResultVO {

    private String status;
    private String finalContent;
    private String failureCode;
    private String detail;
}

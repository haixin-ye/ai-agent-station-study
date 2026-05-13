package yhx.com.domain.agent.service.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractViolation {

    private String code;
    private String field;
    private String message;
}

package yhx.com.domain.agent.service.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContractValidationResult {

    private final List<ContractViolation> violations = new ArrayList<>();

    public static ContractValidationResult passed() {
        return new ContractValidationResult();
    }

    public static ContractValidationResult failed(String code, String field, String message) {
        ContractValidationResult result = new ContractValidationResult();
        result.addViolation(code, field, message);
        return result;
    }

    public boolean isPassed() {
        return violations.isEmpty();
    }

    public List<ContractViolation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    public void addViolation(String code, String field, String message) {
        violations.add(ContractViolation.builder()
                .code(code)
                .field(field)
                .message(message)
                .build());
    }
}

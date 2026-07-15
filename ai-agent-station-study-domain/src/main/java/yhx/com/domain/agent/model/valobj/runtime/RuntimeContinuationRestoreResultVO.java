package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeContinuationRestoreResultVO {

    private Boolean restored;
    private Boolean legacyFallback;
    private String message;

    public boolean isRestored() {
        return Boolean.TRUE.equals(restored);
    }

    public boolean isLegacyFallback() {
        return Boolean.TRUE.equals(legacyFallback);
    }
}

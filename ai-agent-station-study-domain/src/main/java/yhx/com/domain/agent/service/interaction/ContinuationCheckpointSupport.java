package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;

import java.util.Map;

final class ContinuationCheckpointSupport {

    private ContinuationCheckpointSupport() {
    }

    static RuntimePhaseEnumVO resumePhase(ContinuationCheckpointVO checkpoint, RuntimePhaseEnumVO fallback) {
        return checkpoint == null || checkpoint.getResumePhase() == null ? fallback : checkpoint.getResumePhase();
    }

    static Map<String, Object> payload(ContinuationCheckpointVO checkpoint) {
        return checkpoint == null || checkpoint.getPayload() == null ? Map.of() : checkpoint.getPayload();
    }

    static String stringValue(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? null : String.valueOf(value);
    }
}

package yhx.com.domain.agent.service.finalresponse;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;

import java.util.Locale;

public class FinalResponseGuardInputBuilder {

    private static final int DEFAULT_MAX_OUTPUT_CHARS = 30000;

    public FinalResponseGuardInputVO build(FinalDeliveryCommandVO command) {
        return FinalResponseGuardInputVO.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .loopIndex(command.getLoopIndex())
                .candidate(command.getFinalAnswerCandidate())
                .evidenceRefs(command.getEvidenceIds())
                .verifiedToolCallRefs(command.getVerifiedToolCallRefs())
                .userFormatRequirement(command.getUserFormatRequirement())
                .maxOutputChars(command.getMaxOutputChars() == null ? DEFAULT_MAX_OUTPUT_CHARS : command.getMaxOutputChars())
                .userAskedForInternals(userAskedForInternals(command.getUserInput()))
                .build();
    }

    private boolean userAskedForInternals(String userInput) {
        if (userInput == null) {
            return false;
        }
        String normalized = userInput.toLowerCase(Locale.ROOT);
        return normalized.contains("内部") || normalized.contains("trace") || normalized.contains("prompt")
                || normalized.contains("runtime") || normalized.contains("debug");
    }
}

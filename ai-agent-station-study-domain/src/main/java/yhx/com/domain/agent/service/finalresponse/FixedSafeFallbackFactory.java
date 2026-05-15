package yhx.com.domain.agent.service.finalresponse;

import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;

public class FixedSafeFallbackFactory {

    public static final String FALLBACK_TEXT = "I could not produce a safe final response for this request. Please retry with a narrower request or provide more details.";

    public FinalAnswerCandidateVO create() {
        return FinalAnswerCandidateVO.builder()
                .content(FALLBACK_TEXT)
                .format("PLAIN_TEXT")
                .build();
    }
}

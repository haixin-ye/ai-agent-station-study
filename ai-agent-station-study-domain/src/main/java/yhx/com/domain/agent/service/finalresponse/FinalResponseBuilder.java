package yhx.com.domain.agent.service.finalresponse;

import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;

import java.time.LocalDateTime;

public class FinalResponseBuilder {

    public FinalResponseVO build(FinalDeliveryCommandVO command, FinalAnswerCandidateVO candidate, String contentRef) {
        return FinalResponseVO.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .content(candidate == null ? null : candidate.getContent())
                .contentRef(contentRef)
                .format(candidate == null ? null : candidate.getFormat())
                .createdAt(LocalDateTime.now())
                .build();
    }
}

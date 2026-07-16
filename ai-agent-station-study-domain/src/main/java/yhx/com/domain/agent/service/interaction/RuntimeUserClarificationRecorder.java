package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RuntimeUserClarificationRecorder {

    public void append(RuntimeExecutionContext context, UserClarificationVO clarification) {
        if (context == null || clarification == null) {
            return;
        }
        if (context.getRuntimeFacts() != null) {
            List<UserClarificationVO> facts = clarificationFacts(context);
            appendIfMissing(facts, clarification);
            context.getRuntimeFacts().put("userClarifications", facts);
        }
        if (context.getWorkingState() != null) {
            List<UserClarificationVO> working = context.getWorkingState().getUserClarifications();
            if (working == null) {
                working = new ArrayList<>();
                context.getWorkingState().setUserClarifications(working);
            }
            appendIfMissing(working, clarification);
        }
    }

    @SuppressWarnings("unchecked")
    private List<UserClarificationVO> clarificationFacts(RuntimeExecutionContext context) {
        Object existing = context.getRuntimeFacts().get("userClarifications");
        if (existing instanceof List<?> list) {
            return new ArrayList<>((List<UserClarificationVO>) list);
        }
        return new ArrayList<>();
    }

    private void appendIfMissing(List<UserClarificationVO> target, UserClarificationVO clarification) {
        if (clarification.getPendingId() != null) {
            for (int index = 0; index < target.size(); index++) {
                UserClarificationVO existing = target.get(index);
                if (existing != null && Objects.equals(existing.getPendingId(), clarification.getPendingId())) {
                    if (!Objects.equals(existing.getAnswerType(), clarification.getAnswerType())) {
                        target.set(index, clarification);
                    }
                    return;
                }
            }
        }
        boolean duplicate = target.stream().anyMatch(existing -> existing != null
                && Objects.equals(existing.getPendingId(), clarification.getPendingId())
                && Objects.equals(existing.getAnswerType(), clarification.getAnswerType()));
        if (!duplicate) {
            target.add(clarification);
        }
    }
}

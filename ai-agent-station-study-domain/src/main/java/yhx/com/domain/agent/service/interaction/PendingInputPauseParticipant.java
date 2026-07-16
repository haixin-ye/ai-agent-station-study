package yhx.com.domain.agent.service.interaction;

import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;

public interface PendingInputPauseParticipant {

    boolean supports(String handlerCode);

    void beforePendingInputPersisted(PendingInputCreateCommand command);
}

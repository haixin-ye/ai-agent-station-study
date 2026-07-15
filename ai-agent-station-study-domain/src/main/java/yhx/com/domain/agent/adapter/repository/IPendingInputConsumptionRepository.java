package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.valobj.interaction.PendingInputConsumptionResultVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;

public interface IPendingInputConsumptionRepository {

    PendingInputConsumptionResultVO consume(String pendingId,
                                            String runId,
                                            UserAnswerVO answer,
                                            boolean cancelled);
}

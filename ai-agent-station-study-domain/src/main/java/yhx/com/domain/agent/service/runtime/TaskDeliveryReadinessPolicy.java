package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.runtime.TaskDeliverableVO;
import yhx.com.domain.agent.model.valobj.runtime.TaskLedgerVO;

import java.util.List;
import java.util.Set;

public class TaskDeliveryReadinessPolicy {
    private static final Set<String> READY_STATUSES = Set.of("READY", "COMPLETED", "CANCELLED");

    public boolean isReady(TaskLedgerVO ledger) {
        List<TaskDeliverableVO> deliverables = ledger == null ? null : ledger.getDeliverables();
        return deliverables != null && !deliverables.isEmpty()
                && deliverables.stream().allMatch(this::ready);
    }

    private boolean ready(TaskDeliverableVO deliverable) {
        return deliverable != null && READY_STATUSES.contains(deliverable.getStatus());
    }
}

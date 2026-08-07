package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.runtime.TaskLedgerVO;
import yhx.com.domain.agent.service.runtime.TaskDeliveryReadinessPolicy;
import yhx.com.domain.agent.service.runtime.TaskLedgerMergeService;

import java.util.List;
import java.util.Map;

public class TaskLedgerMergeServiceTest {

    @Test
    public void updates_preserve_existing_plan_and_record_explicit_revision() {
        TaskLedgerMergeService service = new TaskLedgerMergeService();
        TaskLedgerVO ledger = service.merge(null, Map.of(
                "goal", "produce mysql and redis guides",
                "deliverableUpdates", List.of(
                        Map.of("deliverableId", "mysql", "description", "MySQL guide", "status", "READY"),
                        Map.of("deliverableId", "redis", "description", "Redis guide", "status", "PENDING")),
                "stepUpdates", List.of(
                        Map.of("stepId", "s1", "description", "draft MySQL", "status", "COMPLETED"),
                        Map.of("stepId", "s2", "description", "draft Redis", "status", "PENDING"))), 0);

        ledger = service.merge(ledger, Map.of(
                "deliverableUpdates", List.of(Map.of("deliverableId", "redis", "status", "READY")),
                "stepUpdates", List.of(Map.of("stepId", "s2", "status", "COMPLETED")),
                "planRevision", Map.of("reason", "Redis source became available", "retainedStepIds", List.of("s1", "s2"))), 1);

        Assert.assertEquals(2, ledger.getDeliverables().size());
        Assert.assertEquals(2, ledger.getSteps().size());
        Assert.assertEquals(1, ledger.getPlanRevisions().size());
        Assert.assertTrue(new TaskDeliveryReadinessPolicy().isReady(ledger));
    }

    @Test
    public void delivery_is_not_ready_when_any_required_deliverable_is_pending() {
        TaskLedgerVO ledger = new TaskLedgerMergeService().merge(null, Map.of(
                "goal", "produce two guides",
                "deliverableUpdates", List.of(
                        Map.of("deliverableId", "mysql", "status", "READY"),
                        Map.of("deliverableId", "redis", "status", "PENDING"))), 0);

        Assert.assertFalse(new TaskDeliveryReadinessPolicy().isReady(ledger));
    }
}

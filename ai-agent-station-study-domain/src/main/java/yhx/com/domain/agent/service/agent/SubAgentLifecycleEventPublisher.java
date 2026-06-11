package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.AgentDispatchResultVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SubAgentLifecycleEventPublisher {

    private final RunEventPublisher eventPublisher;

    public SubAgentLifecycleEventPublisher(RunEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void dispatched(String parentRunId, AgentDispatchResultVO dispatchResult, List<DelegateAgentTaskVO> tasks) {
        if (eventPublisher == null || dispatchResult == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subAgentEventType", "CHILD_AGENTS_DISPATCHED");
        payload.put("waitMode", nullToEmpty(dispatchResult.getWaitMode()));
        payload.put("childRunIds", defaultList(dispatchResult.getChildRunIds()));
        payload.put("tasks", defaultList(tasks));
        eventPublisher.subAgentEvent(parentRunId,
                "已派发子 Agent",
                "MainNode 已派发 " + safeSize(dispatchResult.getChildRunIds()) + " 个并行子任务，父级进入等待。",
                payload);
    }

    public void started(String parentRunId, ParentChildRunRelationVO relation, DelegateAgentTaskVO task) {
        if (eventPublisher == null || relation == null) {
            return;
        }
        Map<String, Object> payload = basePayload(relation);
        payload.put("subAgentEventType", "CHILD_AGENT_STARTED");
        payload.put("status", "RUNNING");
        payload.put("task", task == null ? Map.of() : task);
        eventPublisher.subAgentEvent(parentRunId,
                "子 Agent 开始工作",
                label(relation) + " 开始执行。",
                payload);
    }

    public void action(String parentRunId, ParentChildRunRelationVO relation, SubAgentActionVO action, Integer loopIndex) {
        if (eventPublisher == null || relation == null || action == null) {
            return;
        }
        Map<String, Object> payload = basePayload(relation);
        payload.put("subAgentEventType", "CHILD_AGENT_ACTION");
        payload.put("status", "RUNNING");
        payload.put("loopIndex", loopIndex);
        payload.put("action", nullToEmpty(action.getAction()));
        payload.put("actionInput", action.getActionInput() == null ? Map.of() : action.getActionInput());
        eventPublisher.subAgentEvent(parentRunId,
                "子 Agent 执行动作",
                label(relation) + " 输出 " + nullToEmpty(action.getAction()) + "。",
                payload);
    }

    public void handlerResult(String parentRunId,
                              ParentChildRunRelationVO relation,
                              SubAgentActionHandlerResultVO result,
                              Integer loopIndex) {
        if (eventPublisher == null || relation == null || result == null) {
            return;
        }
        Map<String, Object> payload = basePayload(relation);
        payload.put("subAgentEventType", "CHILD_AGENT_PROGRESS");
        payload.put("status", result.getStatus() == null ? "RUNNING" : result.getStatus().code());
        payload.put("loopIndex", loopIndex);
        payload.put("action", nullToEmpty(result.getAction()));
        payload.put("message", nullToEmpty(result.getMessage()));
        payload.put("resultSnapshot", result.getResultSnapshot() == null ? Map.of() : result.getResultSnapshot());
        eventPublisher.subAgentEvent(parentRunId,
                "子 Agent 更新进度",
                label(relation) + " " + nullToEmpty(result.getMessage()),
                payload);
    }

    public void terminal(String parentRunId, ParentChildRunRelationVO relation) {
        if (eventPublisher == null || relation == null || relation.getStatus() == null) {
            return;
        }
        boolean failed = relation.getStatus().name().contains("FAILED") || relation.getStatus().name().contains("BLOCKED");
        Map<String, Object> payload = basePayload(relation);
        payload.put("subAgentEventType", failed ? "CHILD_AGENT_FAILED" : "CHILD_AGENT_COMMITTED");
        payload.put("status", relation.getStatus().code());
        payload.put("failureMessage", nullToEmpty(relation.getFailureMessage()));
        payload.put("commit", relation.getCommit() == null ? Map.of() : relation.getCommit());
        eventPublisher.subAgentEvent(parentRunId,
                failed ? "子 Agent 失败" : "子 Agent 已提交",
                failed ? label(relation) + " 执行失败。" : label(relation) + " 已提交结果。",
                payload);
    }

    public void parentWaiting(String parentRunId, List<String> childRunIds) {
        if (eventPublisher == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subAgentEventType", "PARENT_WAITING_CHILDREN");
        payload.put("childRunIds", defaultList(childRunIds));
        eventPublisher.subAgentEvent(parentRunId,
                "MainNode 等待子 Agent",
                "父级已暂停，等待 " + safeSize(childRunIds) + " 个子任务完成。",
                payload);
    }

    public void parentReady(String parentRunId) {
        if (eventPublisher == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subAgentEventType", "CHILDREN_READY");
        payload.put("status", "READY");
        eventPublisher.subAgentEvent(parentRunId,
                "子 Agent 全部完成",
                "全部子任务已完成，MainNode 将继续下一轮分析。",
                payload);
    }

    private Map<String, Object> basePayload(ParentChildRunRelationVO relation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("childRunId", nullToEmpty(relation.getChildRunId()));
        payload.put("taskId", nullToEmpty(relation.getTaskId()));
        payload.put("name", nullToEmpty(relation.getChildName()));
        payload.put("dispatchBatchId", nullToEmpty(relation.getDispatchBatchId()));
        return payload;
    }

    private String label(ParentChildRunRelationVO relation) {
        if (relation == null) {
            return "子 Agent";
        }
        if (relation.getChildName() != null && !relation.getChildName().isBlank()) {
            return relation.getChildName();
        }
        return relation.getChildRunId() == null ? "子 Agent" : relation.getChildRunId();
    }

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private <T> List<T> defaultList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

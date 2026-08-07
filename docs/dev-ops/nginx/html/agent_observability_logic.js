(function (root, factory) {
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    if (root) root.AgentObservabilityLogic = api;
})(typeof window !== "undefined" ? window : globalThis, function () {
    const INACTIVE = new Set(["CANCELLED", "OBSOLETE", "SUPERSEDED", "REMOVED", "ABANDONED"]);

    function list(value) { return Array.isArray(value) ? value : []; }
    function keyOf(item, fallback) { return String(item?.id ?? item?.stepId ?? item?.taskId ?? item?.deliverableId ?? fallback); }
    function statusOf(item) { return String(item?.status ?? item?.state ?? "").toUpperCase(); }
    function obsoleteOf(item, cancelledIds) {
        const status = statusOf(item);
        return INACTIVE.has(status) || cancelledIds.has(String(item?.id ?? item?.stepId ?? ""));
    }
    function mergeRecords(base, updates, kind, cancelledIds) {
        const result = [];
        const index = new Map();
        list(base).forEach((item, position) => {
            const copy = { ...item, _planKind: kind, _planIndex: position };
            index.set(keyOf(copy, position), copy);
            result.push(copy);
        });
        list(updates).forEach((item, position) => {
            const id = keyOf(item, `${kind}-new-${position}`);
            const existing = index.get(id);
            if (existing) {
                const previousStatus = statusOf(existing);
                const before = JSON.stringify(existing);
                Object.assign(existing, item, { _planKind: kind, changed: before !== JSON.stringify({ ...existing, ...item }) });
                const nextStatus = statusOf(existing);
                if (previousStatus && nextStatus && previousStatus !== nextStatus) existing.previousStatus = previousStatus;
            } else {
                const copy = { ...item, _planKind: kind, newRecord: true, changed: true };
                index.set(id, copy);
                result.push(copy);
            }
        });
        result.forEach(item => {
            item.obsolete = obsoleteOf(item, cancelledIds);
            if (item.obsolete && !item.replacementState) item.replacementState = statusOf(item) || "OBSOLETE";
        });
        return result;
    }
    function mergeEffectivePlan(ledger, update) {
        const source = ledger || {};
        const change = update || {};
        const revision = change.planRevision || source.planRevision || {};
        const cancelledIds = new Set(list(revision.cancelledStepIds).map(String));
        const steps = mergeRecords(source.steps || source.taskSteps, change.stepUpdates || change.steps, "step", cancelledIds);
        const deliverables = mergeRecords(source.deliverables || source.taskDeliverables, change.deliverableUpdates || change.deliverables, "deliverable", new Set());
        const byId = new Map(deliverables.map(item => [keyOf(item), item]));
        steps.forEach(step => {
            step.attachedDeliverables = list(step.affectedDeliverableIds).map(String).map(id => byId.get(id)).filter(Boolean);
        });
        const attached = new Set(steps.flatMap(step => list(step.affectedDeliverableIds).map(String)));
        return { goal: change.goal || source.goal || change.taskGoal || source.taskGoal, steps, deliverables, unlinkedDeliverables: deliverables.filter(item => !attached.has(keyOf(item))), revision };
    }
    function groupSessionRuns(messages) {
        const groups = new Map();
        list(messages).forEach(message => {
            const id = message?.runId || message?.run_id;
            if (!id) return;
            const group = groups.get(id) || { runId: id, messages: [], userMessage: null, label: "未命名运行", createdAt: message.createdAt || message.created_at };
            group.messages.push(message);
            if (!group.userMessage && String(message.role || "").toUpperCase() === "USER") {
                group.userMessage = message;
                group.label = String(message.content || "").replace(/\s+/g, " ").slice(0, 90) || "空用户消息";
            }
            group.createdAt = group.createdAt || message.createdAt || message.created_at;
            groups.set(id, group);
        });
        return [...groups.values()].sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
    }
    function groupChildLifecycle(events, assignments) {
        const byId = new Map();
        list(assignments).forEach((task, index) => {
            const id = task.childRunId || task.child_run_id || task.runId;
            if (id) byId.set(String(id), { childRunId: String(id), assignment: task, events: [], actions: [], result: "", status: "PENDING" });
            else byId.set(`task-${index}`, { childRunId: "", assignment: task, events: [], actions: [], result: "", status: "PENDING" });
        });
        list(events).forEach(event => {
            const id = event.childRunId || event.child_run_id || event.payload?.childRunId;
            if (!id) return;
            const lane = byId.get(String(id)) || { childRunId: String(id), assignment: null, events: [], actions: [], result: "", status: "PENDING" };
            lane.events.push(event);
            const type = String(event.type || event.eventType || "").toUpperCase();
            if (type.includes("ACTION")) lane.actions.push({ loopIndex: event.loopIndex, action: event.action || event.payload?.action, status: event.status });
            if (type.includes("COMMITTED") || type === "COMMIT") { lane.status = "COMMITTED"; lane.result = event.commit?.result || event.payload?.commit?.result || event.result || lane.result; }
            if (type.includes("FAILED")) lane.status = "FAILED";
        });
        return [...byId.values()];
    }
    function enrichGraphNodeDetails(nodes, loops) {
        const loopByIndex = new Map(list(loops).map(loop => [String(loop?.loopIndex), loop]));
        return list(nodes).map(node => {
            const loop = loopByIndex.get(String(node?.loopIndex));
            if (!loop) return { ...node, details: { ...(node?.details || {}) } };
            return { ...node, details: { ...loop, ...(node?.details || {}) } };
        });
    }
    function collectDebugFailures(details, node, events) {
        const source = details || {};
        const nodeType = String(node?.type || "").toUpperCase();
        const failures = [];
        const seen = new Set();
        const records = value => Array.isArray(value) ? value : value && typeof value === "object" ? Object.values(value) : [];
        const bad = value => /FAIL|ERROR|REJECT|BLOCK|TIMEOUT|INVALID|UNAVAILABLE|SATURATED/.test(String(value || "").toUpperCase());
        const inferredCode = message => {
            const text = String(message || "");
            const contractCode = text.match(/ContractViolation\(code=([A-Z0-9_]+)/i)?.[1];
            if (contractCode) return contractCode.toUpperCase();
            if (/MysqlDataTruncation|Data too long for column/i.test(text)) return "MYSQL_DATA_TRUNCATION";
            if (/timed?\s*out|timeout/i.test(text)) return "TIMEOUT";
            if (/schema/i.test(text)) return "SCHEMA_VALIDATION_FAILED";
            if (/contract/i.test(text)) return "CONTRACT_VIOLATION";
            return "UNCLASSIFIED_FAILURE";
        };
        function add(origin, value, fallback) {
            if (value == null || value === "") return;
            const item = typeof value === "object" ? value : { message: String(value) };
            if (typeof value === "object" && !Object.keys(value).length) return;
            const nested = item.error && typeof item.error === "object" ? item.error : {};
            const receipt = item.receipt && typeof item.receipt === "object" ? item.receipt : {};
            const explicitSignal = typeof value !== "object" || item.errorCode || item.failureCode || item.code || item.failureType
                || item.failureMessage || item.errorMessage || item.message || item.summary
                || item.parseResult?.errorCode || item.parseResult?.errorMessage
                || item.validationResult?.errorCode || item.validationResult?.errorMessage
                || receipt.errorCode || receipt.errorMessage || nested.code || nested.errorCode || nested.message || nested.errorMessage;
            if (!explicitSignal && !fallback?.allowStatusOnly) return;
            const status = item.status || item.callStatus || nested.status || fallback?.status || "FAILED";
            const message = item.failureMessage || item.errorMessage || item.message || item.summary
                || item.parseResult?.errorMessage || item.validationResult?.errorMessage
                || receipt.errorMessage || receipt.message || nested.message || nested.errorMessage || fallback?.message || "Failure details were recorded.";
            const messageCode = inferredCode(message);
            const code = item.errorCode || item.failureCode
                || item.parseResult?.errorCode || item.validationResult?.errorCode
                || receipt.errorCode || receipt.failureCode || nested.errorCode || nested.code
                || (messageCode !== "UNCLASSIFIED_FAILURE" ? messageCode : null)
                || item.failureType || item.code || fallback?.code || messageCode;
            if (!bad(status) && code === "UNCLASSIFIED_FAILURE" && !bad(code) && !fallback?.force) return;
            const key = [code, message].join("|");
            if (seen.has(key)) return;
            seen.add(key);
            failures.push({ source: origin, stage: item.stage || fallback?.stage || node?.type || "RUNTIME", code, status, message, detail: value });
        }
        const nodeIsBad = bad(node?.status) || (!node?.status && bad(node?.severity));
        const mainNode = nodeType === "MAIN_NODE";
        const llmNode = mainNode || nodeType === "CONTEXT_PLANNER";
        const actionNode = ["TOOL_USE", "ASK_USER", "DELEGATE", "RAG_RETRIEVAL", "READY_TO_DELIVER", "FINAL_DELIVERY", "RUNTIME_ACTION"].includes(nodeType);
        if (nodeType === "RUNTIME_FAILURE") add("Backend runtime", source, { force: true, stage: "BACKEND_RUNTIME" });
        if (nodeIsBad && !actionNode) add("Node error", source.error, { force: true });
        if (mainNode) {
            add("Action failure", source.input?.failure || source.actionInput?.failure, { force: true });
            add("State delta failure", source.output?.stateDelta?.failure || source.actionOutput?.stateDelta?.failure, { force: true });
            records(source.taskUpdate?.blockers).forEach(item => add("Task blocker", item, { force: true, code: "TASK_BLOCKER", status: "BLOCKED" }));
        }
        if (llmNode) records(source.attempts).filter(item => item?.success === false || item?.failureType || item?.failureMessage)
            .forEach(item => add("Model contract attempt", item, { force: true, stage: "CONTRACT" }));
        if (actionNode && bad(source.runtimeOutcome?.status)) add("Runtime outcome", source.runtimeOutcome, { force: true });
        if (actionNode) records(source.runtimeOutcome?.blockers).forEach(item => add("Runtime blocker", item, { force: true, code: "RUNTIME_BLOCKER", status: "BLOCKED" }));
        if (nodeType === "TOOL_USE") records(source.toolResults).filter(item => bad(item?.status || item?.callStatus) || item?.isError || item?.errorCode || item?.failureCode || item?.receipt?.errorCode)
            .forEach(item => add("Tool receipt", item, { force: true, stage: "TOOL_USE" }));
        if (nodeType === "DELEGATE") records(source.childAgentResults).filter(item => bad(item?.status || item?.commitStatus) || item?.failureMessage || item?.errorCode)
            .forEach(item => add("Child agent", item, { force: true, stage: "DELEGATE" }));
        if (["READY_TO_DELIVER", "FINAL_DELIVERY"].includes(nodeType) && bad(source.finalDelivery?.guard?.status)) add("Final guard", source.finalDelivery.guard, { force: true, stage: "FINAL_GUARD" });
        if (["READY_TO_DELIVER", "FINAL_DELIVERY"].includes(nodeType) && bad(source.guard?.status)) add("Final guard", source.guard, { force: true, stage: "FINAL_GUARD" });
        function eventOwnerType(event, payload) {
            const type = String(event?.eventType || event?.type || "").toUpperCase();
            const subAgentType = String(payload?.subAgentEventType || "").toUpperCase();
            const component = String(payload?.componentCode || payload?.component || payload?.nodeCode || "").toUpperCase();
            const action = String(payload?.action || payload?.actionType || "").toUpperCase();
            if (type.includes("CHILD_AGENT") || type.includes("SUB_AGENT") || subAgentType.includes("CHILD_AGENT") || subAgentType.includes("SUB_AGENT") || component.includes("SUB_AGENT")) return "DELEGATE";
            if (type.includes("TOOL") || type.includes("MCP") || component.includes("TOOL")) return "TOOL_USE";
            if (type.includes("RAG") || component.includes("RAG")) return "RAG_RETRIEVAL";
            if (type.includes("ASK_USER") || type.includes("CHECKPOINT") || type.includes("PENDING_INPUT")) return "ASK_USER";
            if (type.includes("FINAL") || type.includes("GUARD") || component.includes("FINAL")) return "FINAL_DELIVERY";
            if (component.includes("CONTEXT_PLANNER")) return "CONTEXT_PLANNER";
            if (component.includes("MAIN_AGENT") || component.includes("MAIN_NODE") || type.includes("MAIN_AGENT")) return "MAIN_NODE";
            const actionOwner = { CALL_TOOL: "TOOL_USE", ASK_USER: "ASK_USER", DELEGATE_AGENTS: "DELEGATE", RETRIEVE_RAG: "RAG_RETRIEVAL", READY_TO_DELIVER: "READY_TO_DELIVER", FINAL: "FINAL_DELIVERY" }[action];
            return actionOwner || "RUNTIME_ACTION";
        }
        records(events).forEach(event => {
            const payload = event?.safePayload || event?.payload || {};
            const type = event?.eventType || event?.type;
            const eventNodeId = payload.graphNodeId || payload.nodeId;
            const sameNode = eventNodeId ? String(eventNodeId) === String(node?.id) : eventOwnerType(event, payload) === nodeType;
            const sameChild = nodeType === "DELEGATE" && payload.childRunId && records(source.childAgentResults).some(item => String(item?.childRunId || item?.child_run_id || item?.runId || "") === String(payload.childRunId));
            const sameLoop = payload.loopIndex == null ? node?.loopIndex == null || nodeIsBad || sameChild : String(payload.loopIndex) === String(node?.loopIndex);
            if (sameNode && sameLoop && (bad(type) || bad(payload.status) || payload.failureMessage || payload.errorCode)) {
                add("Lifecycle event", { ...payload, status: payload.status || type, message: payload.failureMessage || payload.message || event?.summary }, { force: true, stage: payload.phase || node?.type });
            }
        });
        if (!failures.length && nodeIsBad) add("Node status", { status: node.status || node.severity, message: node.summary }, { force: true, allowStatusOnly: true });
        failures.sort((left, right) => {
            const score = item => item.code === "UNCLASSIFIED_FAILURE" ? 2 : item.status === "BLOCKED" ? 1 : 0;
            return score(left) - score(right);
        });
        return failures;
    }
    function buildFailureIndex(nodes, events) {
        const index = [];
        const seen = new Set();
        list(nodes).forEach((node, position) => {
            collectDebugFailures(node?.details, node, events).forEach(failure => {
                const key = [node?.id, failure.source, failure.code, failure.message].join("|");
                if (seen.has(key)) return;
                seen.add(key);
                index.push({ ...failure, nodeId: node?.id, nodeType: node?.type, nodeTitle: node?.title, loopIndex: node?.loopIndex, nodePosition: position });
            });
        });
        return index.sort((left, right) => Number(left.loopIndex ?? -1) - Number(right.loopIndex ?? -1) || left.nodePosition - right.nodePosition);
    }
    function groupExecutionLanes(nodes) {
        const lanes = [];
        const byKey = new Map();
        list(nodes).forEach((node, index) => {
            const isPrep = node?.loopIndex == null && ["CONTEXT_PREPARE", "CONTEXT_PLANNER"].includes(String(node?.type || "").toUpperCase());
            const previousLoop = [...lanes].reverse().find(item => item.loopIndex != null)?.loopIndex;
            const effectiveLoop = node?.loopIndex == null && !isPrep ? previousLoop : node?.loopIndex;
            const key = effectiveLoop == null ? "prep" : `loop-${effectiveLoop}`;
            let lane = byKey.get(key);
            if (!lane) {
                lane = { key, loopIndex: effectiveLoop ?? null, nodes: [], firstNodeId: "", lastNodeId: "" };
                byKey.set(key, lane);
                lanes.push(lane);
            }
            lane.nodes.push(node);
            lane.firstNodeId ||= String(node?.id || index);
            lane.lastNodeId = String(node?.id || index);
        });
        return lanes;
    }
    function nextFollowState(current, cause) {
        if (cause === "FOLLOW_ENABLE") return true;
        if (["USER_PAN", "USER_ZOOM", "USER_INSPECT", "USER_SELECT", "USER_DETAIL_SCROLL"].includes(cause)) return false;
        return Boolean(current);
    }
    return { mergeEffectivePlan, groupSessionRuns, groupChildLifecycle, enrichGraphNodeDetails, collectDebugFailures, buildFailureIndex, groupExecutionLanes, nextFollowState, isObsolete: value => obsoleteOf(value, new Set()) };
});

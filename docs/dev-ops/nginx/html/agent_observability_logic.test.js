const { mergeEffectivePlan, groupSessionRuns, groupChildLifecycle, enrichGraphNodeDetails, collectDebugFailures, buildFailureIndex, groupExecutionLanes, nextFollowState } = require('./agent_observability_logic');

const plan = mergeEffectivePlan(
  { steps: [{ id: 's1', title: '旧步骤', status: 'PENDING', affectedDeliverableIds: ['d1'] }], deliverables: [{ id: 'd1', title: '交付物', status: 'PENDING', relatedStepIds: ['s1'] }] },
  { stepUpdates: [{ id: 's1', status: 'COMPLETED' }, { id: 's2', title: '新步骤', status: 'CANCELLED' }], deliverableUpdates: [{ id: 'd1', status: 'READY' }] }
);
if (plan.steps[0].status !== 'COMPLETED' || !plan.steps[0].changed || !plan.steps[1].obsolete) throw new Error('effective plan merge failed');
if (plan.steps[0].attachedDeliverables[0].status !== 'READY') throw new Error('deliverable attachment failed');
const replaced = mergeEffectivePlan({ steps: [{ stepId: 'old', status: 'PENDING' }] }, { planRevision: { cancelledStepIds: ['old'] } });
if (!replaced.steps[0].obsolete) throw new Error('cancelled step revision failed');
const runs = groupSessionRuns([{ runId: 'r1', role: 'USER', content: '第一问' }, { runId: 'r1', role: 'ASSISTANT', content: '答复' }]);
if (runs[0].label !== '第一问') throw new Error('run grouping failed');
const lanes = groupChildLifecycle([
  { type: 'CHILD_STARTED', childRunId: 'c1' },
  { type: 'CHILD_ACTION', childRunId: 'c1', loopIndex: 1, action: 'COMMIT' },
  { type: 'CHILD_COMMITTED', childRunId: 'c1', commit: { result: '完整结果' } }
], [{ taskId: 't1', childRunId: 'c1', name: '规划' }]);
if (lanes[0].assignment.name !== '规划' || lanes[0].result !== '完整结果') throw new Error('child lane projection failed');
const safePayloadLane = groupChildLifecycle([
  { type: 'CHILD_AGENT_ACTION', childRunId: 'c2', loopIndex: 1, action: 'COMMIT' },
  { type: 'CHILD_AGENT_COMMITTED', childRunId: 'c2', commit: { result: '正文' } }
], [{ taskId: 't2', childRunId: 'c2', name: '正文任务' }])[0];
if (safePayloadLane.status !== 'COMMITTED' || safePayloadLane.actions[0].action !== 'COMMIT') throw new Error('safe payload lifecycle failed');
const enrichedReady = enrichGraphNodeDetails(
  [{ type: 'READY_TO_DELIVER', loopIndex: 1, details: { runtimeOutcome: { status: 'CONTINUE_LOOP' } } }],
  [{ loopIndex: 1, taskLedger: { goal: 'deliver complete article', steps: [{ id: 'write', title: 'Write article' }] }, taskUpdate: { stepUpdates: [{ id: 'write', status: 'COMPLETED' }] } }]
)[0];
if (enrichedReady.details.taskLedger.goal !== 'deliver complete article' || enrichedReady.details.runtimeOutcome.status !== 'CONTINUE_LOOP') {
  throw new Error('graph node loop context enrichment failed');
}
const failures = collectDebugFailures({ toolResults: [{ status: 'NOT_CALLED', receipt: { errorCode: 'TOOL_CLIENT_UNAVAILABLE', errorMessage: 'client unavailable' } }] }, { type: 'TOOL_USE' }, []);
if (failures[0].code !== 'TOOL_CLIENT_UNAVAILABLE' || failures[0].source !== 'Tool receipt') throw new Error('debug failure projection failed');
const contractMessage = '[ContractViolation(code=MISSING_DELEGATE_TASK_REQUESTED_CAPABILITIES, field=stateDelta.delegateAgentsRequest.tasks[0].requestedCapabilities)]';
const failureNodes = [
  { id: 'main-1', type: 'MAIN_NODE', loopIndex: 1, status: 'SUCCEEDED', details: { attempts: [{ success: false, failureType: 'CONTRACT_VIOLATION', failureMessage: contractMessage }] } },
  { id: 'delegate-1', type: 'DELEGATE', loopIndex: 1, status: 'FAILED', details: {} },
  { id: 'state-1', type: 'STATE_VIEW', loopIndex: 1, status: 'SUCCEEDED', details: {} }
];
const childFailureEvent = { eventType: 'STATUS_CHANGED', safePayload: { loopIndex: 1, subAgentEventType: 'CHILD_AGENT_FAILED', childRunId: 'child-1', status: 'FAILED', errorCode: 'CHILD_AGENT_DATABASE_ERROR', failureMessage: 'child result persistence failed' } };
const mainFailures = collectDebugFailures(failureNodes[0].details, failureNodes[0], [childFailureEvent]);
const delegateFailures = collectDebugFailures(failureNodes[1].details, failureNodes[1], [childFailureEvent]);
const stateFailures = collectDebugFailures(failureNodes[2].details, failureNodes[2], [childFailureEvent]);
if (mainFailures.length !== 1 || mainFailures[0].code !== 'MISSING_DELEGATE_TASK_REQUESTED_CAPABILITIES') throw new Error('main-node contract failure ownership failed');
if (delegateFailures.length !== 1 || delegateFailures[0].code !== 'CHILD_AGENT_DATABASE_ERROR') throw new Error('child lifecycle failure ownership failed');
if (stateFailures.length !== 0) throw new Error('unrelated state-view failure leakage');
const failureIndex = buildFailureIndex(failureNodes, [childFailureEvent]);
if (failureIndex.length !== 2 || failureIndex[0].loopIndex !== 1 || !failureIndex.every(item => item.nodeId)) throw new Error('global failure index projection failed');
const runtimeFailures = collectDebugFailures(
  { error: { message: 'child result persistence failed', errorCode: 'CHILD_AGENT_DATABASE_ERROR' }, runtimeOutcome: { status: 'FAILED', message: 'runtime stopped after delegated action failed' } },
  { id: 'runtime-1', type: 'RUNTIME_ACTION', loopIndex: 1, status: 'FAILED' },
  []
);
if (runtimeFailures.length !== 1 || runtimeFailures[0].source !== 'Runtime outcome') throw new Error('runtime node leaked delegated root failure');
const fatalRunFailures = collectDebugFailures(
  { failureCode: 'BACKEND_OUT_OF_MEMORY', failureOrigin: 'BACKEND_RUNTIME', terminal: true },
  { id: 'run-level-failure', type: 'RUNTIME_FAILURE', loopIndex: 1, status: 'FAILED', summary: 'JVM 内存耗尽，本次运行已终止' },
  []
);
if (fatalRunFailures.length !== 1 || fatalRunFailures[0].code !== 'BACKEND_OUT_OF_MEMORY') throw new Error('run-level fatal failure code was not projected');
const executionLanes = groupExecutionLanes([
  { id: 'prep', type: 'CONTEXT_PREPARE', loopIndex: null },
  { id: 'main-0', type: 'MAIN_NODE', loopIndex: 0 },
  { id: 'tool-0', type: 'TOOL_USE', loopIndex: 0 },
  { id: 'state-1', type: 'STATE_VIEW', loopIndex: 1 }
]);
if (executionLanes.length !== 3) throw new Error('execution lane grouping failed');
if (executionLanes[1].lastNodeId !== 'tool-0' || executionLanes[2].firstNodeId !== 'state-1') {
  throw new Error('inter-loop connector anchors failed');
}
if (nextFollowState(true, 'DATA_REFRESH') !== true) throw new Error('refresh changed follow state');
if (nextFollowState(true, 'USER_INSPECT') !== false) throw new Error('inspection did not suspend follow');
const fs = require('fs');
const path = require('path');
const studioHtml = fs.readFileSync(path.join(__dirname, 'agent_observability.html'), 'utf8');
const runtimeHtml = fs.readFileSync(path.join(__dirname, 'agent_runtime.html'), 'utf8');
const pointerDownHandler = studioHtml.match(/scroll\.addEventListener\("pointerdown"[^\n]+/)?.[0] || '';
const pointerMoveHandler = studioHtml.match(/scroll\.addEventListener\("pointermove"[^\n]+/)?.[0] || '';
if (pointerDownHandler.includes('setPointerCapture')) throw new Error('node click is captured before drag intent');
if (!pointerMoveHandler.includes('!dragging&&Math.hypot(deltaX,deltaY)>3') || !pointerMoveHandler.includes('setPointerCapture')) throw new Error('drag intent arbitration is missing');
if (!studioHtml.includes('patchSelectedDetail')) throw new Error('keyed detail patching is missing');
if (!studioHtml.includes('captureSelectionState')) throw new Error('selection preservation is missing');
if (!studioHtml.includes('bindDisclosureInteractions')) throw new Error('copy-safe disclosure binding is missing');
if (studioHtml.includes('body.addEventListener("click"')) throw new Error('expanded body remains a close target');
for (const token of ['execution-lane', 'lane-connector-layer', 'renderLoopConnectorPaths', 'data-lane-key']) {
  if (!studioHtml.includes(token)) throw new Error(`loop-lane rendering is missing ${token}`);
}
for (const token of ['command-deck', 'agent-rail', 'agent-pulse', 'run-telemetry', 'follow-latest', 'graph-expanded', 'new-node-indicator']) {
  if (!studioHtml.includes(token)) throw new Error(`command deck is missing ${token}`);
}
if (studioHtml.includes('.graph-shell>.failure-index{position:absolute')) throw new Error('failure index still overlays the execution graph');
if (!studioHtml.includes('scheduleLiveRefresh') || !studioHtml.includes('LIVE_REFRESH_INTERVAL_MS')) throw new Error('live snapshot fallback refresh is missing');
if (!studioHtml.includes('applyRunSignal') || !runtimeHtml.includes('publishObservabilityRun')) throw new Error('new chat run is not handed off to the open studio');
console.log('logic projections pass');

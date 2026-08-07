package yhx.com.domain.agent.service.agent;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentContinuationVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentRunCommandVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentRunResultVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentCommitVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextVO;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenericSubAgentRuntime {

    private static final int DEFAULT_MAX_LOOP = 25;

    private final ParentChildRunRegistry registry;
    private final SubAgentFullContextRecorder contextRecorder;
    private final GenericSubAgentNodePort nodePort;
    private final SubAgentActionDispatcher actionDispatcher;
    private final SubAgentActionPolicy actionPolicy;
    private final SubAgentLifecycleEventPublisher lifecycleEventPublisher;

    public GenericSubAgentRuntime(ParentChildRunRegistry registry,
                                  SubAgentFullContextRecorder contextRecorder,
                                  GenericSubAgentNodePort nodePort) {
        this(registry, contextRecorder, nodePort, null);
    }

    public GenericSubAgentRuntime(ParentChildRunRegistry registry,
                                  SubAgentFullContextRecorder contextRecorder,
                                  GenericSubAgentNodePort nodePort,
                                  SubAgentActionDispatcher actionDispatcher) {
        this(registry, contextRecorder, nodePort, actionDispatcher, new SubAgentActionPolicy());
    }

    public GenericSubAgentRuntime(ParentChildRunRegistry registry,
                                  SubAgentFullContextRecorder contextRecorder,
                                  GenericSubAgentNodePort nodePort,
                                  SubAgentActionDispatcher actionDispatcher,
                                  SubAgentActionPolicy actionPolicy) {
        this(registry, contextRecorder, nodePort, actionDispatcher, actionPolicy, null);
    }

    public GenericSubAgentRuntime(ParentChildRunRegistry registry,
                                  SubAgentFullContextRecorder contextRecorder,
                                  GenericSubAgentNodePort nodePort,
                                  SubAgentActionDispatcher actionDispatcher,
                                  SubAgentActionPolicy actionPolicy,
                                  SubAgentLifecycleEventPublisher lifecycleEventPublisher) {
        this.registry = registry == null ? new ParentChildRunRegistry() : registry;
        this.contextRecorder = contextRecorder == null ? new SubAgentFullContextRecorder() : contextRecorder;
        if (nodePort == null) {
            throw new IllegalArgumentException("GenericSubAgentNodePort is required.");
        }
        this.nodePort = nodePort;
        this.actionDispatcher = actionDispatcher == null ? SubAgentActionDispatcher.defaultDispatcher(this.registry) : actionDispatcher;
        this.actionPolicy = actionPolicy == null ? new SubAgentActionPolicy() : actionPolicy;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
    }

    public GenericSubAgentRunResultVO run(GenericSubAgentRunCommandVO command) {
        validate(command);
        ParentChildRunRelationVO relation = command.getRelation();
        DelegateAgentTaskVO task = command.getTask();
        SubAgentFullContextVO fullContext = contextRecorder.start(
                relation.getChildRunId(),
                relation.getParentRunId(),
                relation.getTaskId(),
                parentTaskContent(task, command.getEffectiveCapabilityCodes(), command.getInitialContext(),
                        command.getAvailableMcpTools()));
        return execute(command, fullContext, 0, null);
    }

    public GenericSubAgentRunResultVO resume(GenericSubAgentContinuationVO continuation, UserAnswerVO answer) {
        if (continuation == null || continuation.getCommand() == null) {
            throw new IllegalArgumentException("Generic subagent continuation and command are required.");
        }
        validate(continuation.getCommand());
        SubAgentFullContextVO fullContext = resolveFullContext(continuation);
        contextRecorder.append(fullContext, "USER_ANSWER", JSON.toJSONString(answer));
        return execute(continuation.getCommand(), fullContext, continuation.getLoopCount() == null ? 0 : continuation.getLoopCount(), answer);
    }

    private GenericSubAgentRunResultVO execute(GenericSubAgentRunCommandVO command,
                                               SubAgentFullContextVO fullContext,
                                               int completedLoopCount,
                                               UserAnswerVO answer) {
        ParentChildRunRelationVO relation = command.getRelation();

        int maxLoop = maxLoop(command.getProfile());
        for (int loop = completedLoopCount + 1; loop <= maxLoop; loop++) {
            SubAgentActionVO action = nodePort.invoke(fullContext);
            contextRecorder.append(fullContext, "NODE_ACTION", JSON.toJSONString(action));
            if (lifecycleEventPublisher != null) {
                lifecycleEventPublisher.action(relation.getParentRunId(), relation, action, loop);
            }
            String policyFailure = actionPolicy.validate(command.getProfile(), command.getEffectiveCapabilityCodes(), action)
                    .orElse(null);
            if (policyFailure != null) {
                contextRecorder.append(fullContext, "POLICY_FAILURE", policyFailure);
                return fail(relation, policyFailure, fullContext, loop);
            }
            SubAgentActionHandlerResultVO handlerResult = actionDispatcher.dispatch(SubAgentActionExecutionContextVO.builder()
                    .relation(relation)
                    .command(command)
                    .fullContext(fullContext)
                    .loopIndex(loop)
                    .build(), action);
            contextRecorder.append(fullContext, "HANDLER_RESULT", JSON.toJSONString(handlerResult));
            if (lifecycleEventPublisher != null) {
                lifecycleEventPublisher.handlerResult(relation.getParentRunId(), relation, handlerResult, loop);
            }

            if (!handlerResult.isTerminal()) {
                if (handlerResult.getMessage() != null && !handlerResult.getMessage().isBlank()) {
                    contextRecorder.append(fullContext, "RUNTIME_NOTE", handlerResult.getMessage());
                }
                continue;
            }
            if (ChildAgentRunStatusEnumVO.COMMITTED == handlerResult.getStatus()) {
                contextRecorder.append(fullContext, "COMMIT", JSON.toJSONString(handlerResult.getCommit()));
            } else if (ChildAgentRunStatusEnumVO.WAITING_USER == handlerResult.getStatus()) {
                Map<String, Object> waitingUser = new LinkedHashMap<>();
                waitingUser.put("pendingInputId", handlerResult.getPendingInputId());
                waitingUser.put("message", handlerResult.getMessage());
                contextRecorder.append(fullContext, "WAITING_USER", JSON.toJSONString(waitingUser));
                registry.saveContinuation(GenericSubAgentContinuationVO.builder()
                        .parentRunId(relation.getParentRunId())
                        .childRunId(relation.getChildRunId())
                        .taskId(relation.getTaskId())
                        .command(command)
                        .fullContext(fullContext)
                        .fullContextSnapshotRef(fullContext.getSnapshotRef())
                        .loopCount(loop)
                        .pendingInputId(handlerResult.getPendingInputId())
                        .build());
            } else {
                contextRecorder.append(fullContext, "FAIL", handlerResult.getFailureMessage());
            }
            return result(relation, handlerResult, loop, fullContext);
        }
        return fail(relation, "Generic subagent exceeded max loop count.", fullContext, maxLoop);
    }

    private GenericSubAgentRunResultVO fail(ParentChildRunRelationVO relation,
                                            String failureMessage,
                                            SubAgentFullContextVO fullContext,
                                            int loopCount) {
        contextRecorder.append(fullContext, "FAIL", failureMessage);
        registry.markFailed(relation.getChildRunId(), failureMessage);
        return result(relation, ChildAgentRunStatusEnumVO.FAILED, null, failureMessage, loopCount, fullContext);
    }

    private GenericSubAgentRunResultVO result(ParentChildRunRelationVO relation,
                                              ChildAgentRunStatusEnumVO status,
                                              SubAgentCommitVO commit,
                                              String failureMessage,
                                              int loopCount,
                                              SubAgentFullContextVO fullContext) {
        syncFullContextSnapshotRef(relation, fullContext);
        return GenericSubAgentRunResultVO.builder()
                .parentRunId(relation.getParentRunId())
                .childRunId(relation.getChildRunId())
                .taskId(relation.getTaskId())
                .status(status)
                .commit(commit)
                .failureMessage(failureMessage)
                .loopCount(loopCount)
                .fullContext(fullContext)
                .build();
    }

    private GenericSubAgentRunResultVO result(ParentChildRunRelationVO relation,
                                              SubAgentActionHandlerResultVO handlerResult,
                                              int loopCount,
                                              SubAgentFullContextVO fullContext) {
        syncFullContextSnapshotRef(relation, fullContext);
        return GenericSubAgentRunResultVO.builder()
                .parentRunId(relation.getParentRunId())
                .childRunId(relation.getChildRunId())
                .taskId(relation.getTaskId())
                .status(handlerResult.getStatus())
                .commit(handlerResult.getCommit())
                .failureMessage(handlerResult.getFailureMessage())
                .pendingInputId(handlerResult.getPendingInputId())
                .askUserRequest(handlerResult.getAskUserRequest())
                .loopCount(loopCount)
                .fullContext(fullContext)
                .build();
    }

    private void syncFullContextSnapshotRef(ParentChildRunRelationVO relation, SubAgentFullContextVO fullContext) {
        if (relation != null && fullContext != null) {
            relation.setFullContextSnapshotRef(fullContext.getSnapshotRef());
        }
    }

    private SubAgentFullContextVO resolveFullContext(GenericSubAgentContinuationVO continuation) {
        return contextRecorder.load(continuation.getFullContextSnapshotRef())
                .or(() -> contextRecorder.load(snapshotRef(continuation.getFullContext())))
                .orElseGet(() -> {
                    if (continuation.getFullContext() == null) {
                        throw new IllegalArgumentException("Generic subagent continuation requires full context or snapshot ref.");
                    }
                    return continuation.getFullContext();
                });
    }

    private String snapshotRef(SubAgentFullContextVO fullContext) {
        return fullContext == null ? null : fullContext.getSnapshotRef();
    }

    private String parentTaskContent(DelegateAgentTaskVO task,
                                     Set<String> capabilities,
                                     Map<String, Object> initialContext,
                                     List<CapabilityCandidateVO> availableMcpTools) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("taskId", task.getTaskId());
        content.put("name", task.getName());
        content.put("objective", task.getObjective());
        content.put("boundary", task.getBoundary());
        content.put("requiredOutput", task.getRequiredOutput());
        content.put("requestedCapabilities", task.getRequestedCapabilities());
        content.put("effectiveCapabilities", capabilities);
        content.put("availableMcpTools", availableMcpTools == null ? List.of() : availableMcpTools);
        content.put("parentContext", task.getParentContext());
        content.put("initialContext", initialContext);
        return JSON.toJSONString(content);
    }

    private void validate(GenericSubAgentRunCommandVO command) {
        if (command == null) {
            throw new IllegalArgumentException("Generic subagent run command is required.");
        }
        ParentChildRunRelationVO relation = command.getRelation();
        if (relation == null || isBlank(relation.getParentRunId()) || isBlank(relation.getChildRunId()) || isBlank(relation.getTaskId())) {
            throw new IllegalArgumentException("Generic subagent run requires parent-child relation.");
        }
        DelegateAgentTaskVO task = command.getTask();
        if (task == null || isBlank(task.getTaskId()) || isBlank(task.getObjective())) {
            throw new IllegalArgumentException("Generic subagent run requires delegated task id and objective.");
        }
    }

    private int maxLoop(AgentProfileVO profile) {
        if (profile == null || profile.getMaxLoopCount() == null || profile.getMaxLoopCount() <= 0) {
            return DEFAULT_MAX_LOOP;
        }
        return profile.getMaxLoopCount();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

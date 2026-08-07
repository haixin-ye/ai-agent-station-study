package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.AgentDispatchResultVO;
import yhx.com.domain.agent.model.valobj.agent.AgentCapabilityResolutionCommandVO;
import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentsRequestVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentDispatchOrchestrationResultVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentOrchestrationResultVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentContinuationVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentRunCommandVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentProfileTypeEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.runtime.port.RagRuntimePort;
import yhx.com.domain.agent.service.runtime.port.ToolActionOrchestratorPort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public class GenericSubAgentDispatchOrchestrator {

    private final AgentDispatchRuntime dispatchRuntime;
    private final ParentChildRunRegistry registry;
    private final ChildAgentResultProjector projector;
    private final Map<String, GenericSubAgentNodePort> nodePortsByChildRunId;
    private final GenericSubAgentNodePort defaultNodePort;
    private final AgentProfileVO genericProfile;
    private final AgentCapabilityResolver capabilityResolver;
    private final ToolActionOrchestratorPort toolActionOrchestratorPort;
    private final RagRuntimePort ragRuntimePort;
    private final UserInteractionManager userInteractionManager;
    private final SubAgentFullContextStore fullContextStore;
    private final DelegateAgentsRequestValidator requestValidator;
    private final SubAgentLifecycleEventPublisher lifecycleEventPublisher;
    private final ParentRunResumePort parentRunResumePort;
    private final Executor childExecutor;

    public GenericSubAgentDispatchOrchestrator(AgentDispatchRuntime dispatchRuntime,
                                               ParentChildRunRegistry registry,
                                               ChildAgentResultProjector projector,
                                               Map<String, GenericSubAgentNodePort> nodePortsByChildRunId) {
        this(dispatchRuntime, registry, projector, nodePortsByChildRunId, (GenericSubAgentNodePort) null);
    }

    public GenericSubAgentDispatchOrchestrator(AgentDispatchRuntime dispatchRuntime,
                                               ParentChildRunRegistry registry,
                                               ChildAgentResultProjector projector,
                                               Map<String, GenericSubAgentNodePort> nodePortsByChildRunId,
                                               GenericSubAgentNodePort defaultNodePort) {
        this(dispatchRuntime, registry, projector, nodePortsByChildRunId, defaultNodePort, new AgentCapabilityResolver());
    }

    public GenericSubAgentDispatchOrchestrator(AgentDispatchRuntime dispatchRuntime,
                                               ParentChildRunRegistry registry,
                                               ChildAgentResultProjector projector,
                                               Map<String, GenericSubAgentNodePort> nodePortsByChildRunId,
                                               AgentCapabilityResolver capabilityResolver) {
        this(dispatchRuntime, registry, projector, nodePortsByChildRunId, null, capabilityResolver, null, null, null);
    }

    public GenericSubAgentDispatchOrchestrator(AgentDispatchRuntime dispatchRuntime,
                                               ParentChildRunRegistry registry,
                                               ChildAgentResultProjector projector,
                                               Map<String, GenericSubAgentNodePort> nodePortsByChildRunId,
                                               GenericSubAgentNodePort defaultNodePort,
                                               AgentCapabilityResolver capabilityResolver) {
        this(dispatchRuntime, registry, projector, nodePortsByChildRunId, defaultNodePort, capabilityResolver, null, null, null);
    }

    public GenericSubAgentDispatchOrchestrator(AgentDispatchRuntime dispatchRuntime,
                                               ParentChildRunRegistry registry,
                                               ChildAgentResultProjector projector,
                                               Map<String, GenericSubAgentNodePort> nodePortsByChildRunId,
                                               AgentCapabilityResolver capabilityResolver,
                                               ToolActionOrchestratorPort toolActionOrchestratorPort,
                                               RagRuntimePort ragRuntimePort,
                                               UserInteractionManager userInteractionManager) {
        this(dispatchRuntime, registry, projector, nodePortsByChildRunId, null, capabilityResolver,
                toolActionOrchestratorPort, ragRuntimePort, userInteractionManager, null);
    }

    public GenericSubAgentDispatchOrchestrator(AgentDispatchRuntime dispatchRuntime,
                                               ParentChildRunRegistry registry,
                                               ChildAgentResultProjector projector,
                                               Map<String, GenericSubAgentNodePort> nodePortsByChildRunId,
                                               GenericSubAgentNodePort defaultNodePort,
                                               AgentCapabilityResolver capabilityResolver,
                                               ToolActionOrchestratorPort toolActionOrchestratorPort,
                                               RagRuntimePort ragRuntimePort,
                                               UserInteractionManager userInteractionManager) {
        this(dispatchRuntime, registry, projector, nodePortsByChildRunId, defaultNodePort, capabilityResolver,
                toolActionOrchestratorPort, ragRuntimePort, userInteractionManager, null);
    }

    public GenericSubAgentDispatchOrchestrator(AgentDispatchRuntime dispatchRuntime,
                                               ParentChildRunRegistry registry,
                                               ChildAgentResultProjector projector,
                                               Map<String, GenericSubAgentNodePort> nodePortsByChildRunId,
                                               GenericSubAgentNodePort defaultNodePort,
                                               AgentCapabilityResolver capabilityResolver,
                                               ToolActionOrchestratorPort toolActionOrchestratorPort,
                                               RagRuntimePort ragRuntimePort,
                                               UserInteractionManager userInteractionManager,
                                               SubAgentFullContextStore fullContextStore) {
        this(dispatchRuntime, registry, projector, nodePortsByChildRunId, defaultNodePort, capabilityResolver,
                toolActionOrchestratorPort, ragRuntimePort, userInteractionManager, fullContextStore,
                null, null, Runnable::run);
    }

    public GenericSubAgentDispatchOrchestrator(AgentDispatchRuntime dispatchRuntime,
                                               ParentChildRunRegistry registry,
                                               ChildAgentResultProjector projector,
                                               Map<String, GenericSubAgentNodePort> nodePortsByChildRunId,
                                               GenericSubAgentNodePort defaultNodePort,
                                               AgentCapabilityResolver capabilityResolver,
                                               ToolActionOrchestratorPort toolActionOrchestratorPort,
                                               RagRuntimePort ragRuntimePort,
                                               UserInteractionManager userInteractionManager,
                                               SubAgentFullContextStore fullContextStore,
                                               SubAgentLifecycleEventPublisher lifecycleEventPublisher,
                                               ParentRunResumePort parentRunResumePort,
                                               Executor childExecutor) {
        this.dispatchRuntime = dispatchRuntime;
        this.registry = registry;
        this.projector = projector;
        this.nodePortsByChildRunId = nodePortsByChildRunId == null ? Map.of() : Map.copyOf(nodePortsByChildRunId);
        this.defaultNodePort = defaultNodePort;
        this.genericProfile = AgentProfileRegistry.defaultRegistry().requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);
        this.capabilityResolver = capabilityResolver == null ? new AgentCapabilityResolver() : capabilityResolver;
        this.toolActionOrchestratorPort = toolActionOrchestratorPort;
        this.ragRuntimePort = ragRuntimePort;
        this.userInteractionManager = userInteractionManager;
        this.fullContextStore = fullContextStore;
        this.requestValidator = new DelegateAgentsRequestValidator(this.capabilityResolver);
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.parentRunResumePort = parentRunResumePort;
        this.childExecutor = Objects.requireNonNull(childExecutor, "Generic subagent Executor is required.");
    }

    public GenericSubAgentDispatchOrchestrationResultVO dispatchRunAndProject(RuntimeExecutionContext parentContext,
                                                                              DelegateAgentsRequestVO request) {
        GenericSubAgentDispatchOrchestrationResultVO prepared = prepareDispatch(parentContext, request);
        startPreparedDispatch(parentContext, request, prepared);
        return prepared;
    }

    public GenericSubAgentDispatchOrchestrationResultVO prepareDispatch(RuntimeExecutionContext parentContext,
                                                                        DelegateAgentsRequestVO request) {
        validate(parentContext, request);
        AgentDispatchResultVO dispatchResult = dispatchRuntime.dispatch(parentContext.getRunId(), request);
        if (lifecycleEventPublisher != null) {
            lifecycleEventPublisher.dispatched(parentContext.getRunId(), dispatchResult, request.getTasks());
        }
        return GenericSubAgentDispatchOrchestrationResultVO.builder()
                .parentRunId(dispatchResult.getParentRunId())
                .waitMode(dispatchResult.getWaitMode())
                .childRunIds(dispatchResult.getChildRunIds())
                .childResults(List.of())
                .parentReady(false)
                .build();
    }

    public void startPreparedDispatch(RuntimeExecutionContext parentContext,
                                      DelegateAgentsRequestVO request,
                                      GenericSubAgentDispatchOrchestrationResultVO prepared) {
        validate(parentContext, request);
        if (prepared == null
                || !Objects.equals(parentContext.getRunId(), prepared.getParentRunId())
                || prepared.getChildRunIds() == null
                || prepared.getChildRunIds().isEmpty()) {
            throw new IllegalArgumentException("Prepared child dispatch is missing or belongs to another parent Run.");
        }
        List<ParentChildRunRelationVO> relations = new ArrayList<>();
        List<DelegateAgentTaskVO> tasks = new ArrayList<>();
        for (String childRunId : prepared.getChildRunIds()) {
            ParentChildRunRelationVO relation = registry.findByChildRunId(childRunId)
                    .orElseThrow(() -> new IllegalArgumentException("Child relation is missing for dispatched child: " + childRunId));
            if (!Objects.equals(parentContext.getRunId(), relation.getParentRunId())) {
                throw new IllegalArgumentException("Prepared child relation belongs to another parent Run.");
            }
            relations.add(relation);
            tasks.add(findTask(request, relation.getTaskId()));
        }
        for (int index = 0; index < relations.size(); index++) {
            startChildAsync(parentContext, relations.get(index), tasks.get(index));
        }
        if (lifecycleEventPublisher != null) {
            lifecycleEventPublisher.parentWaiting(parentContext.getRunId(), prepared.getChildRunIds());
        }
    }

    private void startChildAsync(RuntimeExecutionContext parentContext,
                                 ParentChildRunRelationVO relation,
                                 DelegateAgentTaskVO task) {
        registry.markRunning(relation.getChildRunId());
        if (lifecycleEventPublisher != null) {
            registry.findByChildRunId(relation.getChildRunId())
                    .ifPresent(latest -> lifecycleEventPublisher.started(parentContext.getRunId(), latest, task));
        }
        try {
            childExecutor.execute(() -> {
                try {
                    runOneChild(parentContext, relation, task);
                } catch (Throwable error) {
                    markChildFailed(relation.getChildRunId(),
                            error.getMessage() == null ? "Generic subagent failed unexpectedly." : error.getMessage());
                    if (error instanceof Error fatalError) {
                        throw fatalError;
                    }
                } finally {
                    publishTerminalAndResume(parentContext.getRunId(), relation.getChildRunId());
                }
            });
        } catch (RejectedExecutionException error) {
            markChildFailed(relation.getChildRunId(),
                    "Generic subagent execution was rejected by the configured executor.");
            publishTerminalAndResume(parentContext.getRunId(), relation.getChildRunId());
        }
    }

    private void markChildFailed(String childRunId, String message) {
        registry.markFailed(childRunId, message);
    }

    private void publishTerminalAndResume(String parentRunId, String childRunId) {
        ParentChildRunRelationVO relation = registry.findByChildRunId(childRunId).orElse(null);
        if (relation != null) {
            if (lifecycleEventPublisher != null && relation.getStatus() != null && relation.getStatus().terminal()) {
                lifecycleEventPublisher.terminal(parentRunId, relation);
            }
            resumeParentIfSatisfied(parentRunId, relation.getDispatchBatchId());
        }
    }

    private void resumeParentIfSatisfied(String parentRunId, String dispatchBatchId) {
        if (!registry.isWaitSatisfied(parentRunId, dispatchBatchId)
                || !registry.markParentResumeRequested(parentRunId, dispatchBatchId)) {
            return;
        }
        if (lifecycleEventPublisher != null) {
            lifecycleEventPublisher.parentReady(parentRunId);
        }
        if (parentRunResumePort != null) {
            boolean accepted = parentRunResumePort.resumeParentIfReady(parentRunId);
            if (!accepted) {
                registry.clearParentResumeRequested(parentRunId, dispatchBatchId);
            }
        }
    }

    private GenericSubAgentOrchestrationResultVO runOneChild(RuntimeExecutionContext parentContext,
                                                            ParentChildRunRelationVO relation,
                                                            DelegateAgentTaskVO task) {
        GenericSubAgentNodePort nodePort = nodePortFor(relation.getChildRunId());
        GenericSubAgentRuntime childRuntime = new GenericSubAgentRuntime(
                registry,
                new SubAgentFullContextRecorder(fullContextStore),
                nodePort,
                SubAgentActionDispatcher.runtimeDispatcher(registry, toolActionOrchestratorPort, ragRuntimePort, userInteractionManager),
                new SubAgentActionPolicy(),
                lifecycleEventPublisher);
        GenericSubAgentOrchestrator childOrchestrator = new GenericSubAgentOrchestrator(
                registry,
                childRuntime,
                new NoopChildAgentResultProjector());
        return childOrchestrator.runAndProject(parentContext, command(parentContext, relation, task));
    }

    public GenericSubAgentDispatchOrchestrationResultVO runDispatchedChildrenAndProject(RuntimeExecutionContext parentContext) {
        if (parentContext == null || parentContext.getRunId() == null || parentContext.getRunId().isBlank()) {
            throw new IllegalArgumentException("Parent runtime context with runId is required.");
        }
        registry.restoreParent(parentContext.getRunId());
        List<GenericSubAgentOrchestrationResultVO> results = new ArrayList<>();
        for (ParentChildRunRelationVO relation : registry.listChildren(parentContext.getRunId())) {
            if (relation.getStatus() != null && relation.getStatus().terminal()) {
                projector.project(parentContext, relation);
                results.add(GenericSubAgentOrchestrationResultVO.builder()
                        .parentRunId(relation.getParentRunId())
                        .childRunId(relation.getChildRunId())
                        .taskId(relation.getTaskId())
                        .childStatus(relation.getStatus())
                        .parentReady(registry.isWaitSatisfied(relation.getParentRunId()))
                        .build());
            }
        }
        return GenericSubAgentDispatchOrchestrationResultVO.builder()
                .parentRunId(parentContext.getRunId())
                .waitMode(results.stream().findFirst()
                        .flatMap(result -> registry.findByChildRunId(result.getChildRunId()))
                        .map(ParentChildRunRelationVO::getWaitMode)
                        .orElse(null))
                .childRunIds(registry.listChildren(parentContext.getRunId()).stream()
                        .map(ParentChildRunRelationVO::getChildRunId)
                        .toList())
                .childResults(results)
                .parentReady(registry.isWaitSatisfied(parentContext.getRunId()))
                .build();
    }

    public GenericSubAgentDispatchOrchestrationResultVO resumeChildAndProject(RuntimeExecutionContext parentContext,
                                                                              String childRunId,
                                                                              UserAnswerVO answer) {
        if (parentContext == null || parentContext.getRunId() == null || parentContext.getRunId().isBlank()) {
            throw new IllegalArgumentException("Parent runtime context with runId is required.");
        }
        if (childRunId == null || childRunId.isBlank()) {
            throw new IllegalArgumentException("Child run id is required.");
        }
        registry.restoreParent(parentContext.getRunId());
        ParentChildRunRelationVO relation = registry.findByChildRunId(childRunId)
                .orElseThrow(() -> new IllegalArgumentException("Child relation is missing for child run: " + childRunId));
        if (!Objects.equals(parentContext.getRunId(), relation.getParentRunId())) {
            throw new IllegalArgumentException("Child run " + childRunId
                    + " does not belong to parent Run " + parentContext.getRunId() + ".");
        }
        GenericSubAgentContinuationVO continuation = registry.findContinuation(childRunId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Generic subagent continuation is missing for child run: " + childRunId));
        validateContinuationIdentity(parentContext, relation, childRunId, answer);
        rebindParentRuntimeContext(parentContext, continuation);
        GenericSubAgentNodePort nodePort = nodePortFor(childRunId);
        GenericSubAgentRuntime childRuntime = new GenericSubAgentRuntime(
                registry,
                new SubAgentFullContextRecorder(fullContextStore),
                nodePort,
                SubAgentActionDispatcher.runtimeDispatcher(registry, toolActionOrchestratorPort, ragRuntimePort, userInteractionManager));
        GenericSubAgentOrchestrator childOrchestrator = new GenericSubAgentOrchestrator(
                registry,
                childRuntime,
                projector);
        GenericSubAgentOrchestrationResultVO childResult = childOrchestrator.resumeAndProject(parentContext, childRunId, answer);
        List<String> childRunIds = registry.listChildren(relation.getParentRunId()).stream()
                .map(ParentChildRunRelationVO::getChildRunId)
                .toList();
        return GenericSubAgentDispatchOrchestrationResultVO.builder()
                .parentRunId(relation.getParentRunId())
                .waitMode(relation.getWaitMode())
                .childRunIds(childRunIds)
                .childResults(List.of(childResult))
                .parentReady(registry.isWaitSatisfied(relation.getParentRunId()))
                .build();
    }

    /**
     * RuntimeExecutionContext is deliberately excluded from persisted child snapshots.
     * Rebind the live parent context before a resumed child can execute another action,
     * especially a second approval-gated MCP tool call.
     */
    private void rebindParentRuntimeContext(RuntimeExecutionContext parentContext,
                                             GenericSubAgentContinuationVO continuation) {
        if (continuation != null && continuation.getCommand() != null) {
            continuation.getCommand().setParentRuntimeContext(parentContext);
        }
    }

    private void validateContinuationIdentity(RuntimeExecutionContext parentContext,
                                                ParentChildRunRelationVO relation,
                                                String childRunId,
                                                UserAnswerVO answer) {
        GenericSubAgentContinuationVO continuation = registry.findContinuation(childRunId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Generic subagent continuation is missing for child run: " + childRunId));
        if (!Objects.equals(parentContext.getRunId(), continuation.getParentRunId())
                || !Objects.equals(childRunId, continuation.getChildRunId())
                || !Objects.equals(relation.getTaskId(), continuation.getTaskId())) {
            throw new IllegalArgumentException("Generic subagent continuation identity does not match the parent-child relation.");
        }
        if (continuation.getPendingInputId() != null
                && answer != null
                && answer.getPendingId() != null
                && !Objects.equals(continuation.getPendingInputId(), answer.getPendingId())) {
            throw new IllegalArgumentException("Generic subagent continuation belongs to another PendingInput.");
        }
    }

    private GenericSubAgentNodePort nodePortFor(String childRunId) {
        GenericSubAgentNodePort nodePort = nodePortsByChildRunId.get(childRunId);
        if (nodePort != null) {
            return nodePort;
        }
        if (defaultNodePort != null) {
            return defaultNodePort;
        }
        throw new IllegalArgumentException("GenericSubAgentNodePort is missing for child run: " + childRunId);
    }

    private GenericSubAgentRunCommandVO command(RuntimeExecutionContext parentContext, ParentChildRunRelationVO relation, DelegateAgentTaskVO task) {
        return GenericSubAgentRunCommandVO.builder()
                .relation(relation)
                .task(task)
                .profile(genericProfile)
                .effectiveCapabilityCodes(effectiveCapabilities(task))
                .availableMcpTools(availableMcpTools(parentContext))
                .initialContext(task.getParentContext())
                .sessionId(parentContext == null ? null : parentContext.getSessionId())
                .userId(parentContext == null ? null : parentContext.getUserId())
                .parentRuntimeContext(parentContext)
                .build();
    }

    private Set<String> effectiveCapabilities(DelegateAgentTaskVO task) {
        if (task == null || task.getRequestedCapabilities() == null) {
            return Set.of();
        }
        return capabilityResolver.resolve(AgentCapabilityResolutionCommandVO.builder()
                        .profile(genericProfile)
                        .requestedCapabilityCodes(new LinkedHashSet<>(task.getRequestedCapabilities()))
                        .workspaceScopePresent(workspaceScopePresent(task))
                        .build())
                .getEffectiveCapabilityCodes();
    }

    private List<CapabilityCandidateVO> availableMcpTools(RuntimeExecutionContext parentContext) {
        if (parentContext == null || parentContext.getRunContextState() == null
                || parentContext.getRunContextState().getBaseContext() == null
                || parentContext.getRunContextState().getBaseContext().getSelectedSessionContext() == null
                || parentContext.getRunContextState().getBaseContext().getSelectedSessionContext().getAvailableCapabilities() == null) {
            return List.of();
        }
        return parentContext.getRunContextState().getBaseContext().getSelectedSessionContext()
                .getAvailableCapabilities().stream()
                .filter(candidate -> candidate != null
                        && candidate.getMcpServerCode() != null && !candidate.getMcpServerCode().isBlank()
                        && candidate.getToolName() != null && !candidate.getToolName().isBlank())
                .toList();
    }

    private boolean workspaceScopePresent(DelegateAgentTaskVO task) {
        if (task == null || task.getParentContext() == null || task.getParentContext().isEmpty()) {
            return false;
        }
        return task.getParentContext().containsKey("workspace")
                || task.getParentContext().containsKey("currentWorkspace")
                || task.getParentContext().containsKey("workspaceScope");
    }

    private DelegateAgentTaskVO findTask(DelegateAgentsRequestVO request, String taskId) {
        return request.getTasks().stream()
                .filter(task -> taskId != null && taskId.equals(task.getTaskId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Delegated task is missing for child taskId: " + taskId));
    }

    private void validate(RuntimeExecutionContext parentContext, DelegateAgentsRequestVO request) {
        if (dispatchRuntime == null) {
            throw new IllegalArgumentException("AgentDispatchRuntime is required.");
        }
        if (registry == null) {
            throw new IllegalArgumentException("ParentChildRunRegistry is required.");
        }
        if (projector == null) {
            throw new IllegalArgumentException("ChildAgentResultProjector is required.");
        }
        if (parentContext == null || parentContext.getRunId() == null || parentContext.getRunId().isBlank()) {
            throw new IllegalArgumentException("Parent runtime context with runId is required.");
        }
        if (request == null || request.getTasks() == null || request.getTasks().isEmpty()) {
            throw new IllegalArgumentException("DelegateAgentsRequest with tasks is required.");
        }
        requestValidator.validate(request, genericProfile);
    }
}

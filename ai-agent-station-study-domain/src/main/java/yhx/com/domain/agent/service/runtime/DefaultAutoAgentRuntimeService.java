package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentDispatchOrchestrationResultVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionEffectStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateResult;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveCommand;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveResult;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeChildrenResumeCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeRecoveryCounters;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeResumeCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.interaction.ContextPlannerPendingInputHandler;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.agent.GenericSubAgentDispatchOrchestrator;
import yhx.com.domain.agent.service.agent.ParentChildRunRegistry;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DefaultAutoAgentRuntimeService implements AutoAgentRuntimeService {

    private final IConversationRepository conversationRepository;
    private final IRunRepository runRepository;
    private final IPayloadRepository payloadRepository;
    private final RuntimeComponentPorts componentPorts;
    private final MainActionDispatcher actionDispatcher;
    private final UserInteractionManager userInteractionManager;
    private final RuntimeLoopPolicy loopPolicy;
    private final RuntimeRoutePolicy routePolicy;
    private final RuntimeStateMachine stateMachine;
    private final RuntimeFailureFactory failureFactory;
    private final RuntimePhaseGuard phaseGuard;
    private final RunEventPublisher eventPublisher;
    private final RunTranscriptRecorder transcriptRecorder;
    private final DeveloperTraceRecorder traceRecorder;
    private final RunDiagnosticRecorder diagnosticRecorder;
    private final ParentChildRunRegistry parentChildRunRegistry;
    private final GenericSubAgentDispatchOrchestrator genericSubAgentDispatchOrchestrator;
    private final RunWorkingStateManager workingStateManager = new RunWorkingStateManager();

    public DefaultAutoAgentRuntimeService(IConversationRepository conversationRepository,
                                          IRunRepository runRepository,
                                          IPayloadRepository payloadRepository,
                                          RuntimeComponentPorts componentPorts,
                                          MainActionDispatcher actionDispatcher,
                                          UserInteractionManager userInteractionManager,
                                          RuntimeLoopPolicy loopPolicy,
                                          RuntimeStateMachine stateMachine,
                                          RuntimeFailureFactory failureFactory,
                                          RuntimePhaseGuard phaseGuard,
                                          RunEventPublisher eventPublisher,
                                          RunTranscriptRecorder transcriptRecorder,
                                          DeveloperTraceRecorder traceRecorder) {
        this(conversationRepository,
                runRepository,
                payloadRepository,
                componentPorts,
                actionDispatcher,
                userInteractionManager,
                loopPolicy,
                null,
                stateMachine,
                failureFactory,
                phaseGuard,
                eventPublisher,
                transcriptRecorder,
                traceRecorder,
                null,
                null,
                null);
    }

    public DefaultAutoAgentRuntimeService(IConversationRepository conversationRepository,
                                          IRunRepository runRepository,
                                          IPayloadRepository payloadRepository,
                                          RuntimeComponentPorts componentPorts,
                                          MainActionDispatcher actionDispatcher,
                                          UserInteractionManager userInteractionManager,
                                          RuntimeLoopPolicy loopPolicy,
                                          RuntimeStateMachine stateMachine,
                                          RuntimeFailureFactory failureFactory,
                                          RuntimePhaseGuard phaseGuard,
                                          RunEventPublisher eventPublisher,
                                          RunTranscriptRecorder transcriptRecorder,
                                          DeveloperTraceRecorder traceRecorder,
                                          RunDiagnosticRecorder diagnosticRecorder) {
        this(conversationRepository,
                runRepository,
                payloadRepository,
                componentPorts,
                actionDispatcher,
                userInteractionManager,
                loopPolicy,
                null,
                stateMachine,
                failureFactory,
                phaseGuard,
                eventPublisher,
                transcriptRecorder,
                traceRecorder,
                diagnosticRecorder,
                null,
                null);
    }

    public DefaultAutoAgentRuntimeService(IConversationRepository conversationRepository,
                                          IRunRepository runRepository,
                                          IPayloadRepository payloadRepository,
                                          RuntimeComponentPorts componentPorts,
                                          MainActionDispatcher actionDispatcher,
                                          UserInteractionManager userInteractionManager,
                                          RuntimeLoopPolicy loopPolicy,
                                          RuntimeRoutePolicy routePolicy,
                                          RuntimeStateMachine stateMachine,
                                          RuntimeFailureFactory failureFactory,
                                          RuntimePhaseGuard phaseGuard,
                                          RunEventPublisher eventPublisher,
                                          RunTranscriptRecorder transcriptRecorder,
                                          DeveloperTraceRecorder traceRecorder,
                                          RunDiagnosticRecorder diagnosticRecorder) {
        this(conversationRepository,
                runRepository,
                payloadRepository,
                componentPorts,
                actionDispatcher,
                userInteractionManager,
                loopPolicy,
                routePolicy,
                stateMachine,
                failureFactory,
                phaseGuard,
                eventPublisher,
                transcriptRecorder,
                traceRecorder,
                diagnosticRecorder,
                null,
                null);
    }

    public DefaultAutoAgentRuntimeService(IConversationRepository conversationRepository,
                                          IRunRepository runRepository,
                                          IPayloadRepository payloadRepository,
                                          RuntimeComponentPorts componentPorts,
                                          MainActionDispatcher actionDispatcher,
                                          UserInteractionManager userInteractionManager,
                                          RuntimeLoopPolicy loopPolicy,
                                          RuntimeStateMachine stateMachine,
                                          RuntimeFailureFactory failureFactory,
                                          RuntimePhaseGuard phaseGuard,
                                          RunEventPublisher eventPublisher,
                                          RunTranscriptRecorder transcriptRecorder,
                                          DeveloperTraceRecorder traceRecorder,
                                          ParentChildRunRegistry parentChildRunRegistry) {
        this(conversationRepository,
                runRepository,
                payloadRepository,
                componentPorts,
                actionDispatcher,
                userInteractionManager,
                loopPolicy,
                null,
                stateMachine,
                failureFactory,
                phaseGuard,
                eventPublisher,
                transcriptRecorder,
                traceRecorder,
                null,
                parentChildRunRegistry,
                null);
    }

    public DefaultAutoAgentRuntimeService(IConversationRepository conversationRepository,
                                          IRunRepository runRepository,
                                          IPayloadRepository payloadRepository,
                                          RuntimeComponentPorts componentPorts,
                                          MainActionDispatcher actionDispatcher,
                                          UserInteractionManager userInteractionManager,
                                          RuntimeLoopPolicy loopPolicy,
                                          RuntimeRoutePolicy routePolicy,
                                          RuntimeStateMachine stateMachine,
                                          RuntimeFailureFactory failureFactory,
                                          RuntimePhaseGuard phaseGuard,
                                          RunEventPublisher eventPublisher,
                                          RunTranscriptRecorder transcriptRecorder,
                                          DeveloperTraceRecorder traceRecorder,
                                          RunDiagnosticRecorder diagnosticRecorder,
                                          ParentChildRunRegistry parentChildRunRegistry) {
        this(conversationRepository,
                runRepository,
                payloadRepository,
                componentPorts,
                actionDispatcher,
                userInteractionManager,
                loopPolicy,
                routePolicy,
                stateMachine,
                failureFactory,
                phaseGuard,
                eventPublisher,
                transcriptRecorder,
                traceRecorder,
                diagnosticRecorder,
                parentChildRunRegistry,
                null);
    }

    public DefaultAutoAgentRuntimeService(IConversationRepository conversationRepository,
                                          IRunRepository runRepository,
                                          IPayloadRepository payloadRepository,
                                          RuntimeComponentPorts componentPorts,
                                          MainActionDispatcher actionDispatcher,
                                          UserInteractionManager userInteractionManager,
                                          RuntimeLoopPolicy loopPolicy,
                                          RuntimeRoutePolicy routePolicy,
                                          RuntimeStateMachine stateMachine,
                                          RuntimeFailureFactory failureFactory,
                                          RuntimePhaseGuard phaseGuard,
                                          RunEventPublisher eventPublisher,
                                          RunTranscriptRecorder transcriptRecorder,
                                          DeveloperTraceRecorder traceRecorder,
                                          RunDiagnosticRecorder diagnosticRecorder,
                                          ParentChildRunRegistry parentChildRunRegistry,
                                          GenericSubAgentDispatchOrchestrator genericSubAgentDispatchOrchestrator) {
        this.conversationRepository = conversationRepository;
        this.runRepository = runRepository;
        this.payloadRepository = payloadRepository;
        this.componentPorts = componentPorts;
        this.actionDispatcher = actionDispatcher;
        this.userInteractionManager = userInteractionManager;
        this.loopPolicy = loopPolicy == null ? new RuntimeLoopPolicy() : loopPolicy;
        this.routePolicy = routePolicy == null ? new RuntimeRoutePolicy() : routePolicy;
        this.stateMachine = stateMachine == null ? new RuntimeStateMachine() : stateMachine;
        this.failureFactory = failureFactory == null ? new RuntimeFailureFactory() : failureFactory;
        this.phaseGuard = phaseGuard;
        this.eventPublisher = eventPublisher;
        this.transcriptRecorder = transcriptRecorder;
        this.traceRecorder = traceRecorder;
        this.diagnosticRecorder = diagnosticRecorder;
        this.parentChildRunRegistry = parentChildRunRegistry == null ? new ParentChildRunRegistry() : parentChildRunRegistry;
        this.genericSubAgentDispatchOrchestrator = genericSubAgentDispatchOrchestrator;
    }

    @Override
    public RuntimeStepResult start(RuntimeStartCommand command) {
        if (command == null) {
            RuntimeSafeFailureVO failure = failureFactory.create(RuntimeFailureCodeEnumVO.MISSING_COMMAND,
                    RuntimePhaseEnumVO.CREATED, "RuntimeStartCommand is null.", false);
            return failure(null, null, failure);
        }
        String runId = firstNonBlank(command.getRunId(), "run-" + UUID.randomUUID());
        String sessionId = firstNonBlank(command.getSessionId(), "sess-" + UUID.randomUUID());
        String messageId = "msg-" + UUID.randomUUID();
        String userPayloadRef = savePayload(PayloadTypeEnumVO.TEXT, command.getUserInput(), preview(command.getUserInput()));
        diagnostic(runId, "RUN_START_REQUEST", diagnosticMap(
                "sessionId", sessionId,
                "userId", command.getUserId(),
                "agentId", command.getAgentId(),
                "messageId", messageId,
                "userPayloadRef", userPayloadRef,
                "userInput", command.getUserInput(),
                "inputType", command.getInputType(),
                "metadata", command.getRequestMetadata()
        ));

        conversationRepository.findSession(sessionId).orElseGet(() -> {
            conversationRepository.createSession(AgentSessionEntity.builder()
                    .sessionId(sessionId)
                    .userId(command.getUserId())
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
            return null;
        });
        runRepository.createRun(AgentRunEntity.builder()
                .runId(runId)
                .sessionId(sessionId)
                .userId(command.getUserId())
                .agentId(command.getAgentId())
                .status(RunStatusEnumVO.CREATED)
                .phase(RuntimePhaseEnumVO.CREATED)
                .ragWasUsed(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        conversationRepository.appendMessage(AgentMessageEntity.builder()
                .messageId(messageId)
                .sessionId(sessionId)
                .runId(runId)
                .role(MessageRoleEnumVO.USER)
                .contentRef(userPayloadRef)
                .visibleToUser(true)
                .createdAt(LocalDateTime.now())
                .build());
        transcriptRecorder.appendUserMessage(runId, sessionId, messageId, userPayloadRef);
        eventPublisher.received(runId, "User message received.");

        runRepository.updateRunStatus(runId, RunStatusEnumVO.RUNNING, null);

        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId(runId)
                .sessionId(sessionId)
                .userId(command.getUserId())
                .agentId(command.getAgentId())
                .userMessageId(messageId)
                .userInput(command.getUserInput())
                .runStatus(RunStatusEnumVO.RUNNING)
                .currentPhase(RuntimePhaseEnumVO.CREATED)
                .loopIndex(0)
                .maxLoop(loopPolicy.maxLoop())
                .recoveryCounters(RuntimeRecoveryCounters.initial())
                .runtimeFacts(new HashMap<>())
                .build();
        RuntimeSafeFailureVO transitionFailure = enterPhase(context, stateMachine.nextAfterStart());
        if (transitionFailure != null) {
            return failRun(context, transitionFailure);
        }
        return runLoop(context);
    }

    @Override
    public RuntimeStepResult resume(RuntimeResumeCommand command) {
        if (command == null) {
            RuntimeSafeFailureVO failure = failureFactory.create(RuntimeFailureCodeEnumVO.MISSING_COMMAND,
                    RuntimePhaseEnumVO.RESOLVING_USER_ANSWER, "RuntimeResumeCommand is null.", false);
            return failure(null, null, failure);
        }
        UserInputResolveCommand resolveCommand = UserInputResolveCommand.builder()
                .runId(command.getRunId())
                .pendingId(command.getPendingId())
                .selectedOptionId(command.getSelectedOptionId())
                .freeText(command.getFreeText())
                .cancelled(command.getCancelled())
                .requestMetadata(command.getRequestMetadata())
                .build();
        AgentRunEntity run = runRepository.findRun(resolveCommand.getRunId()).orElse(null);
        diagnostic(resolveCommand.getRunId(), "USER_INPUT_RESUME_REQUEST", diagnosticMap(
                "pendingId", resolveCommand.getPendingId(),
                "selectedOptionId", resolveCommand.getSelectedOptionId(),
                "freeText", resolveCommand.getFreeText(),
                "cancelled", resolveCommand.getCancelled(),
                "requestMetadata", resolveCommand.getRequestMetadata()
        ));
        if (run == null) {
            RuntimeSafeFailureVO failure = failureFactory.create(RuntimeFailureCodeEnumVO.MISSING_ACTIVE_RUN,
                    RuntimePhaseEnumVO.RESOLVING_USER_ANSWER,
                    "Run record is missing during USER_ASK resume: " + resolveCommand.getRunId() + ".", true);
            return failure(resolveCommand.getRunId(), null, failure);
        }
        if (run.getStatus() != RunStatusEnumVO.WAITING_USER) {
            return alreadyResolvedResumeResult(run);
        }
        AgentMessageEntity userMessage = findRunUserMessage(run.getSessionId(), run.getRunId());
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId(resolveCommand.getRunId())
                .sessionId(run.getSessionId())
                .userId(run.getUserId())
                .agentId(run.getAgentId())
                .userMessageId(userMessage == null ? null : userMessage.getMessageId())
                .userInput(loadPayloadContent(userMessage == null ? null : userMessage.getContentRef()))
                .currentPhase(RuntimePhaseEnumVO.WAITING_USER)
                .loopIndex(0)
                .runStatus(RunStatusEnumVO.WAITING_USER)
                .recoveryCounters(RuntimeRecoveryCounters.initial())
                .runtimeFacts(new HashMap<>())
                .build();
        RuntimeSafeFailureVO transitionFailure = enterPhase(context, RuntimePhaseEnumVO.RESOLVING_USER_ANSWER);
        if (transitionFailure != null) {
            return failRun(context, transitionFailure);
        }
        UserInputResolveResult resolveResult = userInteractionManager.resolveUserInput(resolveCommand, context);
        if (resolveResult.getResolutionStatus()
                == yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputResolutionStatusEnumVO.ALREADY_RESOLVED) {
            AgentRunEntity latestRun = runRepository.findRun(resolveCommand.getRunId()).orElse(run);
            return alreadyResolvedResumeResult(latestRun);
        }
        RuntimeStepResult continuation = resolveResult.getContinuationResult();
        if (continuation == null) {
            return failRun(context, failureFactory.missingPendingInput(resolveCommand.getRunId()));
        }
        RuntimeStepResult childResumeResult = resumeGenericSubAgentIfNeeded(context, resolveResult, continuation);
        if (childResumeResult != null) {
            return childResumeResult;
        }
        if (continuation.getStatus() == RuntimeStepStatusEnumVO.CONTINUE) {
            context.setRunStatus(RunStatusEnumVO.RUNNING);
            runRepository.updateRunStatus(context.getRunId(), RunStatusEnumVO.RUNNING, null);
            context.setCurrentPhase(RuntimePhaseEnumVO.RESOLVING_USER_ANSWER);
            RuntimeSafeFailureVO resumeTransition = enterPhase(context, continuation.getNextPhase());
            if (resumeTransition != null) {
                return failRun(context, resumeTransition);
            }
            if (continuation.getNextPhase() == RuntimePhaseEnumVO.PREPARING_CONTEXT
                    || continuation.getNextPhase() == RuntimePhaseEnumVO.BUILDING_STATE_VIEW
                    || continuation.getNextPhase() == RuntimePhaseEnumVO.PREPARING_TOOL
                    || continuation.getNextPhase() == RuntimePhaseEnumVO.CALLING_MAIN_NODE) {
                return runLoop(context);
            }
        }
        applyRunResult(context, continuation);
        return continuation;
    }

    private RuntimeStepResult resumeGenericSubAgentIfNeeded(RuntimeExecutionContext context,
                                                            UserInputResolveResult resolveResult,
                                                            RuntimeStepResult continuation) {
        if (genericSubAgentDispatchOrchestrator == null
                || continuation == null
                || continuation.getStatus() != RuntimeStepStatusEnumVO.WAITING_CHILDREN
                || context == null
                || context.getRuntimeFacts() == null) {
            return null;
        }
        String childRunId = stringRuntimeFact(context, "resumeChildRunId");
        if (childRunId == null || childRunId.isBlank()) {
            return null;
        }
        GenericSubAgentDispatchOrchestrationResultVO childResult = genericSubAgentDispatchOrchestrator.resumeChildAndProject(context,
                childRunId,
                resolveResult == null ? null : resolveResult.getUserAnswer());
        diagnostic(context.getRunId(), "CHILD_AGENT_RESUME_RESULT", diagnosticMap(
                "childRunId", childRunId,
                "childResults", childResult == null ? null : childResult.getChildResults(),
                "parentReady", childResult == null ? null : childResult.getParentReady()
        ));
        if (childResult == null || !childResult.isParentReady()) {
            RuntimeStepResult waiting = RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.WAITING_CHILDREN)
                    .nextRunStatus(RunStatusEnumVO.WAITING_CHILDREN)
                    .nextPhase(RuntimePhaseEnumVO.WAITING_CHILDREN)
                    .message("Parent run is still waiting for delegated child agents.")
                    .build();
            applyRunResult(context, waiting);
            return waiting;
        }
        context.setRunStatus(RunStatusEnumVO.RUNNING);
        runRepository.updateRunStatus(context.getRunId(), RunStatusEnumVO.RUNNING, null);
        RuntimeSafeFailureVO transitionFailure = enterPhase(context, RuntimePhaseEnumVO.BUILDING_STATE_VIEW);
        if (transitionFailure != null) {
            return failRun(context, transitionFailure);
        }
        return runLoop(context);
    }

    private RuntimeStepResult alreadyResolvedResumeResult(AgentRunEntity run) {
        RuntimeStepStatusEnumVO status = switch (run.getStatus()) {
            case COMPLETED -> RuntimeStepStatusEnumVO.COMPLETED;
            case CANCELLED -> RuntimeStepStatusEnumVO.CANCELLED;
            case FAILED -> RuntimeStepStatusEnumVO.FAILED;
            default -> RuntimeStepStatusEnumVO.CONTINUE;
        };
        return RuntimeStepResult.builder()
                .runId(run.getRunId())
                .sessionId(run.getSessionId())
                .status(status)
                .nextRunStatus(run.getStatus())
                .nextPhase(run.getPhase())
                .message("ALREADY_RESOLVED: Run is no longer waiting for this PendingInput.")
                .build();
    }

    @Override
    public RuntimeStepResult resumeChildren(RuntimeChildrenResumeCommand command) {
        String runId = command == null ? null : command.getRunId();
        if (command == null || runId == null || runId.isBlank()) {
            RuntimeSafeFailureVO failure = failureFactory.create(RuntimeFailureCodeEnumVO.MISSING_COMMAND,
                    RuntimePhaseEnumVO.WAITING_CHILDREN, "RuntimeChildrenResumeCommand or runId is missing.", false);
            return failure(runId, null, failure);
        }
        parentChildRunRegistry.restoreParent(runId);
        AgentRunEntity run = runRepository.findRun(runId).orElse(null);
        diagnostic(runId, "CHILDREN_RESUME_REQUEST", diagnosticMap(
                "runId", runId,
                "waitSatisfied", parentChildRunRegistry.isWaitSatisfied(runId)
        ));
        if (run == null) {
            RuntimeSafeFailureVO failure = failureFactory.create(RuntimeFailureCodeEnumVO.MISSING_ACTIVE_RUN,
                    RuntimePhaseEnumVO.WAITING_CHILDREN,
                    "Run record is missing during child-agent resume: " + runId + ".", true);
            return failure(runId, null, failure);
        }
        AgentMessageEntity userMessage = findRunUserMessage(run.getSessionId(), run.getRunId());
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId(runId)
                .sessionId(run.getSessionId())
                .userId(run.getUserId())
                .agentId(run.getAgentId())
                .userMessageId(userMessage == null ? null : userMessage.getMessageId())
                .userInput(loadPayloadContent(userMessage == null ? null : userMessage.getContentRef()))
                .currentPhase(RuntimePhaseEnumVO.WAITING_CHILDREN)
                .loopIndex(0)
                .maxLoop(loopPolicy.maxLoop())
                .runStatus(RunStatusEnumVO.WAITING_CHILDREN)
                .recoveryCounters(RuntimeRecoveryCounters.initial())
                .runtimeFacts(new HashMap<>())
                .build();
        if (!parentChildRunRegistry.isWaitSatisfied(runId)) {
            RuntimeStepResult waiting = RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.WAITING_CHILDREN)
                    .nextRunStatus(RunStatusEnumVO.WAITING_CHILDREN)
                    .nextPhase(RuntimePhaseEnumVO.WAITING_CHILDREN)
                    .message("Parent run is still waiting for delegated child agents.")
                    .build();
            applyRunResult(context, waiting);
            return waiting;
        }
        if (genericSubAgentDispatchOrchestrator != null) {
            genericSubAgentDispatchOrchestrator.runDispatchedChildrenAndProject(context);
        }
        context.setRunStatus(RunStatusEnumVO.RUNNING);
        runRepository.updateRunStatus(context.getRunId(), RunStatusEnumVO.RUNNING, null);
        RuntimeSafeFailureVO transitionFailure = enterPhase(context, RuntimePhaseEnumVO.BUILDING_STATE_VIEW);
        if (transitionFailure != null) {
            return failRun(context, transitionFailure);
        }
        return runLoop(context);
    }

    private AgentMessageEntity findRunUserMessage(String sessionId, String runId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return conversationRepository.listRecentVisibleMessages(sessionId, 20).stream()
                .filter(message -> runId == null || runId.equals(message.getRunId()))
                .filter(message -> message.getRole() == MessageRoleEnumVO.USER)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String loadPayloadContent(String payloadRef) {
        if (payloadRef == null || payloadRef.isBlank()) {
            return null;
        }
        return payloadRepository.findContent(payloadRef).orElse(null);
    }

    private String saveActionPayload(MainAgentActionVO action) {
        if (payloadRepository == null || action == null) {
            return null;
        }
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(action))
                .preview(action.getAction())
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    public RuntimeStepResult reportUnexpectedFailure(String runId, String sessionId, Throwable error) {
        String developerMessage = preview(error == null || error.getMessage() == null
                ? "Unexpected runtime error."
                : error.getMessage());
        diagnosticError(runId, "UNEXPECTED_FAILURE", error, diagnosticMap(
                "sessionId", sessionId,
                "developerMessage", developerMessage
        ));
        RuntimeSafeFailureVO failure = failureFactory.create(RuntimeFailureCodeEnumVO.UNEXPECTED_RUNTIME_ERROR,
                RuntimePhaseEnumVO.FAILED, developerMessage, true);
        if (runId != null && !runId.isBlank()) {
            try {
                traceRecorder.error(runId, null, failure.getFailureCode(), failure.getDeveloperMessage(), null);
            } catch (Exception ignored) {
                // Failure reporting must not hide the user-visible failed event.
            }
            try {
                transcriptRecorder.appendError(runId, null, failure.getFailureCode(), failure.getDeveloperMessage(), null);
            } catch (Exception ignored) {
                // Failure reporting must not hide the user-visible failed event.
            }
            try {
                runRepository.updateRunPhase(runId, RuntimePhaseEnumVO.FAILED);
                runRepository.updateRunStatus(runId, RunStatusEnumVO.FAILED, failure.getFailureCode().code());
            } catch (Exception ignored) {
                // Keep publishing the failed event even if status persistence is already broken.
            }
            try {
                eventPublisher.failed(runId, failure.getUserMessage());
            } catch (Exception ignored) {
                // Nothing else can be done in this top-level emergency path.
            }
        }
        return RuntimeStepResult.builder()
                .runId(runId)
                .sessionId(sessionId)
                .status(RuntimeStepStatusEnumVO.FAILED)
                .nextRunStatus(RunStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .safeFailure(failure)
                .finalAnswer(failure.getUserMessage())
                .message(failure.getDeveloperMessage())
                .build();
    }

    private RuntimeStepResult runLoop(RuntimeExecutionContext context) {
        while (context.getRunStatus() == RunStatusEnumVO.RUNNING) {
            diagnostic(context.getRunId(), "LOOP_STARTED", diagnosticMap(
                    "sessionId", context.getSessionId(),
                    "loopIndex", context.getLoopIndex(),
                    "phase", context.getCurrentPhase() == null ? null : context.getCurrentPhase().code()
            ));
            if (loopPolicy.maxLoopReached(context.countersOrInitial())) {
                return failRun(context, failureFactory.maxLoopReached(context.getCurrentPhase()));
            }

            if (context.getCurrentPhase() == RuntimePhaseEnumVO.PREPARING_TOOL) {
                RuntimeStepResult toolResult = resumeToolAction(context);
                if (toolResult.getStatus() == RuntimeStepStatusEnumVO.CONTINUE) {
                    context.countersOrInitial().incrementLoop();
                    context.setLoopIndex(context.getLoopIndex() == null ? 1 : context.getLoopIndex() + 1);
                    RuntimeSafeFailureVO loopTransition = enterPhase(context, nextLoopPhase(context, toolResult));
                    if (loopTransition != null) {
                        return failRun(context, loopTransition);
                    }
                    continue;
                }
                applyRunResult(context, toolResult);
                return toolResult;
            }

            RuntimeStepResult contextResult = prepareOrRefreshStateView(context);
            if (contextResult != null) {
                return contextResult;
            }

            MainAgentActionVO action = componentPorts.invokeMainAgent(context);

        RuntimeSafeFailureVO transitionFailure = enterPhase(context, RuntimePhaseEnumVO.VALIDATING_ACTION);
        if (transitionFailure != null) {
            return failRun(context, transitionFailure);
        }
        MainAgentActionTypeEnumVO actionType = action == null ? null : MainAgentActionTypeEnumVO.ofCode(action.getAction()).orElse(null);

        if (actionType == null) {
            AutoAgentHumanLog.stage("动作校验", context.getRunId(), "检查失败：MainAgent 输出动作为空或未知，原始 action="
                    + (action == null ? null : action.getAction()));
            return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.MAIN_ACTION_CONTRACT_FAILED,
                    RuntimePhaseEnumVO.VALIDATING_ACTION, "MainAgentAction action type is missing or unknown.", true));
        }

        AutoAgentHumanLog.stage("动作校验", context.getRunId(), "检查通过：action=" + actionType.code());
            MainAgentActionVO previousAction = context.getLastAction();
            context.setLastAction(action);
            String actionPayloadRef = saveActionPayload(action);
            traceRecorder.actionParsed(context.getRunId(), context.getLoopIndex(), actionType, actionPayloadRef);
            transcriptRecorder.appendAssistantAction(context.getRunId(), context.getLoopIndex(), action, actionPayloadRef);

        transitionFailure = enterPhase(context, RuntimePhaseEnumVO.HANDLING_ACTION);
        if (transitionFailure != null) {
            return failRun(context, transitionFailure);
        }
        AutoAgentHumanLog.stage("动作路由", context.getRunId(), "准备处理 action=" + actionType.code());
        RuntimeSafeFailureVO deniedActionFailure = deniedToolExecutionActionFailure(context, actionType, action);
        if (deniedActionFailure != null) {
            return failRun(context, deniedActionFailure);
        }
        MainActionHandlerResult skippedCompletedToolAction = skippedCompletedToolActionResult(context, actionType, action);
        if (skippedCompletedToolAction != null) {
            workingStateManager.apply(context, action, skippedCompletedToolAction);
            RuntimeStepResult stepResult = routeActionResult(context, action, skippedCompletedToolAction);
            if (stepResult.getStatus() == RuntimeStepStatusEnumVO.CONTINUE) {
                context.countersOrInitial().incrementLoop();
                context.setLoopIndex(context.getLoopIndex() == null ? 1 : context.getLoopIndex() + 1);
                RuntimeSafeFailureVO loopTransition = enterPhase(context, nextLoopPhase(context, stepResult));
                if (loopTransition != null) {
                    return failRun(context, loopTransition);
                }
                continue;
            }
            applyRunResult(context, stepResult);
            return stepResult;
        }
        MainActionHandlerResult actionResult = actionDispatcher.dispatch(context, action);
            workingStateManager.apply(context, action, actionResult);
            RuntimeStepResult stepResult = routeActionResult(context, action, actionResult);
            if (stepResult.getStatus() == RuntimeStepStatusEnumVO.CONTINUE) {
                RuntimeSafeFailureVO repeatedActionFailure = repeatedExecutionActionFailure(context, previousAction, actionType, action);
                if (repeatedActionFailure != null) {
                    return failRun(context, repeatedActionFailure);
                }
                context.countersOrInitial().incrementLoop();
                context.setLoopIndex(context.getLoopIndex() == null ? 1 : context.getLoopIndex() + 1);
                RuntimeSafeFailureVO loopTransition = enterPhase(context, nextLoopPhase(context, stepResult));
                if (loopTransition != null) {
                    return failRun(context, loopTransition);
                }
                continue;
            }
            applyRunResult(context, stepResult);
            return stepResult;
        }
        return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.MISSING_ACTIVE_RUN,
                context.getCurrentPhase(), "Run loop exited without terminal status.", false));
    }

    private RuntimeStepResult resumeToolAction(RuntimeExecutionContext context) {
        Map<String, Object> toolIntent = resumeToolIntent(context);
        if (toolIntent == null || toolIntent.isEmpty()) {
            RuntimeSafeFailureVO failure = failureFactory.create(RuntimeFailureCodeEnumVO.ACTION_HANDLER_UNAVAILABLE,
                    RuntimePhaseEnumVO.PREPARING_TOOL, "Tool approval checkpoint is missing toolIntent.", true);
            return failure(context.getRunId(), context.getSessionId(), failure);
        }
        MainAgentActionVO action = MainAgentActionVO.builder()
                .action(MainAgentActionTypeEnumVO.CALL_TOOL.code())
                .stateDelta(Map.of("toolIntent", toolIntent))
                .build();
        context.setLastAction(action);
        String actionPayloadRef = saveActionPayload(action);
        traceRecorder.actionParsed(context.getRunId(), context.getLoopIndex(), MainAgentActionTypeEnumVO.CALL_TOOL, actionPayloadRef);
        transcriptRecorder.appendAssistantAction(context.getRunId(), context.getLoopIndex(), action, actionPayloadRef);

        RuntimeSafeFailureVO transitionFailure = enterPhase(context, RuntimePhaseEnumVO.HANDLING_ACTION);
        if (transitionFailure != null) {
            return failure(context.getRunId(), context.getSessionId(), transitionFailure);
        }
        MainActionHandlerResult actionResult = actionDispatcher.dispatch(context, action);
        workingStateManager.apply(context, action, actionResult);
        return routeActionResult(context, action, actionResult);
    }

    private RuntimeSafeFailureVO repeatedExecutionActionFailure(RuntimeExecutionContext context,
                                                               MainAgentActionVO previousAction,
                                                               MainAgentActionTypeEnumVO actionType,
                                                               MainAgentActionVO currentAction) {
        if (actionType != MainAgentActionTypeEnumVO.CALL_TOOL || !sameAction(previousAction, currentAction)) {
            return null;
        }
        if (loopPolicy.canRetryTool(context.countersOrInitial())) {
            context.countersOrInitial().incrementToolRetry();
            return null;
        }
        return failureFactory.create(RuntimeFailureCodeEnumVO.TOOL_RETRY_EXHAUSTED,
                context.getCurrentPhase(),
                "MainAgent repeated the same CALL_TOOL action after tool retry limit was reached.",
                false);
    }

    private RuntimeSafeFailureVO deniedToolExecutionActionFailure(RuntimeExecutionContext context,
                                                                 MainAgentActionTypeEnumVO actionType,
                                                                 MainAgentActionVO currentAction) {
        if (actionType != MainAgentActionTypeEnumVO.CALL_TOOL || currentAction == null) {
            return null;
        }
        Map<String, Object> currentIntent = toolIntentFromAction(currentAction);
        if (currentIntent == null) {
            return null;
        }
        List<UserClarificationVO> clarifications = context == null || context.getLastStateView() == null
                ? List.of()
                : context.getLastStateView().getUserClarifications();
        if (clarifications == null || clarifications.isEmpty()) {
            return null;
        }
        boolean denied = clarifications.stream()
                .filter(clarification -> clarification != null && "TOOL_APPROVAL_REJECTED".equals(clarification.getAnswerType()))
                .map(UserClarificationVO::getMetadata)
                .filter(Objects::nonNull)
                .map(metadata -> metadata.get("toolIntent"))
                .filter(Map.class::isInstance)
                .map(value -> (Map<String, Object>) value)
                .anyMatch(deniedIntent -> sameToolExecutionIntent(deniedIntent, currentIntent));
        if (!denied) {
            return null;
        }
        return failureFactory.create(RuntimeFailureCodeEnumVO.TOOL_ACTION_DENIED_BY_USER,
                context.getCurrentPhase(),
                "MainAgent attempted a tool action that the user already rejected.",
                false);
    }

    private MainActionHandlerResult skippedCompletedToolActionResult(RuntimeExecutionContext context,
                                                                     MainAgentActionTypeEnumVO actionType,
                                                                     MainAgentActionVO currentAction) {
        if (actionType != MainAgentActionTypeEnumVO.CALL_TOOL || currentAction == null) {
            return null;
        }
        Map<String, Object> currentIntent = toolIntentFromAction(currentAction);
        if (currentIntent == null) {
            return null;
        }
        ActionEffectVO succeeded = successfulToolActionEffect(context, currentIntent);
        if (succeeded == null) {
            return null;
        }
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                .createdEvidenceIds(defaultList(succeeded.getCreatedEvidenceIds()))
                .createdEvidence(defaultList(succeeded.getCreatedEvidence()))
                .actionEffect(ActionEffectVO.builder()
                        .action(MainAgentActionTypeEnumVO.CALL_TOOL.code())
                        .status("SKIPPED_ALREADY_SUCCEEDED")
                        .message("Skipped duplicate CALL_TOOL because the same tool action already succeeded. MainAgent must decide the next semantic action from actionHistory and evidencePack.")
                        .loopIndex(context.getLoopIndex())
                        .toolIntent(currentIntent)
                        .createdEvidenceIds(defaultList(succeeded.getCreatedEvidenceIds()))
                        .createdEvidence(defaultList(succeeded.getCreatedEvidence()))
                        .createdArtifactIds(defaultList(succeeded.getCreatedArtifactIds()))
                        .build())
                .message("Skipped duplicate CALL_TOOL because the same tool action already succeeded.")
                .build();
    }

    private ActionEffectVO successfulToolActionEffect(RuntimeExecutionContext context, Map<String, Object> currentIntent) {
        if (context == null || context.getWorkingState() == null || context.getWorkingState().getActionHistory() == null) {
            return null;
        }
        List<ActionEffectVO> history = context.getWorkingState().getActionHistory();
        if (history.isEmpty()) {
            return null;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            ActionEffectVO effect = history.get(index);
            if (toolActionSucceeded(effect) && sameToolExecutionIntent(effect.getToolIntent(), currentIntent)) {
                return effect;
            }
        }
        return null;
    }

    private boolean toolActionSucceeded(ActionEffectVO effect) {
        if (effect == null || !MainAgentActionTypeEnumVO.CALL_TOOL.code().equals(effect.getAction())) {
            return false;
        }
        return ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name().equals(effect.getStatus());
    }

    private boolean sameAction(MainAgentActionVO left, MainAgentActionVO right) {
        if (left == null || right == null) {
            return false;
        }
        if (!Objects.equals(left.getAction(), right.getAction())) {
            return false;
        }
        if (MainAgentActionTypeEnumVO.CALL_TOOL.code().equals(left.getAction())) {
            return sameToolExecutionIntent(left, right);
        }
        return Objects.equals(left.getStateDelta(), right.getStateDelta());
    }

    private boolean sameToolExecutionIntent(MainAgentActionVO left, MainAgentActionVO right) {
        Map<String, Object> leftIntent = toolIntentFromAction(left);
        Map<String, Object> rightIntent = toolIntentFromAction(right);
        if (leftIntent == null || rightIntent == null) {
            return false;
        }
        return sameToolExecutionIntent(leftIntent, rightIntent);
    }

    private boolean sameToolExecutionIntent(Map<String, Object> leftIntent, Map<String, Object> rightIntent) {
        if (leftIntent == null || rightIntent == null) {
            return false;
        }
        return Objects.equals(leftIntent.get("capabilityCode"), rightIntent.get("capabilityCode"))
                && Objects.equals(leftIntent.get("toolName"), rightIntent.get("toolName"))
                && Objects.equals(leftIntent.get("arguments"), rightIntent.get("arguments"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolIntentFromAction(MainAgentActionVO action) {
        if (action == null || action.getStateDelta() == null) {
            return null;
        }
        Object value = action.getStateDelta().get("toolIntent");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private <T> List<T> defaultList(List<T> value) {
        return value == null ? List.of() : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resumeToolIntent(RuntimeExecutionContext context) {
        if (context == null || context.getRuntimeFacts() == null) {
            return null;
        }
        Object value = context.getRuntimeFacts().get("resumeToolIntent");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private RuntimeStepResult prepareOrRefreshStateView(RuntimeExecutionContext context) {
        RuntimePhaseEnumVO phase = context.getCurrentPhase();
        if (phase == RuntimePhaseEnumVO.BUILDING_STATE_VIEW) {
            if (context.getWorkingState() != null) {
                return projectWorkingStateView(context);
            }
            return refreshStateView(context);
        }
        if (phase == RuntimePhaseEnumVO.PREPARING_CONTEXT || context.getLastStateView() == null) {
            return prepareInitialStateView(context);
        }
        if (phase != RuntimePhaseEnumVO.CALLING_MAIN_NODE) {
            RuntimeSafeFailureVO transitionFailure = enterPhase(context, RuntimePhaseEnumVO.CALLING_MAIN_NODE);
            if (transitionFailure != null) {
                return failRun(context, transitionFailure);
            }
        }
        return null;
    }

    private RuntimeStepResult prepareInitialStateView(RuntimeExecutionContext context) {
        RuntimeSafeFailureVO transitionFailure = enterPhase(context, RuntimePhaseEnumVO.PLANNING_CONTEXT);
        if (transitionFailure != null) {
            return failRun(context, transitionFailure);
        }
        ContextPlannerHandlingResult prepared = componentPorts.prepareContext(context);
        if (prepared == null) {
            return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.CONTEXT_PREPARATION_FAILED,
                    context.getCurrentPhase(), "Context preparation returned null.", true));
        }
        if (prepared.getAskUserRequest() != null) {
            if (alreadyAnswered(context, prepared.getAskUserRequest())) {
                context.countersOrInitial().incrementLoop();
                context.setLoopIndex(context.getLoopIndex() == null ? 1 : context.getLoopIndex() + 1);
                RuntimeSafeFailureVO loopTransition = enterPhase(context, RuntimePhaseEnumVO.BUILDING_STATE_VIEW);
                if (loopTransition != null) {
                    return failRun(context, loopTransition);
                }
                return refreshStateView(context);
            }
            return pauseForUser(context, prepared.getAskUserRequest(), ContextPlannerPendingInputHandler.HANDLER_CODE,
                    PendingInputTypeEnumVO.CONTEXT_CLARIFICATION.code(), "ContextPlanner needs user clarification.");
        }
        if (prepared.getFailure() != null) {
            return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.CONTEXT_PREPARATION_FAILED,
                    context.getCurrentPhase(), prepared.getFailure().getMessage(), true));
        }
        return acceptStateViewAndCallMain(context, prepared);
    }

    private RuntimeStepResult refreshStateView(RuntimeExecutionContext context) {
        ContextPlannerHandlingResult refreshed = componentPorts.refreshContext(context);
        if (refreshed == null) {
            return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.CONTEXT_PREPARATION_FAILED,
                    context.getCurrentPhase(), "Context refresh returned null.", true));
        }
        if (refreshed.getFailure() != null) {
            return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.CONTEXT_PREPARATION_FAILED,
                    context.getCurrentPhase(), refreshed.getFailure().getMessage(), true));
        }
        return acceptStateViewAndCallMain(context, refreshed);
    }

    private RuntimeStepResult acceptStateViewAndCallMain(RuntimeExecutionContext context, ContextPlannerHandlingResult result) {
        return acceptStateViewAndCallMain(context, result, true);
    }

    private RuntimeStepResult acceptStateViewAndCallMain(RuntimeExecutionContext context,
                                                         ContextPlannerHandlingResult result,
                                                         boolean updateWorkingBaseStateView) {
        if (result == null || result.getStateView() == null) {
            return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.CONTEXT_PREPARATION_FAILED,
                    context.getCurrentPhase(), "State view is missing.", true));
        }
        if (context.getCurrentPhase() != RuntimePhaseEnumVO.BUILDING_STATE_VIEW
                && context.getCurrentPhase() != RuntimePhaseEnumVO.CALLING_MAIN_NODE) {
            RuntimeSafeFailureVO buildFailure = enterPhase(context, RuntimePhaseEnumVO.BUILDING_STATE_VIEW);
            if (buildFailure != null) {
                return failRun(context, buildFailure);
            }
        }
        context.setLastStateView(result.getStateView());
        if (context.getWorkingState() == null) {
            context.setWorkingState(workingStateManager.initialize(result.getStateView()));
        } else if (updateWorkingBaseStateView && result.getStateView() != null) {
            context.getWorkingState().setBaseStateView(result.getStateView());
        }
        context.setLastContextSelections(result.getEffectiveSelections());
        transcriptRecorder.appendStateViewSummary(context.getRunId(), context.getLoopIndex(), result.getStateView(), null);
        RuntimeSafeFailureVO transitionFailure = context.getCurrentPhase() == RuntimePhaseEnumVO.CALLING_MAIN_NODE
                ? null
                : enterPhase(context, RuntimePhaseEnumVO.CALLING_MAIN_NODE);
        if (transitionFailure != null) {
            return failRun(context, transitionFailure);
        }
        return null;
    }

    private RuntimeStepResult projectWorkingStateView(RuntimeExecutionContext context) {
        MainAgentStateViewVO stateView = workingStateManager.project(context.getWorkingState());
        if (stateView == null) {
            return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.CONTEXT_PREPARATION_FAILED,
                    context.getCurrentPhase(), "Working state projection returned null.", true));
        }
        return acceptStateViewAndCallMain(context, ContextPlannerHandlingResult.builder()
                .stateView(stateView)
                .effectiveSelections(context.getLastContextSelections())
                .build(), false);
    }

    private RuntimePhaseEnumVO nextLoopPhase(RuntimeExecutionContext context, RuntimeStepResult stepResult) {
        return routePolicy.nextLoopPhase(context, stepResult);
    }

    private RuntimeStepResult routeActionResult(RuntimeExecutionContext context, MainAgentActionVO action, MainActionHandlerResult actionResult) {
        if (actionResult == null || actionResult.getStatus() == null) {
            return failRun(context, failureFactory.actionHandlerUnavailable(action == null ? null : action.getAction()));
        }
        if (actionResult.getStatus() == MainActionHandlerStatusEnumVO.WAITING_USER) {
            if (actionResult.getPendingInputId() != null && !actionResult.getPendingInputId().isBlank()) {
                return RuntimeStepResult.builder()
                        .runId(context.getRunId())
                        .sessionId(context.getSessionId())
                        .status(RuntimeStepStatusEnumVO.WAITING_USER)
                        .nextRunStatus(RunStatusEnumVO.WAITING_USER)
                        .nextPhase(RuntimePhaseEnumVO.WAITING_USER)
                        .askUserRequest(actionResult.getAskUserRequest())
                        .pendingInputId(actionResult.getPendingInputId())
                        .action(action)
                        .actionResult(actionResult)
                        .message(actionResult.getMessage())
                        .build();
            }
            if (alreadyAnswered(context, actionResult.getAskUserRequest())) {
                return RuntimeStepResult.builder()
                        .runId(context.getRunId())
                        .sessionId(context.getSessionId())
                        .status(RuntimeStepStatusEnumVO.CONTINUE)
                        .nextRunStatus(RunStatusEnumVO.RUNNING)
                        .nextPhase(RuntimePhaseEnumVO.PREPARING_CONTEXT)
                        .action(action)
                        .actionResult(actionResult)
                        .message("User already answered this clarification. Continue with userClarifications.")
                        .build();
            }
            return pauseForUser(context, actionResult.getAskUserRequest(), MainAgentPendingInputHandler.HANDLER_CODE,
                    PendingInputTypeEnumVO.MAIN_AGENT_QUESTION.code(), actionResult.getMessage());
        }
        if (actionResult.getStatus() == MainActionHandlerStatusEnumVO.WAITING_CHILDREN) {
            return RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.WAITING_CHILDREN)
                    .nextRunStatus(RunStatusEnumVO.WAITING_CHILDREN)
                    .nextPhase(RuntimePhaseEnumVO.WAITING_CHILDREN)
                    .action(action)
                    .actionResult(actionResult)
                    .message(actionResult.getMessage())
                    .build();
        }
        if (actionResult.getStatus() == MainActionHandlerStatusEnumVO.COMPLETED) {
            RuntimeSafeFailureVO verifyingFailure = enterPhase(context, RuntimePhaseEnumVO.VERIFYING_FINAL);
            if (verifyingFailure != null) {
                return failure(context.getRunId(), context.getSessionId(), verifyingFailure);
            }
            AutoAgentHumanLog.stage("最终检查", context.getRunId(), "最终回答检查通过，准备进入完成阶段。");
            RuntimeSafeFailureVO completedFailure = enterPhase(context, RuntimePhaseEnumVO.COMPLETED);
            if (completedFailure != null) {
                return failure(context.getRunId(), context.getSessionId(), completedFailure);
            }
            return RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.COMPLETED)
                    .nextRunStatus(RunStatusEnumVO.COMPLETED)
                    .nextPhase(RuntimePhaseEnumVO.COMPLETED)
                    .action(action)
                    .actionResult(actionResult)
                    .finalAnswer(actionResult.getFinalAnswerCandidate() == null ? null : actionResult.getFinalAnswerCandidate().getContent())
                    .finalMessageId(actionResult.getFinalMessageId())
                    .message(actionResult.getMessage())
                    .build();
        }
        if (actionResult.getStatus() == MainActionHandlerStatusEnumVO.CONTINUE_LOOP) {
            return RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.CONTINUE)
                    .nextRunStatus(RunStatusEnumVO.RUNNING)
                    .nextPhase(actionResult.getNextPhase() == null ? RuntimePhaseEnumVO.PREPARING_CONTEXT : actionResult.getNextPhase())
                    .action(action)
                    .actionResult(actionResult)
                    .message(actionResult.getMessage())
                    .build();
        }
        if (actionResult.getStatus() == MainActionHandlerStatusEnumVO.CANCELLED) {
            return RuntimeStepResult.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.CANCELLED)
                    .nextRunStatus(RunStatusEnumVO.CANCELLED)
                    .nextPhase(RuntimePhaseEnumVO.CANCELLED)
                    .action(action)
                    .actionResult(actionResult)
                    .message(actionResult.getMessage())
                    .build();
        }
        return RuntimeStepResult.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .status(RuntimeStepStatusEnumVO.FAILED)
                .nextRunStatus(RunStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .action(action)
                .actionResult(actionResult)
                .safeFailure(actionResult.getSafeFailure())
                .message(actionResult.getMessage())
                .build();
    }

    private RuntimeStepResult pauseForUser(RuntimeExecutionContext context,
                                           AskUserRequestVO request,
                                           String handlerCode,
                                           String pendingType,
                                           String message) {
        diagnostic(context.getRunId(), "PAUSE_FOR_USER_REQUEST", diagnosticMap(
                "sessionId", context.getSessionId(),
                "loopIndex", context.getLoopIndex(),
                "handlerCode", handlerCode,
                "pendingType", pendingType,
                "message", message,
                "askUserRequest", request
        ));
        PendingInputCreateResult pending = userInteractionManager.createPendingInput(PendingInputCreateCommand.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .sourceComponent(handlerCode)
                .pendingType(pendingType)
                .askUserRequest(request)
                .runtimeContext(context)
                .continuation(ContinuationCheckpointVO.builder()
                        .handler(handlerCode)
                        .resumePhase(resumePhaseFor(handlerCode))
                        .sourceComponent(handlerCode)
                        .relatedRunId(context.getRunId())
                        .relatedLoopIndex(context.getLoopIndex())
                        .expectedAnswerValueType(request == null ? null : request.getInputMode())
                        .payload(Map.of())
                        .build())
                .build());
        if (!Boolean.TRUE.equals(pending.getCreated())) {
            return failRun(context, failureFactory.invalidPendingAnswer(pending.getFailureMessage()));
        }
        RuntimeSafeFailureVO transitionFailure = enterPhase(context, RuntimePhaseEnumVO.WAITING_USER);
        if (transitionFailure != null) {
            return failRun(context, transitionFailure);
        }
        context.setRunStatus(RunStatusEnumVO.WAITING_USER);
        return RuntimeStepResult.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .status(RuntimeStepStatusEnumVO.WAITING_USER)
                .nextRunStatus(RunStatusEnumVO.WAITING_USER)
                .nextPhase(RuntimePhaseEnumVO.WAITING_USER)
                .askUserRequest(request)
                .pendingInputId(pending.getPendingInputId())
                .message(message)
                .build();
    }

    private RuntimePhaseEnumVO resumePhaseFor(String handlerCode) {
        if (MainAgentPendingInputHandler.HANDLER_CODE.equals(handlerCode)) {
            return RuntimePhaseEnumVO.BUILDING_STATE_VIEW;
        }
        return RuntimePhaseEnumVO.PREPARING_CONTEXT;
    }

    private RuntimeSafeFailureVO enterPhase(RuntimeExecutionContext context, RuntimePhaseEnumVO nextPhase) {
        RuntimeSafeFailureVO failure = phaseGuard.enter(context, nextPhase);
        if (failure == null) {
            runRepository.updateRunPhase(context.getRunId(), nextPhase);
            eventPublisher.phase(context.getRunId(), nextPhase.code(), nextPhase.info());
        }
        return failure;
    }

    private boolean alreadyAnswered(RuntimeExecutionContext context, AskUserRequestVO request) {
        if (context == null || context.getRuntimeFacts() == null || request == null || isBlank(request.getQuestion())) {
            return false;
        }
        Object value = context.getRuntimeFacts().get("userClarifications");
        if (!(value instanceof Iterable<?> iterable)) {
            return false;
        }
        String question = normalize(request.getQuestion());
        for (Object item : iterable) {
            if (item instanceof UserClarificationVO clarification
                    && question.equals(normalize(clarification.getQuestion()))
                    && (clarification.getValue() != null || !isBlank(clarification.getFreeText()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stringRuntimeFact(RuntimeExecutionContext context, String key) {
        Object value = context == null || context.getRuntimeFacts() == null ? null : context.getRuntimeFacts().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private void applyRunResult(RuntimeExecutionContext context, RuntimeStepResult result) {
        if (result == null || result.getNextRunStatus() == null) {
            return;
        }
        diagnostic(context.getRunId(), "RUN_RESULT_APPLIED", diagnosticMap(
                "sessionId", context.getSessionId(),
                "status", result.getStatus() == null ? null : result.getStatus().code(),
                "nextRunStatus", result.getNextRunStatus() == null ? null : result.getNextRunStatus().code(),
                "nextPhase", result.getNextPhase() == null ? null : result.getNextPhase().code(),
                "message", result.getMessage(),
                "finalMessageId", result.getFinalMessageId(),
                "pendingInputId", result.getPendingInputId(),
                "failureCode", result.getSafeFailure() == null || result.getSafeFailure().getFailureCode() == null
                        ? null : result.getSafeFailure().getFailureCode().code()
        ));
        context.setRunStatus(result.getNextRunStatus());
        if (result.getNextPhase() != null) {
            context.setCurrentPhase(result.getNextPhase());
            runRepository.updateRunPhase(context.getRunId(), result.getNextPhase());
        }
        runRepository.updateRunStatus(context.getRunId(), result.getNextRunStatus(),
                result.getSafeFailure() == null || result.getSafeFailure().getFailureCode() == null
                        ? null : result.getSafeFailure().getFailureCode().code());
        if (result.getStatus() == RuntimeStepStatusEnumVO.COMPLETED) {
            eventPublisher.completed(context.getRunId(), result.getFinalMessageId());
        } else if (result.getStatus() == RuntimeStepStatusEnumVO.FAILED) {
            eventPublisher.failed(context.getRunId(), result.getSafeFailure() == null ? result.getMessage() : result.getSafeFailure().getUserMessage());
        } else if (result.getStatus() == RuntimeStepStatusEnumVO.CANCELLED) {
            eventPublisher.cancelled(context.getRunId(), result.getMessage());
        }
    }

    private RuntimeStepResult failRun(RuntimeExecutionContext context, RuntimeSafeFailureVO failure) {
        AutoAgentHumanLog.failure(context == null ? null : context.getRunId(), "运行失败", failure);
        diagnostic(context == null ? null : context.getRunId(), "RUNTIME_FAILURE", diagnosticMap(
                "sessionId", context == null ? null : context.getSessionId(),
                "loopIndex", context == null ? null : context.getLoopIndex(),
                "phase", context == null || context.getCurrentPhase() == null ? null : context.getCurrentPhase().code(),
                "failureCode", failure == null || failure.getFailureCode() == null ? null : failure.getFailureCode().code(),
                "developerMessage", failure == null ? null : failure.getDeveloperMessage(),
                "userMessage", failure == null ? null : failure.getUserMessage()
        ));
        if (context != null && context.getRunId() != null) {
            traceRecorder.error(context.getRunId(), context.getLoopIndex(), failure.getFailureCode(), failure.getDeveloperMessage(), null);
            transcriptRecorder.appendError(context.getRunId(), context.getLoopIndex(), failure.getFailureCode(), failure.getDeveloperMessage(), null);
        }
        RuntimeStepResult result = RuntimeStepResult.builder()
                .runId(context == null ? null : context.getRunId())
                .sessionId(context == null ? null : context.getSessionId())
                .status(RuntimeStepStatusEnumVO.FAILED)
                .nextRunStatus(RunStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .safeFailure(failure)
                .finalAnswer(failure.getUserMessage())
                .message(failure.getDeveloperMessage())
                .build();
        if (context != null && context.getRunId() != null) {
            applyRunResult(context, result);
        }
        return result;
    }

    private RuntimeStepResult failure(String runId, String sessionId, RuntimeSafeFailureVO failure) {
        return RuntimeStepResult.builder()
                .runId(runId)
                .sessionId(sessionId)
                .status(RuntimeStepStatusEnumVO.FAILED)
                .nextRunStatus(RunStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .safeFailure(failure)
                .finalAnswer(failure.getUserMessage())
                .message(failure.getDeveloperMessage())
                .build();
    }

    private String savePayload(PayloadTypeEnumVO payloadType, String content, String preview) {
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(payloadType)
                .content(content)
                .preview(preview)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    private void diagnostic(String runId, String event, Map<String, Object> details) {
        if (diagnosticRecorder == null || runId == null || runId.isBlank()) {
            return;
        }
        diagnosticRecorder.record(runId, "RUNTIME", event, details);
    }

    private void diagnosticError(String runId, String event, Throwable error, Map<String, Object> details) {
        if (diagnosticRecorder == null || runId == null || runId.isBlank()) {
            return;
        }
        diagnosticRecorder.error(runId, "RUNTIME", event, error, details);
    }

    private Map<String, Object> diagnosticMap(Object... keyValues) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (keyValues == null) {
            return value;
        }
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            value.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return value;
    }
}

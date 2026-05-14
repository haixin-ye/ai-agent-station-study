package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
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
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeRecoveryCounters;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeResumeCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.interaction.ContextPlannerPendingInputHandler;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DefaultAutoAgentRuntimeService implements AutoAgentRuntimeService {

    private final IConversationRepository conversationRepository;
    private final IRunRepository runRepository;
    private final IPayloadRepository payloadRepository;
    private final RuntimeComponentPorts componentPorts;
    private final MainActionDispatcher actionDispatcher;
    private final UserInteractionManager userInteractionManager;
    private final RuntimeLoopPolicy loopPolicy;
    private final RuntimeStateMachine stateMachine;
    private final RuntimeFailureFactory failureFactory;
    private final RuntimePhaseGuard phaseGuard;
    private final RunEventPublisher eventPublisher;
    private final RunTranscriptRecorder transcriptRecorder;
    private final DeveloperTraceRecorder traceRecorder;

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
        this.conversationRepository = conversationRepository;
        this.runRepository = runRepository;
        this.payloadRepository = payloadRepository;
        this.componentPorts = componentPorts;
        this.actionDispatcher = actionDispatcher;
        this.userInteractionManager = userInteractionManager;
        this.loopPolicy = loopPolicy == null ? new RuntimeLoopPolicy() : loopPolicy;
        this.stateMachine = stateMachine == null ? new RuntimeStateMachine() : stateMachine;
        this.failureFactory = failureFactory == null ? new RuntimeFailureFactory() : failureFactory;
        this.phaseGuard = phaseGuard;
        this.eventPublisher = eventPublisher;
        this.transcriptRecorder = transcriptRecorder;
        this.traceRecorder = traceRecorder;
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
        UserInputResolveCommand resolveCommand = UserInputResolveCommand.builder()
                .runId(command == null ? null : command.getRunId())
                .pendingId(command == null ? null : command.getPendingId())
                .selectedOptionId(command == null ? null : command.getSelectedOptionId())
                .freeText(command == null ? null : command.getFreeText())
                .cancelled(command == null ? null : command.getCancelled())
                .requestMetadata(command == null ? null : command.getRequestMetadata())
                .build();
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId(resolveCommand.getRunId())
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
        RuntimeStepResult continuation = resolveResult.getContinuationResult();
        if (continuation == null) {
            return failRun(context, failureFactory.missingPendingInput(resolveCommand.getRunId()));
        }
        if (continuation.getStatus() == RuntimeStepStatusEnumVO.CONTINUE) {
            context.setRunStatus(RunStatusEnumVO.RUNNING);
            context.setCurrentPhase(RuntimePhaseEnumVO.RESOLVING_USER_ANSWER);
            RuntimeSafeFailureVO resumeTransition = enterPhase(context, continuation.getNextPhase());
            if (resumeTransition != null) {
                return failRun(context, resumeTransition);
            }
            if (continuation.getNextPhase() == RuntimePhaseEnumVO.PREPARING_CONTEXT) {
                return runLoop(context);
            }
        }
        applyRunResult(context, continuation);
        return continuation;
    }

    private RuntimeStepResult runLoop(RuntimeExecutionContext context) {
        while (context.getRunStatus() == RunStatusEnumVO.RUNNING) {
            if (loopPolicy.maxLoopReached(context.countersOrInitial())) {
                return failRun(context, failureFactory.maxLoopReached(context.getCurrentPhase()));
            }

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
                return pauseForUser(context, prepared.getAskUserRequest(), ContextPlannerPendingInputHandler.HANDLER_CODE,
                        PendingInputTypeEnumVO.CONTEXT_CLARIFICATION.code(), "ContextPlanner needs user clarification.");
            }
            if (prepared.getFailure() != null) {
                return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.CONTEXT_PREPARATION_FAILED,
                        context.getCurrentPhase(), prepared.getFailure().getMessage(), true));
            }
            transitionFailure = enterPhase(context, RuntimePhaseEnumVO.BUILDING_STATE_VIEW);
            if (transitionFailure != null) {
                return failRun(context, transitionFailure);
            }
            context.setLastStateView(prepared.getStateView());
            transcriptRecorder.appendStateViewSummary(context.getRunId(), context.getLoopIndex(), prepared.getStateView(), null);

            transitionFailure = enterPhase(context, RuntimePhaseEnumVO.CALLING_MAIN_NODE);
            if (transitionFailure != null) {
                return failRun(context, transitionFailure);
            }
            MainAgentActionVO action = componentPorts.invokeMainAgent(context);

            transitionFailure = enterPhase(context, RuntimePhaseEnumVO.VALIDATING_ACTION);
            if (transitionFailure != null) {
                return failRun(context, transitionFailure);
            }
            MainAgentActionTypeEnumVO actionType = action == null ? null : MainAgentActionTypeEnumVO.ofCode(action.getAction()).orElse(null);
            if (actionType == null) {
                return failRun(context, failureFactory.create(RuntimeFailureCodeEnumVO.MAIN_ACTION_CONTRACT_FAILED,
                        RuntimePhaseEnumVO.VALIDATING_ACTION, "MainAgentAction action type is missing or unknown.", true));
            }
            context.setLastAction(action);
            traceRecorder.actionParsed(context.getRunId(), context.getLoopIndex(), actionType, null);
            transcriptRecorder.appendAssistantAction(context.getRunId(), context.getLoopIndex(), action, null);

            transitionFailure = enterPhase(context, RuntimePhaseEnumVO.HANDLING_ACTION);
            if (transitionFailure != null) {
                return failRun(context, transitionFailure);
            }
            MainActionHandlerResult actionResult = actionDispatcher.dispatch(context, action);
            RuntimeStepResult stepResult = routeActionResult(context, action, actionResult);
            if (stepResult.getStatus() == RuntimeStepStatusEnumVO.CONTINUE) {
                context.countersOrInitial().incrementLoop();
                context.setLoopIndex(context.getLoopIndex() == null ? 1 : context.getLoopIndex() + 1);
                RuntimeSafeFailureVO loopTransition = enterPhase(context, RuntimePhaseEnumVO.PREPARING_CONTEXT);
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
            return pauseForUser(context, actionResult.getAskUserRequest(), MainAgentPendingInputHandler.HANDLER_CODE,
                    PendingInputTypeEnumVO.MAIN_AGENT_QUESTION.code(), actionResult.getMessage());
        }
        if (actionResult.getStatus() == MainActionHandlerStatusEnumVO.COMPLETED) {
            RuntimeSafeFailureVO verifyingFailure = enterPhase(context, RuntimePhaseEnumVO.VERIFYING_FINAL);
            if (verifyingFailure != null) {
                return failure(context.getRunId(), context.getSessionId(), verifyingFailure);
            }
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
        PendingInputCreateResult pending = userInteractionManager.createPendingInput(PendingInputCreateCommand.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .sourceComponent(handlerCode)
                .pendingType(pendingType)
                .askUserRequest(request)
                .continuation(ContinuationCheckpointVO.builder()
                        .handler(handlerCode)
                        .resumePhase(RuntimePhaseEnumVO.PREPARING_CONTEXT)
                        .sourceComponent(handlerCode)
                        .relatedRunId(context.getRunId())
                        .relatedLoopIndex(context.getLoopIndex())
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

    private RuntimeSafeFailureVO enterPhase(RuntimeExecutionContext context, RuntimePhaseEnumVO nextPhase) {
        RuntimeSafeFailureVO failure = phaseGuard.enter(context, nextPhase);
        if (failure == null) {
            runRepository.updateRunPhase(context.getRunId(), nextPhase);
            eventPublisher.phase(context.getRunId(), nextPhase.code(), nextPhase.info());
        }
        return failure;
    }

    private void applyRunResult(RuntimeExecutionContext context, RuntimeStepResult result) {
        if (result == null || result.getNextRunStatus() == null) {
            return;
        }
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
}

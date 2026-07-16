package yhx.com.domain.agent.service.interaction;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputConsumptionRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputResolutionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateResult;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputConsumptionResultVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveCommand;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeContinuationRestoreResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RunTranscriptRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;

import java.time.LocalDateTime;

@Slf4j
public class UserInteractionManager {

    private final PendingInputManager pendingInputManager;
    private final UserReplyProcessor userReplyProcessor;
    private final PendingInputContinuationDispatcher continuationDispatcher;
    private final IPayloadRepository payloadRepository;
    private final RunEventPublisher eventPublisher;
    private final RunTranscriptRecorder transcriptRecorder;
    private final RuntimeFailureFactory failureFactory;
    private final RuntimeContinuationSnapshotService continuationSnapshotService;
    private final PendingInputPauseCoordinator pauseCoordinator;
    private final IPendingInputConsumptionRepository consumptionRepository;
    private final AskUserRequestPolicy askUserRequestPolicy = new AskUserRequestPolicy();
    private final RuntimeUserClarificationRecorder clarificationRecorder = new RuntimeUserClarificationRecorder();

    public UserInteractionManager(PendingInputManager pendingInputManager,
                                  UserReplyProcessor userReplyProcessor,
                                  PendingInputContinuationDispatcher continuationDispatcher,
                                  IPayloadRepository payloadRepository,
                                  RunEventPublisher eventPublisher,
                                  RunTranscriptRecorder transcriptRecorder,
                                  RuntimeFailureFactory failureFactory) {
        this(pendingInputManager,
                userReplyProcessor,
                continuationDispatcher,
                payloadRepository,
                eventPublisher,
                transcriptRecorder,
                failureFactory,
                new RuntimeContinuationSnapshotService(),
                null,
                null);
    }

    public UserInteractionManager(PendingInputManager pendingInputManager,
                                  UserReplyProcessor userReplyProcessor,
                                  PendingInputContinuationDispatcher continuationDispatcher,
                                  IPayloadRepository payloadRepository,
                                  RunEventPublisher eventPublisher,
                                  RunTranscriptRecorder transcriptRecorder,
                                  RuntimeFailureFactory failureFactory,
                                  RuntimeContinuationSnapshotService continuationSnapshotService,
                                  PendingInputPauseCoordinator pauseCoordinator,
                                  IPendingInputConsumptionRepository consumptionRepository) {
        this.pendingInputManager = pendingInputManager;
        this.userReplyProcessor = userReplyProcessor;
        this.continuationDispatcher = continuationDispatcher;
        this.payloadRepository = payloadRepository;
        this.eventPublisher = eventPublisher;
        this.transcriptRecorder = transcriptRecorder;
        this.failureFactory = failureFactory;
        this.continuationSnapshotService = continuationSnapshotService == null
                ? new RuntimeContinuationSnapshotService()
                : continuationSnapshotService;
        this.pauseCoordinator = pauseCoordinator == null
                ? new PendingInputPauseCoordinator(pendingInputManager, null, eventPublisher, this.continuationSnapshotService)
                : pauseCoordinator;
        this.consumptionRepository = consumptionRepository;
    }

    public PendingInputCreateResult createPendingInput(PendingInputCreateCommand command) {
        String validation = validate(command == null ? null : command.getAskUserRequest());
        if (validation != null) {
            return PendingInputCreateResult.builder()
                    .created(false)
                    .failureMessage(validation)
                    .build();
        }
        return pauseCoordinator.pause(command);
    }

    public UserInputResolveResult resolveUserInput(UserInputResolveCommand command) {
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId(command == null ? null : command.getRunId())
                .currentPhase(RuntimePhaseEnumVO.RESOLVING_USER_ANSWER)
                .build();
        return resolveUserInput(command, context);
    }

    public UserInputResolveResult resolveUserInput(UserInputResolveCommand command, RuntimeExecutionContext context) {
        AgentPendingInputEntity pendingInput = findPendingInput(command);
        if (pendingInput == null) {
            return unresolved(command, null, PendingInputResolutionStatusEnumVO.NOT_FOUND,
                    "Pending input was not found.");
        }
        if (command == null || command.getRunId() == null || !command.getRunId().equals(pendingInput.getRunId())) {
            return unresolved(command, pendingInput, PendingInputResolutionStatusEnumVO.RUN_MISMATCH,
                    "Pending input belongs to another Run.");
        }
        if (!PendingInputStatusEnumVO.PENDING.code().equals(pendingInput.getStatus())) {
            return unresolved(command, pendingInput, PendingInputResolutionStatusEnumVO.ALREADY_RESOLVED,
                    "Pending input was already resolved.");
        }
        if (pendingInput.getExpiresAt() != null && !pendingInput.getExpiresAt().isAfter(LocalDateTime.now())) {
            pendingInputManager.markExpired(pendingInput.getPendingId(), pendingInput.getRunId());
            return unresolved(command, pendingInput, PendingInputResolutionStatusEnumVO.EXPIRED,
                    "Pending input has expired.");
        }
        UserAnswerVO answer = userReplyProcessor.process(pendingInput, command);
        if (answer.getStatus() == UserAnswerStatusEnumVO.FAILED) {
            return UserInputResolveResult.builder()
                    .pendingInputId(pendingInput.getPendingId())
                    .userAnswer(answer)
                    .resolved(false)
                    .resolutionStatus(PendingInputResolutionStatusEnumVO.INVALID_ANSWER)
                    .failureMessage(answer.getFailureMessage())
                    .continuationResult(RuntimeStepResult.builder()
                            .runId(pendingInput.getRunId())
                            .status(RuntimeStepStatusEnumVO.FAILED)
                            .nextRunStatus(null)
                            .nextPhase(RuntimePhaseEnumVO.RESOLVING_USER_ANSWER)
                            .safeFailure(failureFactory.invalidPendingAnswer(answer.getFailureMessage()))
                            .message(answer.getFailureMessage())
                            .build())
                    .build();
        }
        ContinuationCheckpointVO checkpoint = loadContinuation(pendingInput.getContinuationRef());
        RuntimeContinuationRestoreResultVO restoreResult = continuationSnapshotService.restore(checkpoint, context);
        if (!restoreResult.isRestored()) {
            pendingInputManager.markCancelled(pendingInput.getPendingId(), pendingInput.getRunId());
            return unresolved(command, pendingInput, PendingInputResolutionStatusEnumVO.CHECKPOINT_INVALID,
                    restoreResult.getMessage());
        }
        PendingInputConsumptionResultVO consumption = consume(pendingInput, answer);
        if (!consumption.isConsumed()) {
            log.info("[AutoAgent][pending-answer-ignored] runId={}, pendingId={}, reason=ALREADY_RESOLVED",
                    pendingInput.getRunId(), pendingInput.getPendingId());
            return unresolved(command, pendingInput, PendingInputResolutionStatusEnumVO.ALREADY_RESOLVED,
                    "Pending input was resolved by another request.");
        }
        log.info("[AutoAgent][pending-answer-accepted] runId={}, pendingId={}, answerStatus={}, loopIndex={}",
                pendingInput.getRunId(), pendingInput.getPendingId(), answer.getStatus(),
                context == null ? null : context.getLoopIndex());
        String answerRef = consumption.getUserAnswerRef();
        RuntimeStepResult continuationResult;
        try {
            if (answer.getStatus() != UserAnswerStatusEnumVO.CANCELLED) {
                appendUserClarification(context, pendingInput, answer);
                transcriptRecorder.appendUserReply(pendingInput.getRunId(),
                        context == null ? null : context.getLoopIndex(), answer, answerRef);
            }
            if (context != null && context.getRuntimeFacts() != null && checkpoint != null) {
                context.getRuntimeFacts().put("continuationCheckpoint", checkpoint);
            }
            continuationResult = continuationDispatcher.dispatch(answer, checkpoint, context);
        } catch (RuntimeException e) {
            log.error("[AutoAgent][post-consumption-continuation-failed] runId={}, pendingId={}",
                    pendingInput.getRunId(), pendingInput.getPendingId(), e);
            RuntimeSafeFailureVO failure = failureFactory.create(
                    RuntimeFailureCodeEnumVO.MISSING_ACTIVE_PENDING_INPUT,
                    RuntimePhaseEnumVO.RESOLVING_USER_ANSWER,
                    "Continuation failed after PendingInput consumption: " + safeMessage(e),
                    false);
            continuationResult = RuntimeStepResult.builder()
                    .runId(pendingInput.getRunId())
                    .sessionId(context == null ? null : context.getSessionId())
                    .status(RuntimeStepStatusEnumVO.FAILED)
                    .nextRunStatus(RunStatusEnumVO.FAILED)
                    .nextPhase(RuntimePhaseEnumVO.FAILED)
                    .safeFailure(failure)
                    .message(failure.getDeveloperMessage())
                    .build();
        }
        return UserInputResolveResult.builder()
                .pendingInputId(pendingInput.getPendingId())
                .userAnswer(answer)
                .resolved(continuationResult.getStatus() != RuntimeStepStatusEnumVO.FAILED)
                .resolutionStatus(PendingInputResolutionStatusEnumVO.RESOLVED)
                .continuationResult(continuationResult)
                .failureMessage(continuationResult.getMessage())
                .build();
    }

    private AgentPendingInputEntity findPendingInput(UserInputResolveCommand command) {
        if (command == null) {
            return null;
        }
        if (command.getPendingId() != null && !command.getPendingId().isBlank()) {
            return pendingInputManager.findByPendingId(command.getPendingId()).orElse(null);
        }
        if (command.getRunId() != null && !command.getRunId().isBlank()) {
            return pendingInputManager.findActiveByRunId(command.getRunId()).orElse(null);
        }
        return null;
    }

    private String validate(AskUserRequestVO request) {
        return askUserRequestPolicy.normalizeAndValidate(request);
    }

    private String savePayload(Object value) {
        if (payloadRepository == null) {
            return null;
        }
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(value))
                .preview("user-answer")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private ContinuationCheckpointVO loadContinuation(String continuationRef) {
        if (payloadRepository == null || continuationRef == null) {
            return null;
        }
        try {
            return payloadRepository.findContent(continuationRef)
                    .map(content -> JSON.parseObject(content, ContinuationCheckpointVO.class))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("[AutoAgent][checkpoint-load-failed] continuationRef={}, error={}",
                    continuationRef, e.getMessage());
            return null;
        }
    }

    private void appendUserClarification(RuntimeExecutionContext context, AgentPendingInputEntity pendingInput, UserAnswerVO answer) {
        if (context == null || context.getRuntimeFacts() == null || pendingInput == null || answer == null) {
            return;
        }
        clarificationRecorder.append(context, UserClarificationVO.builder()
                .sourceComponent(pendingInput.getSourceComponent())
                .pendingId(pendingInput.getPendingId())
                .question(pendingInput.getQuestion())
                .answerType(answer.getAnswerType() == null ? null : answer.getAnswerType().code())
                .selectedOptionId(answer.getSelectedOptionId())
                .value(answer.getValue())
                .freeText(answer.getFreeText())
                .metadata(answer.getMetadata())
                .build());
    }

    private UserInputResolveResult failed(UserInputResolveCommand command,
                                          RuntimeSafeFailureVO failure,
                                          PendingInputResolutionStatusEnumVO resolutionStatus) {
        return UserInputResolveResult.builder()
                .pendingInputId(command == null ? null : command.getPendingId())
                .resolved(false)
                .resolutionStatus(resolutionStatus)
                .continuationResult(RuntimeStepResult.builder()
                        .runId(command == null ? null : command.getRunId())
                        .status(RuntimeStepStatusEnumVO.FAILED)
                        .nextRunStatus(RunStatusEnumVO.FAILED)
                        .nextPhase(RuntimePhaseEnumVO.FAILED)
                        .safeFailure(failure)
                        .message(failure.getDeveloperMessage())
                        .build())
                .failureMessage(failure.getDeveloperMessage())
                .build();
    }

    private String safeMessage(RuntimeException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private PendingInputConsumptionResultVO consume(AgentPendingInputEntity pendingInput, UserAnswerVO answer) {
        boolean cancelled = answer.getStatus() == UserAnswerStatusEnumVO.CANCELLED;
        if (consumptionRepository != null) {
            try {
                return consumptionRepository.consume(pendingInput.getPendingId(), pendingInput.getRunId(), answer, cancelled);
            } catch (PendingInputConsumptionConflictException ignored) {
                return PendingInputConsumptionResultVO.builder().consumed(false).build();
            }
        }
        String answerRef = cancelled ? null : savePayload(answer);
        boolean consumed = cancelled
                ? pendingInputManager.markCancelled(pendingInput.getPendingId(), pendingInput.getRunId())
                : pendingInputManager.markAnswered(pendingInput.getPendingId(), pendingInput.getRunId(), answerRef);
        return PendingInputConsumptionResultVO.builder()
                .consumed(consumed)
                .userAnswerRef(consumed ? answerRef : null)
                .build();
    }

    private UserInputResolveResult unresolved(UserInputResolveCommand command,
                                              AgentPendingInputEntity pendingInput,
                                              PendingInputResolutionStatusEnumVO status,
                                              String message) {
        RuntimeSafeFailureVO failure = failureFactory.invalidPendingAnswer(message);
        boolean terminal = status == PendingInputResolutionStatusEnumVO.EXPIRED
                || status == PendingInputResolutionStatusEnumVO.CHECKPOINT_INVALID;
        return UserInputResolveResult.builder()
                .pendingInputId(pendingInput == null
                        ? (command == null ? null : command.getPendingId())
                        : pendingInput.getPendingId())
                .resolved(false)
                .resolutionStatus(status)
                .continuationResult(RuntimeStepResult.builder()
                        .runId(command == null ? null : command.getRunId())
                        .status(RuntimeStepStatusEnumVO.FAILED)
                        .nextRunStatus(terminal ? RunStatusEnumVO.FAILED : null)
                        .nextPhase(terminal ? RuntimePhaseEnumVO.FAILED : RuntimePhaseEnumVO.RESOLVING_USER_ANSWER)
                        .safeFailure(failure)
                        .message(message)
                        .build())
                .failureMessage(message)
                .build();
    }
}

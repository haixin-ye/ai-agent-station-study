package yhx.com.domain.agent.service.interaction;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateResult;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveCommand;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RunTranscriptRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserInteractionManager {

    private final PendingInputManager pendingInputManager;
    private final UserReplyProcessor userReplyProcessor;
    private final PendingInputContinuationDispatcher continuationDispatcher;
    private final IPayloadRepository payloadRepository;
    private final RunEventPublisher eventPublisher;
    private final RunTranscriptRecorder transcriptRecorder;
    private final RuntimeFailureFactory failureFactory;

    public UserInteractionManager(PendingInputManager pendingInputManager,
                                  UserReplyProcessor userReplyProcessor,
                                  PendingInputContinuationDispatcher continuationDispatcher,
                                  IPayloadRepository payloadRepository,
                                  RunEventPublisher eventPublisher,
                                  RunTranscriptRecorder transcriptRecorder,
                                  RuntimeFailureFactory failureFactory) {
        this.pendingInputManager = pendingInputManager;
        this.userReplyProcessor = userReplyProcessor;
        this.continuationDispatcher = continuationDispatcher;
        this.payloadRepository = payloadRepository;
        this.eventPublisher = eventPublisher;
        this.transcriptRecorder = transcriptRecorder;
        this.failureFactory = failureFactory;
    }

    public PendingInputCreateResult createPendingInput(PendingInputCreateCommand command) {
        String validation = validate(command == null ? null : command.getAskUserRequest());
        if (validation != null) {
            return PendingInputCreateResult.builder()
                    .created(false)
                    .failureMessage(validation)
                    .build();
        }
        String pendingId = pendingInputManager.create(command);
        eventPublisher.askingUser(command.getRunId(), pendingId, command.getAskUserRequest());
        return PendingInputCreateResult.builder()
                .pendingInputId(pendingId)
                .runId(command.getRunId())
                .created(true)
                .build();
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
            RuntimeSafeFailureVO failure = failureFactory.missingPendingInput(command == null ? null : command.getRunId());
            return failed(command, failure);
        }
        UserAnswerVO answer = userReplyProcessor.process(pendingInput, command);
        if (answer.getStatus() == UserAnswerStatusEnumVO.FAILED) {
            return UserInputResolveResult.builder()
                    .pendingInputId(pendingInput.getPendingId())
                    .userAnswer(answer)
                    .resolved(false)
                    .failureMessage(answer.getFailureMessage())
                    .continuationResult(RuntimeStepResult.builder()
                            .runId(pendingInput.getRunId())
                            .status(RuntimeStepStatusEnumVO.FAILED)
                            .nextRunStatus(RunStatusEnumVO.FAILED)
                            .nextPhase(RuntimePhaseEnumVO.FAILED)
                            .safeFailure(failureFactory.invalidPendingAnswer(answer.getFailureMessage()))
                            .message(answer.getFailureMessage())
                            .build())
                    .build();
        }
        if (answer.getStatus() == UserAnswerStatusEnumVO.CANCELLED) {
            pendingInputManager.markCancelled(pendingInput.getPendingId());
        } else {
            String answerRef = savePayload(answer);
            pendingInputManager.markAnswered(pendingInput.getPendingId(), answerRef);
            appendUserClarification(context, pendingInput, answer);
            transcriptRecorder.appendUserReply(pendingInput.getRunId(), context == null ? null : context.getLoopIndex(), answer, answerRef);
        }
        ContinuationCheckpointVO checkpoint = loadContinuation(pendingInput.getContinuationRef());
        if (context != null && context.getRuntimeFacts() != null && checkpoint != null) {
            context.getRuntimeFacts().put("continuationCheckpoint", checkpoint);
        }
        RuntimeStepResult continuationResult = continuationDispatcher.dispatch(answer, checkpoint, context);
        return UserInputResolveResult.builder()
                .pendingInputId(pendingInput.getPendingId())
                .userAnswer(answer)
                .resolved(continuationResult.getStatus() != RuntimeStepStatusEnumVO.FAILED)
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
        if (request == null) {
            return "AskUserRequest is missing.";
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return "AskUserRequest.question is required.";
        }
        if (request.getInputMode() == null || request.getInputMode().isBlank()) {
            return "AskUserRequest.inputMode is required.";
        }
        return null;
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
        return payloadRepository.findContent(continuationRef)
                .map(content -> JSON.parseObject(content, ContinuationCheckpointVO.class))
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void appendUserClarification(RuntimeExecutionContext context, AgentPendingInputEntity pendingInput, UserAnswerVO answer) {
        if (context == null || context.getRuntimeFacts() == null || pendingInput == null || answer == null) {
            return;
        }
        Object existing = context.getRuntimeFacts().get("userClarifications");
        List<UserClarificationVO> clarifications;
        if (existing instanceof List<?> list) {
            clarifications = (List<UserClarificationVO>) list;
        } else {
            clarifications = new ArrayList<>();
            context.getRuntimeFacts().put("userClarifications", clarifications);
        }
        clarifications.add(UserClarificationVO.builder()
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

    private UserInputResolveResult failed(UserInputResolveCommand command, RuntimeSafeFailureVO failure) {
        return UserInputResolveResult.builder()
                .pendingInputId(command == null ? null : command.getPendingId())
                .resolved(false)
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
}

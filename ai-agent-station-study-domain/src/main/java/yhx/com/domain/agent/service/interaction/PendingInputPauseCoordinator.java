package yhx.com.domain.agent.service.interaction;

import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.adapter.transaction.IInteractionTransactionExecutor;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;

import java.util.Map;

@Slf4j
public class PendingInputPauseCoordinator {

    private final PendingInputManager pendingInputManager;
    private final IRunRepository runRepository;
    private final RunEventPublisher eventPublisher;
    private final RuntimeContinuationSnapshotService snapshotService;
    private final IInteractionTransactionExecutor transactionExecutor;

    public PendingInputPauseCoordinator(PendingInputManager pendingInputManager,
                                        IRunRepository runRepository,
                                        RunEventPublisher eventPublisher,
                                        RuntimeContinuationSnapshotService snapshotService) {
        this(pendingInputManager, runRepository, eventPublisher, snapshotService, null);
    }

    public PendingInputPauseCoordinator(PendingInputManager pendingInputManager,
                                        IRunRepository runRepository,
                                        RunEventPublisher eventPublisher,
                                        RuntimeContinuationSnapshotService snapshotService,
                                        IInteractionTransactionExecutor transactionExecutor) {
        this.pendingInputManager = pendingInputManager;
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
        this.snapshotService = snapshotService == null ? new RuntimeContinuationSnapshotService() : snapshotService;
        this.transactionExecutor = transactionExecutor;
    }

    public PendingInputCreateResult pause(PendingInputCreateCommand command) {
        if (command == null || command.getContinuation() == null) {
            return failed(command, "Pending input continuation metadata is required.");
        }
        if (command.getRuntimeContext() == null) {
            return failed(command, "Pending input requires the current Runtime context for exact recovery.");
        }
        ContinuationCheckpointVO requested = command.getContinuation();
        ContinuationCheckpointVO checkpoint;
        try {
            checkpoint = snapshotService.createCheckpoint(
                    command.getRuntimeContext(),
                    requested.getHandler(),
                    requested.getResumePhase(),
                    requested.getSourceComponent(),
                    requested.getExpectedAnswerValueType(),
                    sourcePayload(requested));
        } catch (IllegalArgumentException e) {
            return failed(command, e.getMessage());
        }
        command.setContinuation(checkpoint);
        try {
            return transactionExecutor == null
                    ? persistPause(command)
                    : transactionExecutor.execute(() -> persistPause(command));
        } catch (RuntimeException e) {
            return failed(command, "Pending input pause could not be persisted atomically: " + e.getMessage());
        }
    }

    private PendingInputCreateResult persistPause(PendingInputCreateCommand command) {
        if (pendingInputManager.findActiveByRunId(command.getRunId()).isPresent()) {
            return failed(command, "Run already has an active PendingInput.");
        }
        String pendingId = pendingInputManager.create(command);
        if (runRepository != null) {
            runRepository.updateRunPhase(command.getRunId(), RuntimePhaseEnumVO.WAITING_USER);
            runRepository.updateRunStatus(command.getRunId(), RunStatusEnumVO.WAITING_USER, null);
        }
        if (eventPublisher != null) {
            eventPublisher.askingUser(command.getRunId(), pendingId, command.getAskUserRequest());
        }
        log.info("[AutoAgent][run-paused] runId={}, pendingId={}, sourceComponent={}, handler={}",
                command.getRunId(), pendingId, command.getSourceComponent(), command.getContinuation().getHandler());
        return PendingInputCreateResult.builder()
                .pendingInputId(pendingId)
                .runId(command.getRunId())
                .created(true)
                .build();
    }

    private Map<String, Object> sourcePayload(ContinuationCheckpointVO checkpoint) {
        return checkpoint.getPayload() == null ? Map.of() : checkpoint.getPayload();
    }

    private PendingInputCreateResult failed(PendingInputCreateCommand command, String message) {
        return PendingInputCreateResult.builder()
                .runId(command == null ? null : command.getRunId())
                .created(false)
                .failureMessage(message)
                .build();
    }
}

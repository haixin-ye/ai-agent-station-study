package yhx.com.test.domain.agent.runtime.support;

import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.adapter.repository.IRunTranscriptRepository;
import yhx.com.domain.agent.adapter.repository.IRunContextRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunAuditEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTranscriptEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunContextEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunLoopEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionEntity;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.RunEventTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TranscriptBlockTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.interaction.ContextPlannerPendingInputHandler;
import yhx.com.domain.agent.service.interaction.FinalRepairPendingInputHandler;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.PendingInputContinuationDispatcher;
import yhx.com.domain.agent.service.interaction.PendingInputManager;
import yhx.com.domain.agent.service.interaction.RagPendingInputHandler;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.interaction.UserReplyProcessor;
import yhx.com.domain.agent.service.runtime.DefaultAutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.NoopMainActionDispatcher;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RunTranscriptRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeComponentPorts;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.domain.agent.service.runtime.RuntimeRoutePolicy;
import yhx.com.domain.agent.service.runtime.RuntimePhaseGuard;
import yhx.com.domain.agent.service.runtime.RuntimeStateMachine;
import yhx.com.domain.agent.service.agent.GenericSubAgentDispatchOrchestrator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RuntimeTestSupport {

    public static DefaultAutoAgentRuntimeService runtime(InMemoryRuntimeRepository repository,
                                                         RuntimeComponentPorts componentPorts,
                                                         boolean testMode,
                                                         RuntimeLoopPolicy loopPolicy) {
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        return runtime(repository, componentPorts,
                new NoopMainActionDispatcher(testMode, failureFactory), loopPolicy, failureFactory, null);
    }

    public static DefaultAutoAgentRuntimeService runtime(InMemoryRuntimeRepository repository,
                                                         RuntimeComponentPorts componentPorts,
                                                         MainActionDispatcher actionDispatcher,
                                                         RuntimeLoopPolicy loopPolicy) {
        return runtime(repository, componentPorts, actionDispatcher, loopPolicy, new RuntimeFailureFactory(), null);
    }

    public static DefaultAutoAgentRuntimeService runtime(InMemoryRuntimeRepository repository,
                                                         RuntimeComponentPorts componentPorts,
                                                         MainActionDispatcher actionDispatcher,
                                                         RuntimeLoopPolicy loopPolicy,
                                                         GenericSubAgentDispatchOrchestrator subAgentOrchestrator) {
        return runtime(repository, componentPorts, actionDispatcher, loopPolicy,
                new RuntimeFailureFactory(), subAgentOrchestrator);
    }

    private static DefaultAutoAgentRuntimeService runtime(InMemoryRuntimeRepository repository,
                                                          RuntimeComponentPorts componentPorts,
                                                          MainActionDispatcher actionDispatcher,
                                                          RuntimeLoopPolicy loopPolicy,
                                                          RuntimeFailureFactory failureFactory,
                                                          GenericSubAgentDispatchOrchestrator subAgentOrchestrator) {
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        PendingInputContinuationDispatcher continuationDispatcher = defaultContinuationDispatcher();
        UserInteractionManager interactionManager = new UserInteractionManager(
                new PendingInputManager(repository, repository),
                new UserReplyProcessor(repository),
                continuationDispatcher,
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();
        if (subAgentOrchestrator != null) {
            return new DefaultAutoAgentRuntimeService(
                    repository, repository, repository, componentPorts, actionDispatcher, interactionManager,
                    loopPolicy, new RuntimeRoutePolicy(), stateMachine, failureFactory,
                    new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder), eventPublisher,
                    transcriptRecorder, traceRecorder, null, null, subAgentOrchestrator, repository);
        }
        return new DefaultAutoAgentRuntimeService(
                repository,
                repository,
                repository,
                componentPorts,
                actionDispatcher,
                interactionManager,
                loopPolicy,
                new RuntimeRoutePolicy(),
                stateMachine,
                failureFactory,
                new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder),
                eventPublisher,
                transcriptRecorder,
                traceRecorder,
                null,
                null,
                null,
                repository);
    }

    public static PendingInputContinuationDispatcher defaultContinuationDispatcher() {
        return new PendingInputContinuationDispatcher(List.of(
                new ContextPlannerPendingInputHandler(),
                new MainAgentPendingInputHandler(),
                new ToolApprovalPendingInputHandler(),
                new RagPendingInputHandler(),
                new FinalRepairPendingInputHandler()));
    }

    public static RuntimeComponentPorts fixedPorts(MainAgentActionVO action) {
        return new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return ContextPlannerHandlingResult.builder()
                        .stateView(MainAgentStateViewVO.builder().build())
                        .effectiveSelections(List.of())
                        .build();
            }

            @Override
            public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
                return ContextPlannerHandlingResult.builder()
                        .stateView(MainAgentStateViewVO.builder().build())
                        .effectiveSelections(List.of())
                        .build();
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                return action;
            }
        };
    }

    public static RuntimeComponentPorts loopingPorts(MainAgentActionVO action) {
        return fixedPorts(action);
    }

    public static class InMemoryRuntimeRepository implements IPayloadRepository,
            IConversationRepository,
            IRunRepository,
            IPendingInputRepository,
            IEventTraceRepository,
            IRunTranscriptRepository,
            IRunContextRepository {

        public final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        public final Map<String, AgentSessionEntity> sessions = new LinkedHashMap<>();
        public final Map<String, AgentRunEntity> runs = new LinkedHashMap<>();
        public final Map<String, AgentPendingInputEntity> pendingInputs = new LinkedHashMap<>();
        public final List<AgentMessageEntity> messages = new ArrayList<>();
        public final List<AgentRunEventEntity> events = new ArrayList<>();
        public final List<AgentRunTraceEntity> traces = new ArrayList<>();
        public final List<AgentRunAuditEntity> audits = new ArrayList<>();
        public final List<AgentRunTranscriptEntity> transcriptBlocks = new ArrayList<>();
        public final Map<String, AgentRunContextEntity> runContexts = new LinkedHashMap<>();
        public final Map<String, Map<Integer, AgentRunLoopEntity>> runLoops = new LinkedHashMap<>();

        @Override
        public void createContext(AgentRunContextEntity context) {
            runContexts.put(context.getRunId(), context);
        }

        @Override
        public boolean updateContext(AgentRunContextEntity context, long expectedVersion) {
            AgentRunContextEntity existing = runContexts.get(context.getRunId());
            if (existing == null || existing.getContextVersion() == null || existing.getContextVersion() != expectedVersion) {
                return false;
            }
            runContexts.put(context.getRunId(), context);
            return true;
        }

        @Override
        public Optional<AgentRunContextEntity> findContext(String runId) {
            return Optional.ofNullable(runContexts.get(runId));
        }

        @Override
        public void saveLoop(AgentRunLoopEntity loop) {
            runLoops.computeIfAbsent(loop.getRunId(), ignored -> new LinkedHashMap<>())
                    .put(loop.getLoopIndex(), loop);
        }

        @Override
        public Optional<AgentRunLoopEntity> findLoop(String runId, Integer loopIndex) {
            return Optional.ofNullable(runLoops.getOrDefault(runId, Map.of()).get(loopIndex));
        }

        @Override
        public List<AgentRunLoopEntity> listLoops(String runId) {
            return new ArrayList<>(runLoops.getOrDefault(runId, Map.of()).values());
        }

        @Override
        public synchronized String savePayload(AgentPayloadEntity payload) {
            String payloadId = payload.getPayloadId() == null ? "payload-" + UUID.randomUUID() : payload.getPayloadId();
            payload.setPayloadId(payloadId);
            payloads.put(payloadId, payload);
            return payloadId;
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.ofNullable(payloads.get(payloadId));
        }

        @Override
        public String createSession(AgentSessionEntity session) {
            sessions.put(session.getSessionId(), session);
            return session.getSessionId();
        }

        @Override
        public Optional<AgentSessionEntity> findSession(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public void appendMessage(AgentMessageEntity message) {
            messages.add(message);
        }

        @Override
        public Optional<AgentMessageEntity> findMessageById(String messageId) {
            return messages.stream().filter(message -> messageId.equals(message.getMessageId())).findFirst();
        }

        @Override
        public List<AgentMessageEntity> listRecentVisibleMessages(String sessionId, int limit) {
            return messages.stream()
                    .filter(message -> sessionId.equals(message.getSessionId()))
                    .filter(message -> Boolean.TRUE.equals(message.getVisibleToUser()))
                    .sorted(Comparator.comparing(AgentMessageEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        @Override
        public String createRun(AgentRunEntity run) {
            runs.put(run.getRunId(), run);
            return run.getRunId();
        }

        @Override
        public void updateRunPhase(String runId, RuntimePhaseEnumVO phase) {
            runs.get(runId).setPhase(phase);
        }

        @Override
        public void updateRunStatus(String runId, RunStatusEnumVO status, String failureCode) {
            AgentRunEntity run = runs.get(runId);
            run.setStatus(status);
            run.setFailureCode(failureCode);
        }

        @Override
        public void updateFinalAnswerRef(String runId, String finalAnswerRef) {
            runs.get(runId).setFinalAnswerRef(finalAnswerRef);
        }

        @Override
        public void markRagWasUsed(String runId) {
            runs.get(runId).setRagWasUsed(true);
        }

        @Override
        public Optional<AgentRunEntity> findRun(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public String createPendingInput(AgentPendingInputEntity pendingInput) {
            String pendingId = pendingInput.getPendingId() == null ? "pending-" + UUID.randomUUID() : pendingInput.getPendingId();
            pendingInput.setPendingId(pendingId);
            pendingInputs.put(pendingId, pendingInput);
            return pendingId;
        }

        @Override
        public synchronized int markAnswered(String pendingId, String runId, String userAnswerRef) {
            AgentPendingInputEntity pendingInput = pendingInputs.get(pendingId);
            if (!canConsume(pendingInput, runId)) {
                return 0;
            }
            pendingInput.setStatus("ANSWERED");
            pendingInput.setUserAnswerRef(userAnswerRef);
            pendingInput.setAnsweredAt(java.time.LocalDateTime.now());
            return 1;
        }

        @Override
        public Optional<AgentPendingInputEntity> findActivePendingInput(String runId) {
            return pendingInputs.values().stream()
                    .filter(pendingInput -> runId.equals(pendingInput.getRunId()))
                    .filter(pendingInput -> "PENDING".equals(pendingInput.getStatus()))
                    .findFirst();
        }

        @Override
        public Optional<AgentPendingInputEntity> findByPendingId(String pendingId) {
            return Optional.ofNullable(pendingInputs.get(pendingId));
        }

        @Override
        public synchronized int markCancelled(String pendingId, String runId) {
            AgentPendingInputEntity pendingInput = pendingInputs.get(pendingId);
            if (!canConsume(pendingInput, runId)) {
                return 0;
            }
            pendingInput.setStatus("CANCELLED");
            pendingInput.setAnsweredAt(java.time.LocalDateTime.now());
            return 1;
        }

        @Override
        public synchronized int markExpired(String pendingId, String runId) {
            AgentPendingInputEntity pendingInput = pendingInputs.get(pendingId);
            if (pendingInput == null || !runId.equals(pendingInput.getRunId())
                    || !"PENDING".equals(pendingInput.getStatus())
                    || pendingInput.getExpiresAt() == null
                    || pendingInput.getExpiresAt().isAfter(java.time.LocalDateTime.now())) {
                return 0;
            }
            pendingInput.setStatus("EXPIRED");
            return 1;
        }

        private boolean canConsume(AgentPendingInputEntity pendingInput, String runId) {
            return pendingInput != null
                    && runId != null
                    && runId.equals(pendingInput.getRunId())
                    && "PENDING".equals(pendingInput.getStatus())
                    && (pendingInput.getExpiresAt() == null
                    || pendingInput.getExpiresAt().isAfter(java.time.LocalDateTime.now()));
        }

        @Override
        public void appendUserVisibleEvent(AgentRunEventEntity event) {
            if (event.getEventType() == null) {
                event.setEventType(RunEventTypeEnumVO.STATUS_CHANGED);
            }
            events.add(event);
        }

        @Override
        public void appendTrace(AgentRunTraceEntity trace) {
            if (trace.getTraceType() == null) {
                trace.setTraceType(TraceTypeEnumVO.RUNTIME_DECISION);
            }
            traces.add(trace);
        }

        @Override
        public void appendAudit(AgentRunAuditEntity audit) {
            audits.add(audit);
        }

        @Override
        public List<AgentRunEventEntity> listUserVisibleEvents(String runId, int limit) {
            return events.stream()
                    .filter(event -> runId.equals(event.getRunId()))
                    .filter(event -> Boolean.TRUE.equals(event.getUserVisible()))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        @Override
        public List<AgentRunTraceEntity> listDebugTrace(String runId, int limit) {
            return traces.stream()
                    .filter(trace -> runId.equals(trace.getRunId()))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        @Override
        public String appendBlock(AgentRunTranscriptEntity block) {
            String blockId = block.getBlockId() == null ? "block-" + UUID.randomUUID() : block.getBlockId();
            block.setBlockId(blockId);
            block.setSeq((long) transcriptBlocks.size() + 1);
            if (block.getBlockType() == null) {
                block.setBlockType(TranscriptBlockTypeEnumVO.RUNTIME_EVENT);
            }
            transcriptBlocks.add(block);
            return blockId;
        }

        @Override
        public List<AgentRunTranscriptEntity> listRunBlocks(String runId) {
            return transcriptBlocks.stream().filter(block -> runId.equals(block.getRunId())).collect(Collectors.toList());
        }

        @Override
        public List<AgentRunTranscriptEntity> listBlocksForCompaction(String runId, Long beforeSeq) {
            return transcriptBlocks.stream()
                    .filter(block -> runId.equals(block.getRunId()))
                    .filter(block -> beforeSeq == null || block.getSeq() < beforeSeq)
                    .collect(Collectors.toList());
        }

        @Override
        public String appendCompactionSummary(AgentRunTranscriptEntity block) {
            return appendBlock(block);
        }
    }
}

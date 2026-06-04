package yhx.com.domain.agent.service.finalresponse;

import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.FinalDeliveryStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.finalresponse.FinalRepairPromptContextVO;
import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseGuardInputVO;
import yhx.com.domain.agent.model.valobj.finalresponse.FinalResponseVO;
import yhx.com.domain.agent.model.valobj.invocation.FinalResponseGuardResultVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerificationRouteCommandVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerificationRouteResultVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;
import yhx.com.domain.agent.service.node.finalrepair.FinalRepairNodeService;
import yhx.com.domain.agent.service.rag.runtime.RagVerificationRouter;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.FinalDeliveryPort;

public class FinalDeliveryService implements FinalDeliveryPort {

    private static final int DEFAULT_MAX_FINAL_REPAIR_ATTEMPTS = 2;

    private final IRunRepository runRepository;
    private final RagVerificationRouter ragVerificationRouter;
    private final FinalResponseGuardInputBuilder guardInputBuilder;
    private final FinalResponseGuard finalResponseGuard;
    private final FinalResponseBuilder finalResponseBuilder;
    private final FinalRepairNodeService finalRepairNodeService;
    private final FixedSafeFallbackFactory fallbackFactory;
    private final FinalResponsePersistenceService persistenceService;
    private final RuntimeFailureFactory failureFactory;
    private final RunEventPublisher eventPublisher;
    private final boolean finalResponseGuardEnabled;

    public FinalDeliveryService(IRunRepository runRepository,
                                RagVerificationRouter ragVerificationRouter,
                                FinalResponseGuardInputBuilder guardInputBuilder,
                                FinalResponseGuard finalResponseGuard,
                                FinalResponseBuilder finalResponseBuilder,
                                FinalRepairNodeService finalRepairNodeService,
                                FixedSafeFallbackFactory fallbackFactory,
                                FinalResponsePersistenceService persistenceService,
                                RuntimeFailureFactory failureFactory,
                                RunEventPublisher eventPublisher) {
        this(runRepository,
                ragVerificationRouter,
                guardInputBuilder,
                finalResponseGuard,
                finalResponseBuilder,
                finalRepairNodeService,
                fallbackFactory,
                persistenceService,
                failureFactory,
                eventPublisher,
                true);
    }

    public FinalDeliveryService(IRunRepository runRepository,
                                RagVerificationRouter ragVerificationRouter,
                                FinalResponseGuardInputBuilder guardInputBuilder,
                                FinalResponseGuard finalResponseGuard,
                                FinalResponseBuilder finalResponseBuilder,
                                FinalRepairNodeService finalRepairNodeService,
                                FixedSafeFallbackFactory fallbackFactory,
                                FinalResponsePersistenceService persistenceService,
                                RuntimeFailureFactory failureFactory,
                                RunEventPublisher eventPublisher,
                                boolean finalResponseGuardEnabled) {
        this.runRepository = runRepository;
        this.ragVerificationRouter = ragVerificationRouter;
        this.guardInputBuilder = guardInputBuilder;
        this.finalResponseGuard = finalResponseGuard;
        this.finalResponseBuilder = finalResponseBuilder;
        this.finalRepairNodeService = finalRepairNodeService;
        this.fallbackFactory = fallbackFactory;
        this.persistenceService = persistenceService;
        this.failureFactory = failureFactory;
        this.eventPublisher = eventPublisher;
        this.finalResponseGuardEnabled = finalResponseGuardEnabled;
    }

    @Override
    public FinalDeliveryResultVO deliver(FinalDeliveryCommandVO command) {
        FinalDeliveryCommandVO normalized = normalize(command);
        persistenceService.saveCandidateDebugPayload(normalized);
        RagVerificationRouteResultVO ragResult = verifyRagIfNeeded(normalized);
        if (ragResult != null && ragResult.getFailureCode() != null) {
            return repairOrFallback(normalized, "RAG_VERIFICATION_FAILED", ragResult.getMessage());
        }
        if (!finalResponseGuardEnabled) {
            return deliverWithoutFinalGuard(normalized);
        }
        FinalResponseGuardInputVO guardInput = guardInputBuilder.build(normalized);
        FinalResponseGuardResultVO guardResult = finalResponseGuard.check(guardInput);
        persistenceService.saveGuardDetail(normalized.getRunId(), guardResult);
        if ("FAILED".equals(guardResult.getStatus())) {
            return repairOrFallback(normalized, guardResult.getFailureCode(), guardResult.getDetail());
        }
        FinalResponseVO finalResponse = finalResponseBuilder.build(normalized, normalized.getFinalAnswerCandidate(), null);
        FinalResponseVO persisted = persistenceService.persistDelivered(normalized, finalResponse);
        return FinalDeliveryResultVO.builder()
                .status(FinalDeliveryStatusEnumVO.DELIVERED)
                .completed(true)
                .finalResponse(persisted)
                .guardResult(guardResult)
                .finalMessageId(persisted.getMessageId())
                .finalAnswerRef(persisted.getContentRef())
                .deliveredContent(persisted.getContent())
                .message("Final response delivered.")
                .build();
    }

    private FinalDeliveryResultVO deliverWithoutFinalGuard(FinalDeliveryCommandVO command) {
        FinalResponseGuardResultVO guardResult = FinalResponseGuardResultVO.builder()
                .status("PASSED")
                .finalContent(command.getFinalAnswerCandidate() == null ? null : command.getFinalAnswerCandidate().getContent())
                .failureCode(null)
                .detail("Final response guard disabled by configuration.")
                .build();
        FinalResponseVO finalResponse = finalResponseBuilder.build(command, command.getFinalAnswerCandidate(), null);
        FinalResponseVO persisted = persistenceService.persistDelivered(command, finalResponse);
        return FinalDeliveryResultVO.builder()
                .status(FinalDeliveryStatusEnumVO.DELIVERED)
                .completed(true)
                .finalResponse(persisted)
                .guardResult(guardResult)
                .finalMessageId(persisted.getMessageId())
                .finalAnswerRef(persisted.getContentRef())
                .deliveredContent(persisted.getContent())
                .message("Final response delivered with final guard disabled.")
                .build();
    }

    private FinalDeliveryResultVO repairOrFallback(FinalDeliveryCommandVO command, String failureCode, String detail) {
        if (repairBudgetRemains(command) && finalRepairNodeService != null) {
            FinalAnswerCandidateVO repaired = finalRepairNodeService.repair(FinalRepairPromptContextVO.builder()
                    .runId(command.getRunId())
                    .agentId(command.getAgentId())
                    .loopIndex(command.getLoopIndex())
                    .userInput(command.getUserInput())
                    .failedCandidate(command.getFinalAnswerCandidate())
                    .failureCode(failureCode)
                    .guardSummary(detail)
                    .repairInstruction("Rewrite only the user-facing final answer. Do not mention repair, runtime, nodes, trace, validation, prompts, contracts, or tool receipts.")
                    .build());
            if (repaired != null) {
                FinalDeliveryCommandVO repairedCommand = copyWithCandidate(command, repaired);
                repairedCommand.setSourceAction(MainAgentActionTypeEnumVO.REPAIR_FINAL);
                repairedCommand.setFinalRepairCount((command.getFinalRepairCount() == null ? 0 : command.getFinalRepairCount()) + 1);
                FinalDeliveryResultVO result = deliver(repairedCommand);
                result.setRepairRequested(true);
                return result;
            }
        }
        FinalDeliveryCommandVO fallbackCommand = copyWithCandidate(command, fallbackFactory.create());
        FinalResponseGuardResultVO fallbackGuard = finalResponseGuard.check(guardInputBuilder.build(fallbackCommand));
        persistenceService.saveGuardDetail(command.getRunId(), fallbackGuard);
        if ("PASSED".equals(fallbackGuard.getStatus())) {
            FinalResponseVO persisted = persistenceService.persistDelivered(fallbackCommand,
                    finalResponseBuilder.build(fallbackCommand, fallbackCommand.getFinalAnswerCandidate(), null));
            return FinalDeliveryResultVO.builder()
                    .status(FinalDeliveryStatusEnumVO.DELIVERED)
                    .completed(true)
                    .finalResponse(persisted)
                    .guardResult(fallbackGuard)
                    .finalMessageId(persisted.getMessageId())
                    .finalAnswerRef(persisted.getContentRef())
                    .deliveredContent(persisted.getContent())
                    .message("Fixed safe fallback delivered.")
                    .build();
        }
        RuntimeSafeFailureVO safeFailure = failureFactory.create(RuntimeFailureCodeEnumVO.FINAL_INTERNAL_LEAK, null,
                "Final response guard failed and fallback did not pass guard: " + detail, false);
        persistenceService.persistFailure(command.getRunId(), failureCode, detail);
        return FinalDeliveryResultVO.builder()
                .status(FinalDeliveryStatusEnumVO.FAILED)
                .failed(true)
                .safeFailure(safeFailure)
                .guardResult(fallbackGuard)
                .failureCode(failureCode)
                .message(detail)
                .build();
    }

    private RagVerificationRouteResultVO verifyRagIfNeeded(FinalDeliveryCommandVO command) {
        boolean ragWasUsed = Boolean.TRUE.equals(command.getRagWasUsed()) || runRagWasUsed(command.getRunId());
        if (!ragWasUsed || ragVerificationRouter == null) {
            return null;
        }
        return ragVerificationRouter.verifyIfRequired(RagVerificationRouteCommandVO.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .loopIndex(command.getLoopIndex())
                .agentId(command.getAgentId())
                .userMessageId(command.getUserMessageId())
                .userInput(command.getUserInput())
                .ragWasUsed(true)
                .requiresKnowledgeBaseGrounding(true)
                .claimsKnowledgeBaseGrounding(true)
                .citations(command.getEvidenceIds())
                .finalAnswerCandidate(command.getFinalAnswerCandidate())
                .build());
    }

    private FinalDeliveryCommandVO normalize(FinalDeliveryCommandVO command) {
        if (command.getSourceAction() == MainAgentActionTypeEnumVO.FAIL) {
            return copyWithCandidate(command, FinalAnswerCandidateVO.builder()
                    .content("抱歉，这次任务没有被安全完成。请稍后重试，或调整问题后再试。")
                    .format("PLAIN_TEXT")
                    .build());
        }
        return command;
    }

    private FinalDeliveryCommandVO copyWithCandidate(FinalDeliveryCommandVO command, FinalAnswerCandidateVO candidate) {
        return FinalDeliveryCommandVO.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .userId(command.getUserId())
                .agentId(command.getAgentId())
                .userMessageId(command.getUserMessageId())
                .userInput(command.getUserInput())
                .loopIndex(command.getLoopIndex())
                .sourceAction(command.getSourceAction())
                .finalAnswerCandidate(candidate)
                .failure(command.getFailure())
                .userClarifications(command.getUserClarifications())
                .evidenceIds(command.getEvidenceIds())
                .verifiedToolCallRefs(command.getVerifiedToolCallRefs())
                .userFormatRequirement(command.getUserFormatRequirement())
                .maxOutputChars(command.getMaxOutputChars())
                .ragWasUsed(command.getRagWasUsed())
                .finalRepairCount(command.getFinalRepairCount())
                .maxFinalRepairAttempts(command.getMaxFinalRepairAttempts())
                .build();
    }

    private boolean repairBudgetRemains(FinalDeliveryCommandVO command) {
        int used = command.getFinalRepairCount() == null ? 0 : command.getFinalRepairCount();
        int max = command.getMaxFinalRepairAttempts() == null ? DEFAULT_MAX_FINAL_REPAIR_ATTEMPTS : command.getMaxFinalRepairAttempts();
        return used < max;
    }

    private boolean runRagWasUsed(String runId) {
        if (runRepository == null || runId == null) {
            return false;
        }
        return runRepository.findRun(runId).map(AgentRunEntity::getRagWasUsed).orElse(false);
    }
}

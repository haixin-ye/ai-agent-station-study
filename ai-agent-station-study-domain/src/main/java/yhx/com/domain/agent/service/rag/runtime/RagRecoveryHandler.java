package yhx.com.domain.agent.service.rag.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;

public class RagRecoveryHandler {

    private static final int MAX_RAG_RETRY = 1;
    private static final int MAX_FINAL_REPAIR = 1;

    private final RuntimeFailureFactory failureFactory;

    public RagRecoveryHandler() {
        this(new RuntimeFailureFactory());
    }

    public RagRecoveryHandler(RuntimeFailureFactory failureFactory) {
        this.failureFactory = failureFactory == null ? new RuntimeFailureFactory() : failureFactory;
    }

    public MainActionHandlerResult handleVerificationFailure(VerificationResultVO result, RuntimeExecutionContext context) {
        String failureCode = result == null ? "CONTRACT_INVALID" : result.getFailureCode();
        if ("RAG_NO_HIT".equals(failureCode) && context != null
                && context.countersOrInitial().ragRetryCountValue() < MAX_RAG_RETRY) {
            context.countersOrInitial().incrementRagRetry();
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                    .nextPhase(RuntimePhaseEnumVO.PREPARING_CONTEXT)
                    .message("Retry RAG with a rewritten query.")
                    .build();
        }
        if (canRepairFinal(context) && isFinalRepairFailure(failureCode)) {
            context.countersOrInitial().incrementFinalRepair();
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                    .nextPhase(RuntimePhaseEnumVO.REPAIRING_FINAL)
                    .message("Repair final answer using only verified evidence.")
                    .build();
        }
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .safeFailure(failureFactory.create(RuntimeFailureCodeEnumVO.RAG_VERIFICATION_FAILED,
                        RuntimePhaseEnumVO.VERIFYING_RAG,
                        result == null ? "RAG verification failed." : result.getDetail(),
                        false))
                .message("RAG verification could not be recovered.")
                .build();
    }

    private boolean canRepairFinal(RuntimeExecutionContext context) {
        return context != null && context.countersOrInitial().finalRepairCountValue() < MAX_FINAL_REPAIR;
    }

    private boolean isFinalRepairFailure(String failureCode) {
        return "RAG_NO_EVIDENCE".equals(failureCode)
                || "RAG_NO_HIT".equals(failureCode)
                || "RAG_UNGROUNDED".equals(failureCode)
                || "RAG_CONTRADICTION".equals(failureCode)
                || "FINAL_INVALID_CITATION".equals(failureCode)
                || "CONTRACT_INVALID".equals(failureCode);
    }
}

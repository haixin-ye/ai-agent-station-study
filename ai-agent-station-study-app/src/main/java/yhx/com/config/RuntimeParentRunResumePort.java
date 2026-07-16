package yhx.com.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeChildrenResumeCommand;
import yhx.com.domain.agent.service.agent.ParentRunResumePort;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RuntimeParentRunResumePort implements ParentRunResumePort {

    private static final int MAX_ATTEMPTS = 20;
    private static final long RETRY_DELAY_MILLIS = 50L;

    private final ObjectProvider<AutoAgentRuntimeService> runtimeServiceProvider;
    private final IRunRepository runRepository;
    private final Executor executor;

    public RuntimeParentRunResumePort(ObjectProvider<AutoAgentRuntimeService> runtimeServiceProvider,
                                      IRunRepository runRepository,
                                      Executor executor) {
        this.runtimeServiceProvider = runtimeServiceProvider;
        this.runRepository = runRepository;
        this.executor = Objects.requireNonNull(executor, "Parent Run resume Executor is required.");
    }

    @Override
    public boolean resumeParentIfReady(String parentRunId) {
        if (parentRunId == null || parentRunId.isBlank()) {
            return false;
        }
        try {
            executor.execute(() -> resumeWhenParentIsWaiting(parentRunId));
            return true;
        } catch (RejectedExecutionException error) {
            log.warn("Parent run resume scheduling was rejected; the waiting run remains retryable, runId={}",
                    parentRunId, error);
            return false;
        }
    }

    private void resumeWhenParentIsWaiting(String parentRunId) {
        try {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                AgentRunEntity run = runRepository == null ? null : runRepository.findRun(parentRunId).orElse(null);
                if (run != null && run.getStatus() == RunStatusEnumVO.WAITING_CHILDREN) {
                    AutoAgentRuntimeService runtimeService = runtimeServiceProvider.getIfAvailable();
                    if (runtimeService != null) {
                        runtimeService.resumeChildren(RuntimeChildrenResumeCommand.builder()
                                .runId(parentRunId)
                                .build());
                    }
                    return;
                }
                sleepBeforeRetry(attempt);
            }
            log.warn("Skip parent resume because run did not enter WAITING_CHILDREN, runId={}", parentRunId);
        } catch (Exception error) {
            log.warn("Failed to resume parent run after child agents completed, runId={}", parentRunId, error);
        }
    }

    private void sleepBeforeRetry(int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}

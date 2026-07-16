package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import yhx.com.config.RuntimeParentRunResumePort;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;

import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuntimeParentRunResumePortTest {

    @Test
    public void submits_parent_resume_to_the_configured_executor() {
        AutoAgentRuntimeService runtimeService = mock(AutoAgentRuntimeService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AutoAgentRuntimeService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(runtimeService);
        IRunRepository runRepository = waitingParentRepository("run-parent");
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        RuntimeParentRunResumePort port = new RuntimeParentRunResumePort(provider, runRepository, submitted::set);

        Assert.assertTrue(port.resumeParentIfReady("run-parent"));

        Assert.assertNotNull(submitted.get());
        submitted.get().run();
        verify(runtimeService).resumeChildren(any());
    }

    @Test
    public void executor_rejection_does_not_escape_or_change_parent_state() {
        @SuppressWarnings("unchecked")
        ObjectProvider<AutoAgentRuntimeService> provider = mock(ObjectProvider.class);
        IRunRepository runRepository = waitingParentRepository("run-parent");
        RuntimeParentRunResumePort port = new RuntimeParentRunResumePort(provider, runRepository, command -> {
            throw new RejectedExecutionException("saturated");
        });

        Assert.assertFalse(port.resumeParentIfReady("run-parent"));

        Assert.assertEquals(RunStatusEnumVO.WAITING_CHILDREN,
                runRepository.findRun("run-parent").orElseThrow().getStatus());
    }

    private IRunRepository waitingParentRepository(String runId) {
        IRunRepository repository = mock(IRunRepository.class);
        AgentRunEntity run = AgentRunEntity.builder()
                .runId(runId)
                .status(RunStatusEnumVO.WAITING_CHILDREN)
                .build();
        when(repository.findRun(runId)).thenReturn(Optional.of(run));
        return repository;
    }
}

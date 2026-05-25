package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.service.context.ContextCandidatePreselector;
import yhx.com.domain.agent.service.context.ContextPreparationService;
import yhx.com.domain.agent.service.memory.NoopVectorMemoryRepository;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ContextPreparationServiceTest {

    @Test
    public void prepare_runs_mysql_and_vector_candidate_preparers_in_parallel_then_merges() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        BlockingMysqlPreselector mysql = new BlockingMysqlPreselector(started, release, ContextCandidateBundleVO.builder()
                .sessionSummaries(List.of(SummaryCandidateVO.builder().summaryId("summary-mysql").summary("mysql summary").build()))
                .artifactCandidates(List.of(ArtifactCandidateVO.builder().artifactId("artifact-1").title("mysql artifact").build()))
                .memoryCandidates(List.of())
                .build());
        BlockingVectorPreselector vector = new BlockingVectorPreselector(started, release, ContextCandidateBundleVO.builder()
                .sessionSummaries(List.of(SummaryCandidateVO.builder().summaryId("summary-vector").summary("vector summary").build()))
                .artifactCandidates(List.of(ArtifactCandidateVO.builder().artifactId("artifact-1").sourceScore(0.9).build()))
                .memoryCandidates(List.of(MemoryCandidateVO.builder().memoryId("memory-vector").summary("vector memory").build()))
                .build());
        ExecutorService recallExecutor = Executors.newFixedThreadPool(2);
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
        try {
            ContextPreparationService service = new ContextPreparationService(mysql, vector, recallExecutor, Duration.ofSeconds(2));

            Future<ContextCandidateBundleVO> future = callerExecutor.submit(() -> service.prepare(ContextPreparationCommand.builder()
                    .userInput("mcp")
                    .build()));

            Assert.assertTrue("both recall branches should start before either is released", started.await(1, TimeUnit.SECONDS));
            release.countDown();
            ContextCandidateBundleVO result = future.get(2, TimeUnit.SECONDS);

            Assert.assertEquals(2, result.getSessionSummaries().size());
            Assert.assertEquals(1, result.getArtifactCandidates().size());
            Assert.assertEquals(0.9, result.getArtifactCandidates().get(0).getSourceScore(), 0.001);
            Assert.assertEquals(1, result.getMemoryCandidates().size());
        } finally {
            release.countDown();
            recallExecutor.shutdownNow();
            callerExecutor.shutdownNow();
        }
    }

    @Test
    public void vector_recall_failure_degrades_to_mysql_candidates() {
        ContextCandidateBundleVO mysqlBundle = ContextCandidateBundleVO.builder()
                .sessionSummaries(List.of(SummaryCandidateVO.builder().summaryId("summary-mysql").summary("mysql summary").build()))
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
                .build();
        ExecutorService recallExecutor = Executors.newFixedThreadPool(2);
        ContextPreparationService service = new ContextPreparationService(
                new StaticMysqlPreselector(mysqlBundle),
                new FailingVectorPreselector(),
                recallExecutor,
                Duration.ofMillis(100));

        try {
            ContextCandidateBundleVO result = service.prepare(ContextPreparationCommand.builder().userInput("mcp").build());

            Assert.assertEquals(1, result.getSessionSummaries().size());
            Assert.assertEquals("summary-mysql", result.getSessionSummaries().get(0).getSummaryId());
        } finally {
            recallExecutor.shutdownNow();
        }
    }

    private static class StaticMysqlPreselector extends ContextCandidatePreselector {

        private final ContextCandidateBundleVO bundle;

        private StaticMysqlPreselector(ContextCandidateBundleVO bundle) {
            super(null, null, null, null);
            this.bundle = bundle;
        }

        @Override
        public ContextCandidateBundleVO buildCandidates(ContextPreparationCommand command) {
            return bundle;
        }
    }

    private static class BlockingMysqlPreselector extends StaticMysqlPreselector {

        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingMysqlPreselector(CountDownLatch started, CountDownLatch release, ContextCandidateBundleVO bundle) {
            super(bundle);
            this.started = started;
            this.release = release;
        }

        @Override
        public ContextCandidateBundleVO buildCandidates(ContextPreparationCommand command) {
            started.countDown();
            await(release);
            return super.buildCandidates(command);
        }
    }

    private static class BlockingVectorPreselector extends VectorContextRecallPreselector {

        private final CountDownLatch started;
        private final CountDownLatch release;
        private final ContextCandidateBundleVO bundle;

        private BlockingVectorPreselector(CountDownLatch started, CountDownLatch release, ContextCandidateBundleVO bundle) {
            super(new NoopVectorMemoryRepository(), null, null, null, null);
            this.started = started;
            this.release = release;
            this.bundle = bundle;
        }

        @Override
        public ContextCandidateBundleVO recall(ContextPreparationCommand command) {
            started.countDown();
            await(release);
            return bundle;
        }
    }

    private static class FailingVectorPreselector extends VectorContextRecallPreselector {

        private FailingVectorPreselector() {
            super(new NoopVectorMemoryRepository(), null, null, null, null);
        }

        @Override
        public ContextCandidateBundleVO recall(ContextPreparationCommand command) {
            throw new IllegalStateException("vector unavailable");
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

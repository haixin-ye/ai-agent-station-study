package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.EvidenceCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.RagCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.service.context.ContextCandidatePreselector;
import yhx.com.domain.agent.service.context.ContextPreparationService;
import yhx.com.domain.agent.service.memory.NoopVectorMemoryRepository;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;
import yhx.com.domain.agent.service.rag.RagContextRecallPreselector;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ContextPreparationServiceTest {

    @Test
    public void prepare_runs_mysql_vector_and_rag_candidate_preparers_in_parallel_then_merges() throws Exception {
        CountDownLatch started = new CountDownLatch(3);
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
                .evidenceCandidates(List.of(EvidenceCandidateVO.builder().evidenceId("rag-evidence-vector").summary("rag vector evidence").build()))
                .build());
        BlockingRagPreselector rag = new BlockingRagPreselector(started, release, List.of(RagCandidateVO.builder()
                .candidateId("rag-candidate-1")
                .documentId("doc-1")
                .sourceType("RAG_FILE_CHUNK")
                .title("rag asset")
                .summary("rag summary")
                .build()));
        ExecutorService recallExecutor = Executors.newFixedThreadPool(3);
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
        try {
            ContextPreparationService service = new ContextPreparationService(
                    mysql, vector, rag, recallExecutor, Duration.ofSeconds(2), Duration.ofSeconds(2));

            Future<ContextCandidateBundleVO> future = callerExecutor.submit(() -> service.prepare(ContextPreparationCommand.builder()
                    .userInput("mcp")
                    .build()));

            Assert.assertTrue("all recall branches should start before any branch is released", started.await(1, TimeUnit.SECONDS));
            release.countDown();
            ContextCandidateBundleVO result = future.get(2, TimeUnit.SECONDS);

            Assert.assertEquals(2, result.getSessionSummaries().size());
            Assert.assertTrue(result.getArtifactCandidates().isEmpty());
            Assert.assertEquals(1, result.getMemoryCandidates().size());
            Assert.assertEquals(1, result.getEvidenceCandidates().size());
            Assert.assertEquals("rag-evidence-vector", result.getEvidenceCandidates().get(0).getEvidenceId());
            Assert.assertEquals(1, result.getRagCandidates().size());
            Assert.assertEquals("rag-candidate-1", result.getRagCandidates().get(0).getCandidateId());
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

    @Test
    public void rag_recall_failure_degrades_to_empty_rag_candidates() {
        ContextCandidateBundleVO mysqlBundle = ContextCandidateBundleVO.builder()
                .sessionSummaries(List.of(SummaryCandidateVO.builder().summaryId("summary-mysql").summary("mysql summary").build()))
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
                .build();
        ExecutorService recallExecutor = Executors.newFixedThreadPool(3);
        ContextPreparationService service = new ContextPreparationService(
                new StaticMysqlPreselector(mysqlBundle),
                new FailingVectorPreselector(),
                new FailingRagPreselector(),
                recallExecutor,
                Duration.ofMillis(100),
                Duration.ofMillis(100));

        try {
            ContextCandidateBundleVO result = service.prepare(ContextPreparationCommand.builder().userInput("rag").build());

            Assert.assertEquals(1, result.getSessionSummaries().size());
            Assert.assertNotNull(result.getRagCandidates());
            Assert.assertTrue(result.getRagCandidates().isEmpty());
        } finally {
            recallExecutor.shutdownNow();
        }
    }

    @Test
    public void prepare_can_skip_vector_and_rag_recall_for_runtime_refresh() {
        ContextCandidateBundleVO mysqlBundle = ContextCandidateBundleVO.builder()
                .sessionSummaries(List.of(SummaryCandidateVO.builder().summaryId("summary-mysql").summary("mysql summary").build()))
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
                .evidenceCandidates(List.of(EvidenceCandidateVO.builder().evidenceId("tool-evidence-1").summary("tool result").build()))
                .build();
        ExecutorService recallExecutor = Executors.newFixedThreadPool(3);
        ContextPreparationService service = new ContextPreparationService(
                new StaticMysqlPreselector(mysqlBundle),
                new FailingVectorPreselector(),
                new FailingRagPreselector(),
                recallExecutor,
                Duration.ofMillis(100),
                Duration.ofMillis(100));

        try {
            ContextCandidateBundleVO result = service.prepare(ContextPreparationCommand.builder()
                    .userInput("runtime refresh")
                    .vectorRecallEnabled(false)
                    .ragRecallEnabled(false)
                    .build());

            Assert.assertEquals(1, result.getSessionSummaries().size());
            Assert.assertEquals(1, result.getEvidenceCandidates().size());
            Assert.assertEquals("tool-evidence-1", result.getEvidenceCandidates().get(0).getEvidenceId());
            Assert.assertNotNull(result.getRagCandidates());
            Assert.assertTrue(result.getRagCandidates().isEmpty());
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

    private static class BlockingRagPreselector extends RagContextRecallPreselector {

        private final CountDownLatch started;
        private final CountDownLatch release;
        private final List<RagCandidateVO> candidates;

        private BlockingRagPreselector(CountDownLatch started, CountDownLatch release, List<RagCandidateVO> candidates) {
            super(null, null, null, null);
            this.started = started;
            this.release = release;
            this.candidates = candidates;
        }

        @Override
        public List<RagCandidateVO> recall(ContextPreparationCommand command) {
            started.countDown();
            await(release);
            return candidates;
        }
    }

    private static class FailingRagPreselector extends RagContextRecallPreselector {

        private FailingRagPreselector() {
            super(null, null, null, null);
        }

        @Override
        public List<RagCandidateVO> recall(ContextPreparationCommand command) {
            throw new IllegalStateException("rag unavailable");
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

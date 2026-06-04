package yhx.com.test.domain.rag;

import yhx.com.domain.agent.adapter.repository.IRagRepository;
import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagFilePayloadEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitIngestCommandEntity;
import yhx.com.domain.agent.service.rag.IRagDomainService;
import yhx.com.domain.agent.service.rag.RagService;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class RagServiceTest {

    @Test
    public void test_queryRagTagList() {
        FakeRagRepository fakeRagRepository = new FakeRagRepository();
        fakeRagRepository.tags = Set.of("java", "spring");
        IRagDomainService ragService = new RagService(fakeRagRepository);

        Set<String> result = ragService.queryRagTagList();

        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("java"));
    }

    @Test
    public void test_ingestFiles() {
        FakeRagRepository fakeRagRepository = new FakeRagRepository();
        IRagDomainService ragService = new RagService(fakeRagRepository);

        RagFileIngestCommandEntity commandEntity = RagFileIngestCommandEntity.builder()
                .knowledgeTag("agent-docs")
                .files(List.of(RagFilePayloadEntity.builder()
                        .fileName("readme.txt")
                        .content("hello rag".getBytes(StandardCharsets.UTF_8))
                        .build()))
                .build();

        ragService.ingestFiles(commandEntity);

        Assert.assertNotNull(fakeRagRepository.lastFileCommand);
        Assert.assertEquals("agent-docs", fakeRagRepository.lastFileCommand.getKnowledgeTag());
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_ingestFiles_emptyFiles() {
        IRagDomainService ragService = new RagService(new FakeRagRepository());

        ragService.ingestFiles(RagFileIngestCommandEntity.builder()
                .files(List.of())
                .build());
    }

    @Test
    public void test_ingestFiles_blankTag_usesGlobalDefault() {
        FakeRagRepository fakeRagRepository = new FakeRagRepository();
        IRagDomainService ragService = new RagService(fakeRagRepository);

        ragService.ingestFiles(RagFileIngestCommandEntity.builder()
                .knowledgeTag(" ")
                .files(List.of(RagFilePayloadEntity.builder()
                        .fileName("readme.txt")
                        .content("hello rag".getBytes(StandardCharsets.UTF_8))
                        .build()))
                .build());

        Assert.assertEquals(RagService.DEFAULT_KNOWLEDGE_TAG, fakeRagRepository.lastFileCommand.getKnowledgeTag());
    }

    @Test
    public void test_analyzeGitRepository() throws Exception {
        FakeRagRepository fakeRagRepository = new FakeRagRepository();
        IRagDomainService ragService = new RagService(fakeRagRepository);

        RagGitIngestCommandEntity commandEntity = RagGitIngestCommandEntity.builder()
                .repoUrl("https://github.com/example/demo.git")
                .userName("u")
                .token("t")
                .build();

        ragService.analyzeGitRepository(commandEntity);

        Assert.assertNotNull(fakeRagRepository.lastGitCommand);
        Assert.assertEquals("https://github.com/example/demo.git", fakeRagRepository.lastGitCommand.getRepoUrl());
    }

    private static class FakeRagRepository implements IRagRepository {
        private Set<String> tags = Set.of();
        private RagFileIngestCommandEntity lastFileCommand;
        private RagGitIngestCommandEntity lastGitCommand;

        @Override
        public Set<String> queryRagTagList() {
            return tags;
        }

        @Override
        public void ingestFiles(RagFileIngestCommandEntity commandEntity) {
            this.lastFileCommand = commandEntity;
        }

        @Override
        public void ingestGitRepository(RagGitIngestCommandEntity commandEntity) {
            this.lastGitCommand = commandEntity;
        }
    }

}


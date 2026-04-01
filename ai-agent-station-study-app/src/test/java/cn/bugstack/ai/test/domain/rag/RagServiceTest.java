package cn.bugstack.ai.test.domain.rag;

import cn.bugstack.ai.domain.agent.adapter.repository.IRagRepository;
import cn.bugstack.ai.domain.agent.model.entity.RagFileIngestCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.RagFilePayloadEntity;
import cn.bugstack.ai.domain.agent.model.entity.RagGitIngestCommandEntity;
import cn.bugstack.ai.domain.agent.service.rag.IRagService;
import cn.bugstack.ai.domain.agent.service.rag.RagService;
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
        IRagService ragService = new RagService(fakeRagRepository);

        Set<String> result = ragService.queryRagTagList();

        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("java"));
    }

    @Test
    public void test_ingestFiles() {
        FakeRagRepository fakeRagRepository = new FakeRagRepository();
        IRagService ragService = new RagService(fakeRagRepository);

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
    public void test_ingestFiles_emptyTag() {
        IRagService ragService = new RagService(new FakeRagRepository());

        ragService.ingestFiles(RagFileIngestCommandEntity.builder()
                .knowledgeTag(" ")
                .files(List.of())
                .build());
    }

    @Test
    public void test_analyzeGitRepository() throws Exception {
        FakeRagRepository fakeRagRepository = new FakeRagRepository();
        IRagService ragService = new RagService(fakeRagRepository);

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

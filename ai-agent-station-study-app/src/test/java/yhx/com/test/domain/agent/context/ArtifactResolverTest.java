package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.service.artifact.ArtifactResolver;
import yhx.com.test.domain.agent.context.support.FakeContextRepositories;

import java.util.List;

public class ArtifactResolverTest {

    @Test
    public void resolves_exact_artifact_id() {
        FakeContextRepositories repos = new FakeContextRepositories();
        repos.artifacts.put("artifact-1", AgentArtifactEntity.builder().artifactId("artifact-1").build());

        Assert.assertTrue(new ArtifactResolver(repos).resolveById("artifact-1").isPresent());
    }

    @Test
    public void resolves_by_highest_candidate_score() {
        ArtifactResolver resolver = new ArtifactResolver(new FakeContextRepositories());

        ArtifactCandidateVO result = resolver.resolveCandidate("that article", List.of(
                ArtifactCandidateVO.builder().artifactId("artifact-1").totalScore(1.0).build(),
                ArtifactCandidateVO.builder().artifactId("artifact-2").totalScore(5.0).build())).orElseThrow();

        Assert.assertEquals("artifact-2", result.getArtifactId());
    }

    @Test
    public void ambiguous_alias_returns_multiple_candidates() {
        ArtifactResolver resolver = new ArtifactResolver(new FakeContextRepositories());

        Assert.assertEquals(2, resolver.ambiguousCandidates(List.of(
                ArtifactCandidateVO.builder().artifactId("artifact-1").totalScore(5.0).build(),
                ArtifactCandidateVO.builder().artifactId("artifact-2").totalScore(4.5).build())).size());
    }
}

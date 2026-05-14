package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolArgumentContentModeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.ToolArgumentsMaterializationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolIntentVO;
import yhx.com.domain.agent.service.tool.ToolArgumentMaterializer;

import java.math.BigDecimal;
import java.util.Map;

public class ToolArgumentMaterializerTest {

    @Test
    public void artifact_full_text_required_loads_payload() {
        ToolTestSupport.Repository repository = repositoryWithArtifact();
        ToolArgumentsMaterializationResultVO result = materializer(repository).materialize(intent(Map.of(
                "contentSource", Map.of("type", "ARTIFACT", "artifactId", "artifact-1", "contentMode", "FULL_TEXT_REQUIRED")
        )), capability(ToolArgumentContentModeEnumVO.SUMMARY_ONLY));

        Assert.assertNull(result.getFailureCode());
        Assert.assertEquals("article body", result.getArguments().get("content"));
    }

    @Test
    public void artifact_metadata_only_does_not_load_body() {
        ToolTestSupport.Repository repository = repositoryWithArtifact();
        ToolArgumentsMaterializationResultVO result = materializer(repository).materialize(intent(Map.of(
                "contentSource", Map.of("type", "ARTIFACT", "artifactId", "artifact-1", "contentMode", "METADATA_ONLY")
        )), capability(ToolArgumentContentModeEnumVO.FULL_TEXT_REQUIRED));

        Assert.assertNull(result.getFailureCode());
        Assert.assertTrue(result.getArguments().get("content") instanceof Map);
    }

    @Test
    public void evidence_summary_only_loads_summary() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        repository.saveEvidence(AgentEvidenceEntity.builder()
                .evidenceId("evidence-1")
                .runId("run-001")
                .summary("tool returned url")
                .confidence(BigDecimal.ONE)
                .build());

        ToolArgumentsMaterializationResultVO result = materializer(repository).materialize(intent(Map.of(
                "evidenceSource", Map.of("type", "EVIDENCE", "evidenceId", "evidence-1")
        )), capability(ToolArgumentContentModeEnumVO.SUMMARY_ONLY));

        Assert.assertNull(result.getFailureCode());
        Assert.assertEquals("tool returned url", result.getArguments().get("evidence"));
    }

    @Test
    public void missing_required_artifact_fails() {
        ToolArgumentsMaterializationResultVO result = materializer(new ToolTestSupport.Repository()).materialize(intent(Map.of(
                "contentSource", Map.of("type", "ARTIFACT", "artifactId", "missing", "contentMode", "FULL_TEXT_REQUIRED")
        )), capability(ToolArgumentContentModeEnumVO.FULL_TEXT_REQUIRED));

        Assert.assertEquals("TOOL_ARGUMENT_ARTIFACT_MISSING", result.getFailureCode());
    }

    private ToolArgumentMaterializer materializer(ToolTestSupport.Repository repository) {
        return new ToolArgumentMaterializer(repository, repository, repository);
    }

    private ToolIntentVO intent(Map<String, Object> arguments) {
        return ToolIntentVO.builder().capabilityCode("cap").arguments(arguments).build();
    }

    private CapabilitySpecVO capability(ToolArgumentContentModeEnumVO contentMode) {
        return CapabilitySpecVO.builder().defaultContentMode(contentMode).build();
    }

    private ToolTestSupport.Repository repositoryWithArtifact() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        repository.savePayload(AgentPayloadEntity.builder()
                .payloadId("payload-1")
                .payloadType(PayloadTypeEnumVO.ARTIFACT_CONTENT)
                .content("article body")
                .build());
        repository.saveArtifact(AgentArtifactEntity.builder()
                .artifactId("artifact-1")
                .title("Article")
                .summary("summary")
                .contentRef("payload-1")
                .build());
        return repository;
    }
}

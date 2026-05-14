package yhx.com.domain.agent.adapter.repository;

public interface IRunTranscriptRepository {

    void appendTranscriptBlock(String runId, String blockType, String payloadRef);
}

package yhx.com.domain.agent.service.artifact;

public class ArtifactVersionService {

    public int nextVersion(Integer currentVersion) {
        return currentVersion == null ? 1 : currentVersion + 1;
    }
}

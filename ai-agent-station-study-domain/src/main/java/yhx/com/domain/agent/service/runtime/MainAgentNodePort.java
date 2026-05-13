package yhx.com.domain.agent.service.runtime;

@FunctionalInterface
public interface MainAgentNodePort {

    String call(String stateViewJson);
}

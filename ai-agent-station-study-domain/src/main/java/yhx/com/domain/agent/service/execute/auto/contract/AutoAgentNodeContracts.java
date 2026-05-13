package yhx.com.domain.agent.service.execute.auto.contract;

import java.util.List;

/**
 * Registry of the current node contracts.
 */
public final class AutoAgentNodeContracts {

    public static final AutoAgentNodeContract STEP1 = new BasicContract(
            "node1",
            "v1",
            List.of("sessionGoal", "masterPlan", "taskBoard", "roundArchive", "nextRoundDirective", "overallStatus")
    );

    public static final AutoAgentNodeContract STEP2 = new BasicContract(
            "node2",
            "v1",
            List.of("currentRound", "currentStepPlan", "toolPolicy", "sourceContent")
    );

    public static final AutoAgentNodeContract STEP3 = new BasicContract(
            "node3",
            "v1",
            List.of("currentRound", "taskBoard", "acceptedResults", "overallStatus", "roundArchive", "roundExecutionSummary")
    );

    public static final AutoAgentNodeContract STEP4 = new BasicContract(
            "node4",
            "v1",
            List.of("acceptedResults", "taskBoard", "roundArchive", "overallStatus", "nextRoundDirective")
    );

    private AutoAgentNodeContracts() {
    }

    private record BasicContract(String nodeId, String contractVersion, List<String> primaryTruthSources)
            implements AutoAgentNodeContract {
    }
}

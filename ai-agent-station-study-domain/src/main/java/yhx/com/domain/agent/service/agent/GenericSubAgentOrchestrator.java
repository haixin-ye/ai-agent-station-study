package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentOrchestrationResultVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentContinuationVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentRunCommandVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentRunResultVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

public class GenericSubAgentOrchestrator {

    private final ParentChildRunRegistry registry;
    private final GenericSubAgentRuntime runtime;
    private final ChildAgentResultProjector projector;

    public GenericSubAgentOrchestrator(ParentChildRunRegistry registry,
                                       GenericSubAgentRuntime runtime,
                                       ChildAgentResultProjector projector) {
        this.registry = registry == null ? new ParentChildRunRegistry() : registry;
        if (runtime == null) {
            throw new IllegalArgumentException("GenericSubAgentRuntime is required.");
        }
        if (projector == null) {
            throw new IllegalArgumentException("ChildAgentResultProjector is required.");
        }
        this.runtime = runtime;
        this.projector = projector;
    }

    public GenericSubAgentOrchestrationResultVO runAndProject(RuntimeExecutionContext parentContext,
                                                             GenericSubAgentRunCommandVO command) {
        if (parentContext == null) {
            throw new IllegalArgumentException("Parent runtime context is required.");
        }
        if (command == null || command.getRelation() == null) {
            throw new IllegalArgumentException("Generic subagent run command and relation are required.");
        }
        GenericSubAgentRunResultVO childResult = runtime.run(command);
        ParentChildRunRelationVO relation = registry.findByChildRunId(command.getRelation().getChildRunId())
                .orElseThrow(() -> new IllegalStateException("Child relation is missing after subagent run."));
        if (relation.getStatus() != null && relation.getStatus().terminal()) {
            projector.project(parentContext, relation);
        }
        return GenericSubAgentOrchestrationResultVO.builder()
                .parentRunId(relation.getParentRunId())
                .childRunId(relation.getChildRunId())
                .taskId(relation.getTaskId())
                .childStatus(relation.getStatus())
                .parentReady(registry.isWaitSatisfied(relation.getParentRunId()))
                .childRunResult(childResult)
                .build();
    }

    public GenericSubAgentOrchestrationResultVO resumeAndProject(RuntimeExecutionContext parentContext,
                                                                String childRunId,
                                                                UserAnswerVO answer) {
        if (parentContext == null) {
            throw new IllegalArgumentException("Parent runtime context is required.");
        }
        if (childRunId == null || childRunId.isBlank()) {
            throw new IllegalArgumentException("Child run id is required.");
        }
        GenericSubAgentContinuationVO continuation = registry.findContinuation(childRunId)
                .orElseThrow(() -> new IllegalArgumentException("Generic subagent continuation is missing for child run: " + childRunId));
        GenericSubAgentRunResultVO childResult = runtime.resume(continuation, answer);
        ParentChildRunRelationVO relation = registry.findByChildRunId(childRunId)
                .orElseThrow(() -> new IllegalStateException("Child relation is missing after subagent resume."));
        if (relation.getStatus() != null && relation.getStatus().terminal()) {
            projector.project(parentContext, relation);
        }
        return GenericSubAgentOrchestrationResultVO.builder()
                .parentRunId(relation.getParentRunId())
                .childRunId(relation.getChildRunId())
                .taskId(relation.getTaskId())
                .childStatus(relation.getStatus())
                .parentReady(registry.isWaitSatisfied(relation.getParentRunId()))
                .childRunResult(childResult)
                .build();
    }
}

package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.AgentCapabilityResolutionCommandVO;
import yhx.com.domain.agent.model.valobj.agent.AgentCapabilityResolutionResultVO;
import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentsRequestVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentDelegationWaitModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentCapabilityCodeEnumVO;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DelegateAgentsRequestValidator {

    private final AgentCapabilityResolver capabilityResolver;

    public DelegateAgentsRequestValidator() {
        this(new AgentCapabilityResolver());
    }

    public DelegateAgentsRequestValidator(AgentCapabilityResolver capabilityResolver) {
        this.capabilityResolver = capabilityResolver == null ? new AgentCapabilityResolver() : capabilityResolver;
    }

    public void validate(DelegateAgentsRequestVO request, AgentProfileVO childProfile) {
        if (request == null) {
            throw new IllegalArgumentException("DelegateAgentsRequest is required.");
        }
        if (AgentDelegationWaitModeEnumVO.ofCode(request.getWaitMode()).isEmpty()) {
            throw new IllegalArgumentException("Only WAIT_ALL is supported for DELEGATE_AGENTS.");
        }
        List<DelegateAgentTaskVO> tasks = request.getTasks();
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("DELEGATE_AGENTS requires at least one child task.");
        }
        Set<String> taskIds = new LinkedHashSet<>();
        for (DelegateAgentTaskVO task : tasks) {
            validateTask(task, childProfile, taskIds);
        }
    }

    private void validateTask(DelegateAgentTaskVO task, AgentProfileVO childProfile, Set<String> taskIds) {
        if (task == null) {
            throw new IllegalArgumentException("Delegated task must not be null.");
        }
        if (isBlank(task.getTaskId())) {
            throw new IllegalArgumentException("Delegated task requires taskId.");
        }
        if (!taskIds.add(task.getTaskId())) {
            throw new IllegalArgumentException("Delegated taskId must be unique: " + task.getTaskId() + ".");
        }
        if (isBlank(task.getName())) {
            throw new IllegalArgumentException("Delegated task requires name.");
        }
        if (isBlank(task.getObjective())) {
            throw new IllegalArgumentException("Delegated task requires objective.");
        }
        if (isBlank(task.getRequiredOutput())) {
            throw new IllegalArgumentException("Delegated task requires requiredOutput.");
        }
        Set<String> requestedCapabilities = task.getRequestedCapabilities() == null
                ? Set.of()
                : new LinkedHashSet<>(task.getRequestedCapabilities());
        if (!requestedCapabilities.contains(AgentCapabilityCodeEnumVO.COMMIT.code())) {
            throw new IllegalArgumentException("Delegated task requestedCapabilities must include COMMIT: "
                    + task.getTaskId() + ".");
        }
        AgentCapabilityResolutionResultVO resolution = capabilityResolver.resolve(AgentCapabilityResolutionCommandVO.builder()
                .profile(childProfile)
                .requestedCapabilityCodes(requestedCapabilities)
                .workspaceScopePresent(workspaceScopePresent(task))
                .build());
        if (resolution.getDeniedCapabilityCodes() != null && !resolution.getDeniedCapabilityCodes().isEmpty()) {
            throw new IllegalArgumentException("Delegated task requested capabilities outside its boundary: "
                    + resolution.getDeniedCapabilityCodes() + ".");
        }
    }

    private boolean workspaceScopePresent(DelegateAgentTaskVO task) {
        if (task == null || task.getParentContext() == null || task.getParentContext().isEmpty()) {
            return false;
        }
        return task.getParentContext().containsKey("workspace")
                || task.getParentContext().containsKey("currentWorkspace")
                || task.getParentContext().containsKey("workspaceScope");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

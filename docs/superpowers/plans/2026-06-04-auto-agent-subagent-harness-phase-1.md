# AutoAgent Subagent Harness Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first safe Agent Harness foundation slice without exposing subagent delegation to MainAgent yet.

**Architecture:** This phase adds lifecycle and policy foundations only: `WAITING_CHILDREN`, agent profile identity/action policy, and workspace-aware capability resolution hooks. MainAgent behavior remains equivalent because no new delegation action is exposed to prompt, active contract, or runtime routing in this phase.

**Tech Stack:** Java 17, Maven, JUnit 4, Spring Boot app test module for focused domain tests.

---

## File Structure

- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/runtime/RunStatusEnumVO.java`
  - Add `WAITING_CHILDREN`.
- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/runtime/RuntimePhaseEnumVO.java`
  - Add `WAITING_CHILDREN`.
- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/RuntimeStateMachine.java`
  - Treat `WAITING_CHILDREN` as paused.
  - Allow parent to enter `WAITING_CHILDREN` from action handling.
  - Allow child completion wakeup from `WAITING_CHILDREN` back to context/state-view preparation.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/agent/AgentProfileTypeEnumVO.java`
  - Define `MAIN_AGENT`, `GENERIC_SUB_AGENT`, `CODE_AGENT_BRIDGE`.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/agent/AgentCapabilityCodeEnumVO.java`
  - Define coarse capabilities from the spec.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent/AgentProfileVO.java`
  - Hold profile type, allowed action codes, maximum capability codes, and safety limits.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/agent/AgentProfileRegistry.java`
  - Provide default profiles.
  - Keep `DELEGATE_CODE_AGENT` out of MainAgent active actions until CodeAgent runtime exists.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent/AgentCapabilityResolutionCommandVO.java`
  - Input for requested capabilities and workspace presence.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent/AgentCapabilityResolutionResultVO.java`
  - Output effective and denied capabilities.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/agent/AgentCapabilityResolver.java`
  - Intersect requested capabilities with profile maximums.
  - Remove file read/write capabilities when no workspace scope exists.
- Test `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeStateMachineTest.java`
  - Cover `WAITING_CHILDREN`.
- Create test `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/harness/AgentProfileRegistryTest.java`
  - Cover default profiles and reserved CodeAgent action.
- Create test `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/harness/AgentCapabilityResolverTest.java`
  - Cover workspace-aware file capability filtering.

## Task 1: Add Child Waiting Lifecycle

- [ ] **Step 1: Write failing lifecycle tests**

Add tests to `RuntimeStateMachineTest`:

```java
@Test
public void waiting_children_is_paused_but_not_terminal() {
    RuntimeStateMachine stateMachine = new RuntimeStateMachine();

    Assert.assertTrue(stateMachine.isPausedRunStatus(RunStatusEnumVO.WAITING_CHILDREN));
    Assert.assertFalse(stateMachine.isTerminalRunStatus(RunStatusEnumVO.WAITING_CHILDREN));
}

@Test
public void handling_action_can_pause_for_children_and_resume_after_child_wait() {
    RuntimeStateMachine stateMachine = new RuntimeStateMachine();

    Assert.assertTrue(stateMachine.canEnter(RuntimePhaseEnumVO.HANDLING_ACTION, RuntimePhaseEnumVO.WAITING_CHILDREN));
    Assert.assertTrue(stateMachine.canEnter(RuntimePhaseEnumVO.WAITING_CHILDREN, RuntimePhaseEnumVO.BUILDING_STATE_VIEW));
    Assert.assertFalse(stateMachine.canEnter(RuntimePhaseEnumVO.WAITING_CHILDREN, RuntimePhaseEnumVO.RESOLVING_USER_ANSWER));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RuntimeStateMachineTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile/test failure because `WAITING_CHILDREN` does not exist.

- [ ] **Step 3: Implement lifecycle enum and state machine changes**

Add `WAITING_CHILDREN` to run status and runtime phase. Add transitions:

```text
HANDLING_ACTION -> WAITING_CHILDREN
WAITING_CHILDREN -> BUILDING_STATE_VIEW
WAITING_CHILDREN -> PREPARING_CONTEXT
```

Update `isPausedRunStatus` so both `WAITING_USER` and `WAITING_CHILDREN` are paused.

- [ ] **Step 4: Run lifecycle test to verify it passes**

Run the same Maven command. Expected: PASS.

## Task 2: Add Agent Profile Defaults

- [ ] **Step 1: Write failing profile registry test**

Create `AgentProfileRegistryTest` with assertions:

```java
@Test
public void main_agent_profile_keeps_existing_actions_but_does_not_expose_code_agent() {
    AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
            .requireProfile(AgentProfileTypeEnumVO.MAIN_AGENT);

    Assert.assertTrue(profile.allowsAction("FINAL"));
    Assert.assertTrue(profile.allowsAction("CALL_TOOL"));
    Assert.assertTrue(profile.allowsAction("ASK_USER"));
    Assert.assertTrue(profile.allowsAction("DELEGATE_AGENTS"));
    Assert.assertFalse(profile.allowsAction("DELEGATE_CODE_AGENT"));
}

@Test
public void generic_sub_agent_profile_commits_but_cannot_final_or_delegate() {
    AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
            .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

    Assert.assertTrue(profile.allowsAction("COMMIT"));
    Assert.assertTrue(profile.allowsAction("ASK_USER"));
    Assert.assertFalse(profile.allowsAction("FINAL"));
    Assert.assertFalse(profile.allowsAction("DELEGATE_AGENTS"));
}

@Test
public void generic_sub_agent_limits_are_broad_and_explicit() {
    AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
            .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

    Assert.assertEquals(Integer.valueOf(25), profile.getMaxLoopCount());
    Assert.assertEquals(Integer.valueOf(200000), profile.getMaxContextChars());
    Assert.assertEquals(Integer.valueOf(200000), profile.getMaxSingleToolResultChars());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentProfileRegistryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile failure because profile classes do not exist.

- [ ] **Step 3: Implement profile enum, capability enum, `AgentProfileVO`, and registry**

Create the files listed in File Structure. Keep them under `domain/agent/model/valobj/**` and `domain/agent/service/agent/**`.

- [ ] **Step 4: Run profile test to verify it passes**

Run the same Maven command. Expected: PASS.

## Task 3: Add Workspace-Aware Capability Resolver

- [ ] **Step 1: Write failing resolver test**

Create `AgentCapabilityResolverTest`:

```java
@Test
public void generic_sub_agent_drops_file_capabilities_without_workspace_scope() {
    AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
            .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

    AgentCapabilityResolutionResultVO result = new AgentCapabilityResolver().resolve(
            AgentCapabilityResolutionCommandVO.builder()
                    .profile(profile)
                    .requestedCapabilityCodes(Set.of("RAG", "FILE_READ", "FILE_WRITE"))
                    .workspaceScopePresent(false)
                    .build());

    Assert.assertTrue(result.getEffectiveCapabilityCodes().contains("RAG"));
    Assert.assertFalse(result.getEffectiveCapabilityCodes().contains("FILE_READ"));
    Assert.assertFalse(result.getEffectiveCapabilityCodes().contains("FILE_WRITE"));
    Assert.assertTrue(result.getDeniedCapabilityCodes().contains("FILE_READ"));
    Assert.assertTrue(result.getDeniedCapabilityCodes().contains("FILE_WRITE"));
}

@Test
public void generic_sub_agent_keeps_file_capabilities_with_workspace_scope() {
    AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
            .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

    AgentCapabilityResolutionResultVO result = new AgentCapabilityResolver().resolve(
            AgentCapabilityResolutionCommandVO.builder()
                    .profile(profile)
                    .requestedCapabilityCodes(Set.of("FILE_READ"))
                    .workspaceScopePresent(true)
                    .build());

    Assert.assertTrue(result.getEffectiveCapabilityCodes().contains("FILE_READ"));
}
```

- [ ] **Step 2: Run resolver test to verify it fails**

Run:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentCapabilityResolverTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile failure because resolver classes do not exist.

- [ ] **Step 3: Implement resolver command/result and resolver**

Keep resolver deterministic. It must not call tools, databases, prompts, or LLMs.

- [ ] **Step 4: Run resolver test to verify it passes**

Run the same Maven command. Expected: PASS.

## Task 4: Focused Verification And Commit

- [ ] **Step 1: Run focused tests**

Run:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RuntimeStateMachineTest,AgentProfileRegistryTest,AgentCapabilityResolverTest,MainAgentActionContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 2: Commit**

Stage only the plan and Phase 1 code/test files:

```bash
git add docs/superpowers/plans/2026-06-04-auto-agent-subagent-harness-phase-1.md \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/runtime/RunStatusEnumVO.java \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/runtime/RuntimePhaseEnumVO.java \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/RuntimeStateMachine.java \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/agent \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/agent \
  ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeStateMachineTest.java \
  ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/harness
git commit -m "agent: add subagent harness foundation"
```

Do not stage `.idea/vcs.xml` or `rooftop_basil_plan.txt`.


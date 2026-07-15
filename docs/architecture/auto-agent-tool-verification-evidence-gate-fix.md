# AutoAgent Tool Verification Evidence Gate Fix

## 1. Problem

The current tool orchestration order is:

```text
Tool Runtime invocation
  -> ToolVerifier.verify(...)
  -> ToolEvidenceConverter.createInvocationEvidencePack(...)
```

`ToolVerifier` persists a verification result, but the verification result does not currently control whether the invocation result is converted into successful Evidence.

`ToolActionOrchestrator` creates Evidence mainly from `ToolInvocationResultVO` and derives `ToolActionEffectStatus` from the invocation status. Therefore an inconsistent state can occur:

```text
ToolInvocationResult.status = SUCCESS
but
ToolVerification.status = FAILED
```

For example, the ToolCall may be marked successful while its `receiptRef` is missing. `ToolVerifier` correctly reports `TOOL_RECEIPT_MISSING`, but the current orchestrator may still create Evidence that looks like successful tool output.

This weakens the intended trust chain:

```text
real MCP invocation
  -> receipt captured
  -> execution proof verified
  -> trusted Evidence
```

## 2. Required Behavior

Verification must become a mandatory Evidence gate.

```text
Verification PASSED
  -> create normal invocation Evidence
  -> ToolActionEffectStatus = TOOL_SUCCEEDED when invocation succeeded

Verification FAILED or missing
  -> do not create successful Evidence from invocation content
  -> create explicit verification-failure Evidence
  -> ToolActionEffectStatus = TOOL_FAILED
  -> continue the Runtime loop with a structured failure result
```

The original receipt may remain persisted for audit and diagnosis, but unverified receipt content must not be presented to MainAgent as trusted successful evidence.

## 3. Implementation Plan

### 3.1 Gate Evidence creation in ToolActionOrchestrator

After:

```java
VerificationResultVO verification = toolVerifier.verify(request, invocationResult);
```

check that verification is non-null and has `PASSED` status before calling the existing successful Evidence conversion path.

Suggested control flow:

```java
if (!verificationPassed(verification)) {
    ToolEvidenceCreationResultVO failureEvidence =
        evidenceConverter.createVerificationFailureEvidencePack(
            runId,
            invocationResult,
            verification
        );

    return ToolActionResultVO.builder()
        .status(ToolActionStatusEnumVO.CONTINUE_LOOP)
        .actionEffectStatus(ToolActionEffectStatusEnumVO.TOOL_FAILED)
        .evidenceIds(failureEvidence.getEvidenceIds())
        .evidence(failureEvidence.getEvidence())
        .message(verificationFailureMessage(verification))
        .build();
}
```

Only the passed branch may call `createInvocationEvidencePack(...)` as trusted invocation evidence.

### 3.2 Add verification-failure Evidence conversion

Add a focused converter method, for example:

```java
createVerificationFailureEvidencePack(
    String runId,
    ToolInvocationResultVO invocationResult,
    VerificationResultVO verification
)
```

The Evidence should contain:

```text
evidenceType = TOOL
sourceRef = toolCallId
summary = tool execution could not be verified
failureCode = verification failure code, when representable
content = bounded diagnostic detail only
```

Do not copy unverified tool result content into the trusted Evidence content field.

The raw receipt remains available through the persisted ToolCall/receiptRef audit chain.

### 3.3 Make ActionEffect status depend on verification

Update the status calculation so that:

```text
invocation SUCCESS + verification PASSED
  -> TOOL_SUCCEEDED

invocation SUCCESS + verification FAILED
  -> TOOL_FAILED

invocation FAILED
  -> TOOL_FAILED
```

Do not derive success from `ToolInvocationResultVO.status` alone.

### 3.4 Preserve Runtime recovery behavior

A verification failure should normally return `CONTINUE_LOOP` with failure Evidence so MainAgent can:

- retry when appropriate;
- choose another tool;
- ask the user for missing information;
- explain that the operation could not be verified.

Do not report successful completion or allow a final answer to claim a successful side effect based on unverified Evidence.

## 4. Required Tests

### 4.1 Normal verified success

Given:

```text
ToolInvocationResult = SUCCESS
ToolCall.status = SUCCEEDED
receiptRef exists
required approval is APPROVED
```

Expect:

```text
verification PASSED
normal tool Evidence created
actionEffectStatus = TOOL_SUCCEEDED
```

### 4.2 Missing receipt

Given:

```text
ToolInvocationResult = SUCCESS
ToolCall.status = SUCCEEDED
receiptRef is missing
```

Expect:

```text
verification FAILED with TOOL_RECEIPT_MISSING
no successful invocation Evidence
verification-failure Evidence created
actionEffectStatus = TOOL_FAILED
```

### 4.3 Missing approval

Given an approval-required request without an `APPROVED` ToolApproval:

```text
verification FAILED with TOOL_APPROVAL_REQUIRED
no successful Evidence
actionEffectStatus = TOOL_FAILED
```

### 4.4 ToolCall missing or not called

Cover:

```text
ToolCall missing
toolInvocationId missing
ToolCall.status = NOT_CALLED
```

Each case must create failure Evidence and never trusted success Evidence.

### 4.5 Invocation failure

An MCP timeout or tool error must preserve the existing failure Evidence behavior and remain `TOOL_FAILED`.

## 5. Acceptance Criteria

The fix is complete when:

1. `ToolVerification` is a mandatory gate before successful Evidence creation.
2. Verification failure cannot produce successful Tool Evidence.
3. Verification failure forces `ToolActionEffectStatus=TOOL_FAILED`.
4. Raw receipts remain persisted for audit but are not injected as trusted content.
5. MainAgent receives structured failure Evidence and can continue safely.
6. Existing approval, receipt, worklog, WorkingState, and second-loop behavior remains compatible.
7. Targeted tool orchestration tests and full Maven compile pass.

Keep the change scoped to the Tool Use Harness. Do not redesign MCP discovery, capability exposure, ASK_USER, or final delivery as part of this fix.

package yhx.com.domain.agent.service.invocation;

import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.adapter.port.INodeClientPort;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.contract.ContractViolation;
import yhx.com.domain.agent.model.valobj.contract.RawOutputParseResult;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationFailureTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContractRepairRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientResponse;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationAttempt;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.prompt.PromptAssemblyCommand;
import yhx.com.domain.agent.model.valobj.prompt.PromptAssemblyResult;
import yhx.com.domain.agent.service.contract.ContractRegistry;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.contract.RawOutputParser;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.domain.agent.service.runtime.RunDiagnosticRecorder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class NodeInvocationPipeline {

    private final PromptAssembler promptAssembler;
    private final INodeClientPort nodeClientPort;
    private final RawOutputParser rawOutputParser;
    private final ContractRegistry contractRegistry;
    private final ContractValidator contractValidator;
    private final NodeOutputMapper nodeOutputMapper;
    private final RunDiagnosticRecorder diagnosticRecorder;

    public NodeInvocationPipeline(PromptAssembler promptAssembler, INodeClientPort nodeClientPort) {
        this(promptAssembler, nodeClientPort, RawOutputParser.defaultParser(), ContractRegistry.defaultRegistry(),
                ContractValidator.defaultValidator(), new NodeOutputMapper(), null);
    }

    public NodeInvocationPipeline(PromptAssembler promptAssembler,
                                  INodeClientPort nodeClientPort,
                                  RunDiagnosticRecorder diagnosticRecorder) {
        this(promptAssembler, nodeClientPort, RawOutputParser.defaultParser(), ContractRegistry.defaultRegistry(),
                ContractValidator.defaultValidator(), new NodeOutputMapper(), diagnosticRecorder);
    }

    public NodeInvocationPipeline(PromptAssembler promptAssembler,
                                  INodeClientPort nodeClientPort,
                                  RawOutputParser rawOutputParser,
                                  ContractRegistry contractRegistry,
                                  ContractValidator contractValidator,
                                  NodeOutputMapper nodeOutputMapper) {
        this(promptAssembler, nodeClientPort, rawOutputParser, contractRegistry, contractValidator, nodeOutputMapper, null);
    }

    public NodeInvocationPipeline(PromptAssembler promptAssembler,
                                  INodeClientPort nodeClientPort,
                                  RawOutputParser rawOutputParser,
                                  ContractRegistry contractRegistry,
                                  ContractValidator contractValidator,
                                  NodeOutputMapper nodeOutputMapper,
                                  RunDiagnosticRecorder diagnosticRecorder) {
        this.promptAssembler = promptAssembler;
        this.nodeClientPort = nodeClientPort;
        this.rawOutputParser = rawOutputParser;
        this.contractRegistry = contractRegistry;
        this.contractValidator = contractValidator;
        this.nodeOutputMapper = nodeOutputMapper;
        this.diagnosticRecorder = diagnosticRecorder;
    }

    public NodeInvocationResult invoke(NodeInvocationCommand command) {
        log.info("[AutoAgent][node-invoke] runId={}, component={}, contractVersion={}, promptVersion={}, modelCode={}, maxRepairAttempts={}",
                command == null ? null : command.getRunId(),
                command == null ? null : command.getComponentCode(),
                command == null ? null : command.getContractVersion(),
                command == null ? null : command.getPromptVersion(),
                command == null ? null : command.getModelCode(),
                command == null ? null : command.getMaxRepairAttempts());
        diagnostic(command == null ? null : command.getRunId(), "NODE_INVOKE", diagnosticMap(
                "componentCode", command == null ? null : command.getComponentCode(),
                "contractVersion", command == null ? null : command.getContractVersion(),
                "promptVersion", command == null ? null : command.getPromptVersion(),
                "modelCode", command == null ? null : command.getModelCode(),
                "maxRepairAttempts", command == null ? null : command.getMaxRepairAttempts()
        ));
        List<NodeInvocationAttempt> attempts = new ArrayList<>();
        InvocationEvaluation first = callAndEvaluate(command, command.getInputView(), false, 1);
        attempts.add(first.attempt());
        if (first.success()) {
            return result(NodeInvocationStatusEnumVO.SUCCESS, command, first, attempts, null, null);
        }

        int maxRepairAttempts = command.getMaxRepairAttempts() == null ? 0 : command.getMaxRepairAttempts();
        InvocationEvaluation last = first;
        for (int repairAttempt = 1; repairAttempt <= maxRepairAttempts; repairAttempt++) {
            ContractRepairRequest repairRequest = buildRepairRequest(command, last, repairAttempt);
            InvocationEvaluation repaired = callAndEvaluate(command, repairRequest, true, repairAttempt + 1);
            attempts.add(repaired.attempt());
            last = repaired;
            if (repaired.success()) {
                return result(NodeInvocationStatusEnumVO.REPAIR_SUCCEEDED, command, repaired, attempts, null, null);
            }
        }

        NodeInvocationStatusEnumVO status = first.parseResult() == null || !first.parseResult().isSuccess()
                ? NodeInvocationStatusEnumVO.PARSE_FAILED
                : NodeInvocationStatusEnumVO.CONTRACT_FAILED;
        if (maxRepairAttempts > 0) {
            status = NodeInvocationStatusEnumVO.REPAIR_FAILED;
        }
        return result(status, command, last, attempts,
                last.failureType() == null ? null : last.failureType().code(),
                last.failureMessage());
    }

    private InvocationEvaluation callAndEvaluate(NodeInvocationCommand command, Object inputView, boolean repairAttempt, int attemptNo) {
        PromptAssemblyResult promptResult = promptAssembler.assemble(PromptAssemblyCommand.builder()
                .runId(command.getRunId())
                .agentId(command.getAgentId())
                .componentCode(repairAttempt ? AgentComponentCodeEnumVO.CONTRACT_REPAIR.name() : command.getComponentCode())
                .contractVersion(command.getContractVersion())
                .promptVersion(command.getPromptVersion())
                .inputView(inputView)
                .metadata(command.getInvocationMetadata())
                .build());
        String prompt = promptResult.assembledPrompt();
        log.info("[AutoAgent][node-call] runId={}, component={}, attemptNo={}, repairAttempt={}, promptChars={}",
                command.getRunId(), command.getComponentCode(), attemptNo, repairAttempt, prompt == null ? 0 : prompt.length());
        diagnostic(command.getRunId(), "NODE_CALL", diagnosticMap(
                "componentCode", command.getComponentCode(),
                "attemptNo", attemptNo,
                "repairAttempt", repairAttempt,
                "promptChars", prompt == null ? 0 : prompt.length(),
                "prompt", prompt
        ));
        String rawOutput;
        try {
            NodeClientResponse response = nodeClientPort.call(NodeClientRequest.builder()
                    .runId(command.getRunId())
                    .componentCode(command.getComponentCode())
                    .modelCode(command.getModelCode())
                    .prompt(prompt)
                    .temperature(command.getTemperature())
                    .maxOutputTokens(command.getMaxOutputTokens())
                    .metadata(command.getInvocationMetadata())
                    .build());
            rawOutput = response == null ? null : response.getRawOutput();
        } catch (Exception e) {
            log.error("[AutoAgent][node-client-error] runId={}, component={}, attemptNo={}, repairAttempt={}",
                    command.getRunId(), command.getComponentCode(), attemptNo, repairAttempt, e);
            diagnosticError(command.getRunId(), "NODE_CLIENT_ERROR", e, diagnosticMap(
                    "componentCode", command.getComponentCode(),
                    "attemptNo", attemptNo,
                    "repairAttempt", repairAttempt
            ));
            NodeInvocationAttempt attempt = NodeInvocationAttempt.builder()
                    .attemptNo(attemptNo)
                    .componentCode(command.getComponentCode())
                    .prompt(prompt)
                    .failureType(NodeInvocationFailureTypeEnumVO.CLIENT_ERROR)
                    .failureMessage(e.getMessage())
                    .repairAttempt(repairAttempt)
                    .build();
            return new InvocationEvaluation(attempt, null, null, null, false, NodeInvocationFailureTypeEnumVO.CLIENT_ERROR, e.getMessage());
        }

        RawOutputParseResult parseResult = rawOutputParser.parse(rawOutput);
        ContractValidationResult validationResult = null;
        Object typedOutput = null;
        NodeInvocationFailureTypeEnumVO failureType = null;
        String failureMessage = null;

        if (parseResult.isSuccess()) {
            try {
                contractRegistry.getRequired(AgentComponentCodeEnumVO.valueOf(command.getComponentCode()));
                typedOutput = nodeOutputMapper.map(command.getComponentCode(), command.getContractVersion(), parseResult.getJsonObject());
                validationResult = validate(command.getComponentCode(), parseResult.getNormalizedJson());
                if (!validationResult.isPassed()) {
                    failureType = NodeInvocationFailureTypeEnumVO.CONTRACT_VIOLATION;
                    failureMessage = validationResult.getViolations().toString();
                }
            } catch (Exception e) {
                validationResult = ContractValidationResult.failed("MAPPING_FAILED", "$", e.getMessage());
                failureType = NodeInvocationFailureTypeEnumVO.CONTRACT_VIOLATION;
                failureMessage = e.getMessage();
            }
        } else {
            failureType = "EMPTY_OUTPUT".equals(parseResult.getErrorCode())
                    ? NodeInvocationFailureTypeEnumVO.EMPTY_OUTPUT
                    : NodeInvocationFailureTypeEnumVO.INVALID_JSON;
            failureMessage = parseResult.getErrorMessage();
        }

        boolean success = parseResult.isSuccess() && validationResult != null && validationResult.isPassed();
        if (success) {
            log.info("[AutoAgent][node-success] runId={}, component={}, attemptNo={}, repairAttempt={}, rawOutput={}",
                    command.getRunId(), command.getComponentCode(), attemptNo, repairAttempt, preview(rawOutput));
            diagnostic(command.getRunId(), "NODE_SUCCESS", diagnosticMap(
                    "componentCode", command.getComponentCode(),
                    "attemptNo", attemptNo,
                    "repairAttempt", repairAttempt,
                    "rawOutput", rawOutput
            ));
        } else {
            log.warn("[AutoAgent][node-invalid] runId={}, component={}, attemptNo={}, repairAttempt={}, failureType={}, failureMessage={}, rawOutput={}",
                    command.getRunId(), command.getComponentCode(), attemptNo, repairAttempt, failureType, failureMessage, preview(rawOutput));
            diagnostic(command.getRunId(), "NODE_INVALID", diagnosticMap(
                    "level", "WARN",
                    "componentCode", command.getComponentCode(),
                    "attemptNo", attemptNo,
                    "repairAttempt", repairAttempt,
                    "failureType", failureType == null ? null : failureType.code(),
                    "failureMessage", failureMessage,
                    "rawOutput", rawOutput
            ));
        }
        NodeInvocationAttempt attempt = NodeInvocationAttempt.builder()
                .attemptNo(attemptNo)
                .componentCode(command.getComponentCode())
                .prompt(prompt)
                .rawOutput(rawOutput)
                .parseResult(parseResult)
                .validationResult(validationResult)
                .failureType(failureType)
                .failureMessage(failureMessage)
                .repairAttempt(repairAttempt)
                .build();
        return new InvocationEvaluation(attempt, rawOutput, parseResult, validationResult, typedOutput, success, failureType, failureMessage);
    }

    private ContractValidationResult validate(String componentCode, String normalizedJson) {
        if (AgentComponentCodeEnumVO.MAIN_AGENT.name().equals(componentCode)
                || AgentComponentCodeEnumVO.FINAL_REPAIR.name().equals(componentCode)) {
            return contractValidator.validateMainAgentAction(normalizedJson);
        }
        if (AgentComponentCodeEnumVO.CONTEXT_PLANNER.name().equals(componentCode)) {
            return contractValidator.validateContextPlannerOutput(normalizedJson);
        }
        if (AgentComponentCodeEnumVO.RAG_VERIFIER.name().equals(componentCode)
                || AgentComponentCodeEnumVO.TOOL_VERIFIER.name().equals(componentCode)) {
            return contractValidator.validateVerificationResult(normalizedJson);
        }
        return ContractValidationResult.passed();
    }

    private ContractRepairRequest buildRepairRequest(NodeInvocationCommand command, InvocationEvaluation last, int repairAttempt) {
        return ContractRepairRequest.builder()
                .originalComponentCode(command.getComponentCode())
                .originalContractVersion(command.getContractVersion())
                .invalidRawOutput(last.rawOutput())
                .validationFailures(validationFailures(last))
                .allowedRepairScope("Fix JSON syntax, missing required fields, forbidden fields, or StateDelta scope violations only.")
                .currentRetryAttempt(repairAttempt)
                .build();
    }

    private List<String> validationFailures(InvocationEvaluation evaluation) {
        if (evaluation.validationResult() != null && !evaluation.validationResult().isPassed()) {
            return evaluation.validationResult().getViolations().stream()
                    .map(this::formatViolation)
                    .toList();
        }
        if (evaluation.parseResult() != null && !evaluation.parseResult().isSuccess()) {
            return List.of(evaluation.parseResult().getErrorCode() + ": " + evaluation.parseResult().getErrorMessage());
        }
        return List.of("Unknown contract failure.");
    }

    private String formatViolation(ContractViolation violation) {
        return violation.getCode() + " at " + violation.getField() + ": " + violation.getMessage();
    }

    private String preview(String rawOutput) {
        if (rawOutput == null) {
            return null;
        }
        String normalized = rawOutput.replace("\r", "\\r").replace("\n", "\\n");
        return normalized.length() <= 600 ? normalized : normalized.substring(0, 600) + "...";
    }

    private NodeInvocationResult result(NodeInvocationStatusEnumVO status,
                                        NodeInvocationCommand command,
                                        InvocationEvaluation evaluation,
                                        List<NodeInvocationAttempt> attempts,
                                        String failureCode,
                                        String failureMessage) {
        return NodeInvocationResult.builder()
                .status(status)
                .componentCode(command.getComponentCode())
                .contractVersion(command.getContractVersion())
                .typedOutput(evaluation.typedOutput())
                .rawOutput(evaluation.rawOutput())
                .parseResult(evaluation.parseResult())
                .validationResult(evaluation.validationResult())
                .attempts(attempts)
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .build();
    }

    private void diagnostic(String runId, String event, Map<String, Object> details) {
        if (diagnosticRecorder == null) {
            return;
        }
        diagnosticRecorder.record(runId, "NODE", event, sanitize(details));
    }

    private void diagnosticError(String runId, String event, Throwable error, Map<String, Object> details) {
        if (diagnosticRecorder == null) {
            return;
        }
        diagnosticRecorder.error(runId, "NODE", event, error, sanitize(details));
    }

    private Map<String, Object> sanitize(Map<String, Object> details) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, item) -> value.put(key, item));
        }
        return value;
    }

    private Map<String, Object> diagnosticMap(Object... keyValues) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (keyValues == null) {
            return value;
        }
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            value.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return value;
    }

    private record InvocationEvaluation(NodeInvocationAttempt attempt,
                                        String rawOutput,
                                        RawOutputParseResult parseResult,
                                        ContractValidationResult validationResult,
                                        Object typedOutput,
                                        boolean success,
                                        NodeInvocationFailureTypeEnumVO failureType,
                                        String failureMessage) {
        private InvocationEvaluation(NodeInvocationAttempt attempt,
                                     String rawOutput,
                                     RawOutputParseResult parseResult,
                                     ContractValidationResult validationResult,
                                     boolean success,
                                     NodeInvocationFailureTypeEnumVO failureType,
                                     String failureMessage) {
            this(attempt, rawOutput, parseResult, validationResult, null, success, failureType, failureMessage);
        }
    }
}

package yhx.com.domain.agent.service.invocation;

import yhx.com.domain.agent.adapter.port.INodeClientPort;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.contract.ContractViolation;
import yhx.com.domain.agent.model.valobj.contract.RawOutputParseResult;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.service.contract.ContractRegistry;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.contract.RawOutputParser;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.domain.agent.service.prompt.PromptAssemblyCommand;
import yhx.com.domain.agent.service.prompt.PromptAssemblyResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NodeInvocationPipeline {

    private final PromptAssembler promptAssembler;
    private final INodeClientPort nodeClientPort;
    private final RawOutputParser rawOutputParser;
    private final ContractRegistry contractRegistry;
    private final ContractValidator contractValidator;
    private final NodeOutputMapper nodeOutputMapper;

    public NodeInvocationPipeline(PromptAssembler promptAssembler, INodeClientPort nodeClientPort) {
        this(promptAssembler, nodeClientPort, RawOutputParser.defaultParser(), ContractRegistry.defaultRegistry(),
                ContractValidator.defaultValidator(), new NodeOutputMapper());
    }

    public NodeInvocationPipeline(PromptAssembler promptAssembler,
                                  INodeClientPort nodeClientPort,
                                  RawOutputParser rawOutputParser,
                                  ContractRegistry contractRegistry,
                                  ContractValidator contractValidator,
                                  NodeOutputMapper nodeOutputMapper) {
        this.promptAssembler = promptAssembler;
        this.nodeClientPort = nodeClientPort;
        this.rawOutputParser = rawOutputParser;
        this.contractRegistry = contractRegistry;
        this.contractValidator = contractValidator;
        this.nodeOutputMapper = nodeOutputMapper;
    }

    public NodeInvocationResult invoke(NodeInvocationCommand command) {
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

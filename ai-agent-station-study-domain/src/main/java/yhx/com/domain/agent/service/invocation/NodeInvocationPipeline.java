package yhx.com.domain.agent.service.invocation;

import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.adapter.port.INodeClientPort;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.contract.ContractViolation;
import yhx.com.domain.agent.model.valobj.contract.RawOutputParseResult;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationFailureTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContractRepairRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientRequest;
import yhx.com.domain.agent.model.valobj.invocation.NodeClientResponse;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationAttempt;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.prompt.PromptAssemblyCommand;
import yhx.com.domain.agent.model.valobj.prompt.PromptAssemblyResult;
import yhx.com.domain.agent.service.contract.ContractRegistry;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.contract.RawOutputParser;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.domain.agent.service.runtime.RunDiagnosticRecorder;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class NodeInvocationPipeline {

    private static final int DIAGNOSTIC_PREVIEW_LIMIT = 2000;
    private static final int MAX_EMPTY_OUTPUT_RETRIES = 1;
    private static final int DEFAULT_MAX_MAIN_AGENT_PROMPT_CHARS = 250_000;
    private static final String CONTRACT_REPAIR_PROMPT_VERSION = "v1";

    private final PromptAssembler promptAssembler;
    private final INodeClientPort nodeClientPort;
    private final RawOutputParser rawOutputParser;
    private final ContractRegistry contractRegistry;
    private final ContractValidator contractValidator;
    private final NodeOutputMapper nodeOutputMapper;
    private final RunDiagnosticRecorder diagnosticRecorder;
    private final FunctionCallMapper functionCallMapper;
    private final NodeFunctionSpecRegistry functionSpecRegistry;
    private final DeveloperTraceRecorder developerTraceRecorder;
    private final int maxMainAgentPromptChars;

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
                                  RunDiagnosticRecorder diagnosticRecorder,
                                  DeveloperTraceRecorder developerTraceRecorder) {
        this(promptAssembler, nodeClientPort, diagnosticRecorder, developerTraceRecorder,
                DEFAULT_MAX_MAIN_AGENT_PROMPT_CHARS);
    }

    public NodeInvocationPipeline(PromptAssembler promptAssembler,
                                  INodeClientPort nodeClientPort,
                                  RunDiagnosticRecorder diagnosticRecorder,
                                  DeveloperTraceRecorder developerTraceRecorder,
                                  int maxMainAgentPromptChars) {
        this(promptAssembler, nodeClientPort, RawOutputParser.defaultParser(), ContractRegistry.defaultRegistry(),
                ContractValidator.defaultValidator(), new NodeOutputMapper(), diagnosticRecorder,
                FunctionCallMapper.defaultMapper(), NodeFunctionSpecRegistry.defaultRegistry(), developerTraceRecorder,
                maxMainAgentPromptChars);
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
        this(promptAssembler, nodeClientPort, rawOutputParser, contractRegistry, contractValidator, nodeOutputMapper,
                diagnosticRecorder, FunctionCallMapper.defaultMapper(), NodeFunctionSpecRegistry.defaultRegistry(), null);
    }

    public NodeInvocationPipeline(PromptAssembler promptAssembler,
                                  INodeClientPort nodeClientPort,
                                  RawOutputParser rawOutputParser,
                                  ContractRegistry contractRegistry,
                                  ContractValidator contractValidator,
                                  NodeOutputMapper nodeOutputMapper,
                                  RunDiagnosticRecorder diagnosticRecorder,
                                  FunctionCallMapper functionCallMapper,
                                  NodeFunctionSpecRegistry functionSpecRegistry) {
        this(promptAssembler, nodeClientPort, rawOutputParser, contractRegistry, contractValidator, nodeOutputMapper,
                diagnosticRecorder, functionCallMapper, functionSpecRegistry, null);
    }

    public NodeInvocationPipeline(PromptAssembler promptAssembler,
                                  INodeClientPort nodeClientPort,
                                  RawOutputParser rawOutputParser,
                                  ContractRegistry contractRegistry,
                                  ContractValidator contractValidator,
                                  NodeOutputMapper nodeOutputMapper,
                                  RunDiagnosticRecorder diagnosticRecorder,
                                  FunctionCallMapper functionCallMapper,
                                  NodeFunctionSpecRegistry functionSpecRegistry,
                                  DeveloperTraceRecorder developerTraceRecorder) {
        this(promptAssembler, nodeClientPort, rawOutputParser, contractRegistry, contractValidator, nodeOutputMapper,
                diagnosticRecorder, functionCallMapper, functionSpecRegistry, developerTraceRecorder,
                DEFAULT_MAX_MAIN_AGENT_PROMPT_CHARS);
    }

    public NodeInvocationPipeline(PromptAssembler promptAssembler,
                                  INodeClientPort nodeClientPort,
                                  RawOutputParser rawOutputParser,
                                  ContractRegistry contractRegistry,
                                  ContractValidator contractValidator,
                                  NodeOutputMapper nodeOutputMapper,
                                  RunDiagnosticRecorder diagnosticRecorder,
                                  FunctionCallMapper functionCallMapper,
                                  NodeFunctionSpecRegistry functionSpecRegistry,
                                  DeveloperTraceRecorder developerTraceRecorder,
                                  int maxMainAgentPromptChars) {
        this.promptAssembler = promptAssembler;
        this.nodeClientPort = nodeClientPort;
        this.rawOutputParser = rawOutputParser;
        this.contractRegistry = contractRegistry;
        this.contractValidator = contractValidator;
        this.nodeOutputMapper = nodeOutputMapper;
        this.diagnosticRecorder = diagnosticRecorder;
        this.functionCallMapper = functionCallMapper == null ? FunctionCallMapper.defaultMapper() : functionCallMapper;
        this.functionSpecRegistry = functionSpecRegistry == null ? NodeFunctionSpecRegistry.defaultRegistry() : functionSpecRegistry;
        this.developerTraceRecorder = developerTraceRecorder;
        this.maxMainAgentPromptChars = Math.max(0, maxMainAgentPromptChars);
    }

    public NodeInvocationResult invoke(NodeInvocationCommand command) {
        validateCommandContract(command);
        log.info("[AutoAgent][node-invoke] runId={}, component={}, contractVersion={}, promptVersion={}, modelCode={}, maxRepairAttempts={}",
                command == null ? null : command.getRunId(),
                command == null ? null : command.getComponentCode(),
                command == null ? null : command.getContractVersion(),
                command == null ? null : command.getPromptVersion(),
                command == null ? null : command.getModelCode(),
                command == null ? null : command.getMaxRepairAttempts());
        AutoAgentHumanLog.stage("节点调用", command == null ? null : command.getRunId(), "准备调用 "
                + (command == null ? null : command.getComponentCode())
                + "，模型=" + (command == null ? null : command.getModelCode())
                + "，契约=" + (command == null ? null : command.getContractVersion()));
        diagnostic(command == null ? null : command.getRunId(), "NODE_INVOKE", diagnosticMap(
                "componentCode", command == null ? null : command.getComponentCode(),
                "contractVersion", command == null ? null : command.getContractVersion(),
                "promptVersion", command == null ? null : command.getPromptVersion(),
                "modelCode", command == null ? null : command.getModelCode(),
                "maxRepairAttempts", command == null ? null : command.getMaxRepairAttempts()
        ));
        List<NodeInvocationAttempt> attempts = new ArrayList<>();
        InvocationEvaluation last = callAndEvaluate(command, command.getInputView(), false, 1);
        attempts.add(last.attempt());
        int nextAttemptNo = 2;
        for (int retry = 0;
             retry < MAX_EMPTY_OUTPUT_RETRIES && NodeInvocationFailureTypeEnumVO.EMPTY_OUTPUT.equals(last.failureType());
             retry++) {
            log.warn("[AutoAgent][node-retry] runId={}, component={}, attemptNo={}, reason=EMPTY_OUTPUT, retryOriginal=true",
                    command.getRunId(), command.getComponentCode(), nextAttemptNo);
            AutoAgentHumanLog.stage("节点调用", command.getRunId(), command.getComponentCode()
                    + " 返回空响应，正在重新调用原节点：attempt=" + nextAttemptNo);
            diagnostic(command.getRunId(), "NODE_RETRY", diagnosticMap(
                    "componentCode", command.getComponentCode(),
                    "attemptNo", nextAttemptNo,
                    "reason", NodeInvocationFailureTypeEnumVO.EMPTY_OUTPUT.code(),
                    "retryOriginal", true
            ));
            last = callAndEvaluate(command, command.getInputView(), false, nextAttemptNo++);
            attempts.add(last.attempt());
        }
        if (last.success()) {
            return result(NodeInvocationStatusEnumVO.SUCCESS, command, last, attempts, null, null);
        }
        if (NodeInvocationFailureTypeEnumVO.CLIENT_ERROR.equals(last.failureType())) {
            return result(NodeInvocationStatusEnumVO.CLIENT_FAILED, command, last, attempts,
                    last.failureType().code(), last.failureMessage());
        }
        if (NodeInvocationFailureTypeEnumVO.EMPTY_OUTPUT.equals(last.failureType())) {
            return result(NodeInvocationStatusEnumVO.PARSE_FAILED, command, last, attempts,
                    last.failureType().code(), last.failureMessage());
        }

        int maxRepairAttempts = command.getMaxRepairAttempts() == null ? 0 : command.getMaxRepairAttempts();
        for (int repairAttempt = 1; repairAttempt <= maxRepairAttempts; repairAttempt++) {
            ContractRepairRequest repairRequest = buildRepairRequest(command, last, repairAttempt);
            InvocationEvaluation repaired = callAndEvaluate(command, repairRequest, true, nextAttemptNo++);
            attempts.add(repaired.attempt());
            last = repaired;
            if (repaired.success()) {
                return result(NodeInvocationStatusEnumVO.REPAIR_SUCCEEDED, command, repaired, attempts, null, null);
            }
        }

        NodeInvocationStatusEnumVO status = last.parseResult() == null || !last.parseResult().isSuccess()
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
                .promptVersion(repairAttempt ? CONTRACT_REPAIR_PROMPT_VERSION : command.getPromptVersion())
                .inputView(inputView)
                .metadata(command.getInvocationMetadata())
                .invocationMode(invocationMode(command, repairAttempt))
                .functionSpecs(functionSpecs(command, repairAttempt))
                .build());
        String prompt = promptResult.assembledPrompt();
        log.info("[AutoAgent][node-call] runId={}, component={}, attemptNo={}, repairAttempt={}, promptChars={}",
                command.getRunId(), command.getComponentCode(), attemptNo, repairAttempt, prompt == null ? 0 : prompt.length());
        AutoAgentHumanLog.stage("节点调用", command.getRunId(), "调用 "
                + command.getComponentCode() + "：attempt=" + attemptNo
                + "，repair=" + repairAttempt
                + "，promptChars=" + (prompt == null ? 0 : prompt.length()));
        diagnostic(command.getRunId(), "NODE_CALL", diagnosticMap(
                "componentCode", command.getComponentCode(),
                "attemptNo", attemptNo,
                "repairAttempt", repairAttempt,
                "promptChars", prompt == null ? 0 : prompt.length(),
                "promptPreview", boundedPreview(prompt, DIAGNOSTIC_PREVIEW_LIMIT)
        ));
        if (isOversizedMainAgentPrompt(command, prompt)) {
            String failureMessage = "NODE_PROMPT_TOO_LARGE: MainAgent prompt has " + prompt.length()
                    + " characters; configured limit is " + maxMainAgentPromptChars
                    + ". Narrow or chunk the active payload working set.";
            log.error("[AutoAgent][node-prompt-too-large] runId={}, component={}, attemptNo={}, promptChars={}, limit={}",
                    command.getRunId(), command.getComponentCode(), attemptNo, prompt.length(), maxMainAgentPromptChars);
            NodeInvocationAttempt attempt = NodeInvocationAttempt.builder()
                    .attemptNo(attemptNo)
                    .componentCode(command.getComponentCode())
                    .prompt(boundedPreview(prompt, DIAGNOSTIC_PREVIEW_LIMIT))
                    .failureType(NodeInvocationFailureTypeEnumVO.CLIENT_ERROR)
                    .failureMessage(failureMessage)
                    .repairAttempt(repairAttempt)
                    .build();
            observeOutput(command, attemptNo, repairAttempt, null, null, null, null,
                    NodeInvocationFailureTypeEnumVO.CLIENT_ERROR, failureMessage, false);
            return new InvocationEvaluation(attempt, null, null, null, false,
                    NodeInvocationFailureTypeEnumVO.CLIENT_ERROR, failureMessage);
        }
        observeInput(command, promptResult, prompt, repairAttempt, attemptNo);
        String rawOutput;
        NodeClientResponse response;
        try {
            response = nodeClientPort.call(NodeClientRequest.builder()
                    .runId(command.getRunId())
                    .componentCode(command.getComponentCode())
                    .modelCode(command.getModelCode())
                    .prompt(prompt)
                    .systemPrompt(promptResult.systemPrompt())
                    .userPrompt(promptResult.userPrompt())
                    .temperature(command.getTemperature())
                    .maxOutputTokens(command.getMaxOutputTokens())
                    .metadata(command.getInvocationMetadata())
                    .invocationMode(invocationMode(command, repairAttempt))
                    .functionSpecs(functionSpecs(command, repairAttempt))
                    .build());
            rawOutput = rawOutput(command, response, repairAttempt);
        } catch (Exception e) {
            log.error("[AutoAgent][node-client-error] runId={}, component={}, attemptNo={}, repairAttempt={}",
                    command.getRunId(), command.getComponentCode(), attemptNo, repairAttempt, e);
            AutoAgentHumanLog.stage("节点调用", command.getRunId(), command.getComponentCode()
                    + " 调用失败：客户端异常，原因=" + e.getMessage());
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
            observeOutput(command, attemptNo, repairAttempt, null, null, null, null,
                    NodeInvocationFailureTypeEnumVO.CLIENT_ERROR, e.getMessage(), false);
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
            AutoAgentHumanLog.stage("节点调用", command.getRunId(), command.getComponentCode()
                    + " 输出合法：attempt=" + attemptNo + "，repair=" + repairAttempt);
            diagnostic(command.getRunId(), "NODE_SUCCESS", diagnosticMap(
                    "componentCode", command.getComponentCode(),
                    "attemptNo", attemptNo,
                    "repairAttempt", repairAttempt,
                    "rawOutputChars", rawOutput == null ? 0 : rawOutput.length(),
                    "rawOutputPreview", boundedPreview(rawOutput, DIAGNOSTIC_PREVIEW_LIMIT)
            ));
        } else {
            log.warn("[AutoAgent][node-invalid] runId={}, component={}, attemptNo={}, repairAttempt={}, failureType={}, failureMessage={}, rawOutput={}",
                    command.getRunId(), command.getComponentCode(), attemptNo, repairAttempt, failureType, failureMessage, preview(rawOutput));
            AutoAgentHumanLog.stage("节点调用", command.getRunId(),
                    AutoAgentHumanLog.nodeInvalidSummary(command.getComponentCode(), failureType, failureMessage, rawOutput));
            diagnostic(command.getRunId(), "NODE_INVALID", diagnosticMap(
                    "level", "WARN",
                    "componentCode", command.getComponentCode(),
                    "attemptNo", attemptNo,
                    "repairAttempt", repairAttempt,
                    "failureType", failureType == null ? null : failureType.code(),
                    "failureMessage", failureMessage,
                    "rawOutputChars", rawOutput == null ? 0 : rawOutput.length(),
                    "rawOutputPreview", boundedPreview(rawOutput, DIAGNOSTIC_PREVIEW_LIMIT)
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
        observeOutput(command, attemptNo, repairAttempt, rawOutput, parseResult, validationResult, typedOutput,
                failureType, failureMessage, success);
        return new InvocationEvaluation(attempt, rawOutput, parseResult, validationResult, typedOutput, success, failureType, failureMessage);
    }

    private void observeInput(NodeInvocationCommand command, PromptAssemblyResult promptResult, String prompt,
                              boolean repairAttempt, int attemptNo) {
        if (developerTraceRecorder == null || command == null) {
            return;
        }
        Map<String, Object> details = diagnosticMap(
                "agentId", command.getAgentId(),
                "contractVersion", command.getContractVersion(),
                "promptVersion", command.getPromptVersion(),
                "modelCode", command.getModelCode(),
                "inputView", command.getInputView(),
                "prompt", prompt,
                "systemPrompt", promptResult == null ? null : promptResult.systemPrompt(),
                "userPrompt", promptResult == null ? null : promptResult.userPrompt(),
                "invocationMetadata", command.getInvocationMetadata(),
                "invocationMode", invocationMode(command, repairAttempt),
                "functionSpecs", functionSpecs(command, repairAttempt),
                "repairAttempt", repairAttempt
        );
        developerTraceRecorder.nodeInput(command.getRunId(), loopIndex(command), command.getComponentCode(), attemptNo, details);
    }

    private void observeOutput(NodeInvocationCommand command, int attemptNo, boolean repairAttempt, String rawOutput,
                               RawOutputParseResult parseResult, ContractValidationResult validationResult,
                               Object typedOutput, NodeInvocationFailureTypeEnumVO failureType,
                               String failureMessage, boolean success) {
        if (developerTraceRecorder == null || command == null) {
            return;
        }
        Map<String, Object> details = diagnosticMap(
                "agentId", command.getAgentId(),
                "rawOutput", rawOutput,
                "parseResult", parseResult,
                "validationResult", validationResult,
                "typedOutput", typedOutput,
                "failureType", failureType == null ? null : failureType.code(),
                "failureMessage", failureMessage,
                "repairAttempt", repairAttempt,
                "success", success
        );
        developerTraceRecorder.nodeOutput(command.getRunId(), loopIndex(command), command.getComponentCode(), attemptNo, details);
    }

    private Integer loopIndex(NodeInvocationCommand command) {
        if (command == null || command.getInvocationMetadata() == null) {
            return null;
        }
        Object value = command.getInvocationMetadata().get("loopIndex");
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isOversizedMainAgentPrompt(NodeInvocationCommand command, String prompt) {
        return command != null
                && AgentComponentCodeEnumVO.MAIN_AGENT.name().equals(command.getComponentCode())
                && maxMainAgentPromptChars > 0
                && prompt != null
                && prompt.length() > maxMainAgentPromptChars;
    }

    private ContractValidationResult validate(String componentCode, String normalizedJson) {
        return contractValidator.validateComponentOutput(componentCode, normalizedJson);
    }

    private void validateCommandContract(NodeInvocationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Node invocation command is required.");
        }
        AgentComponentCodeEnumVO component;
        try {
            component = AgentComponentCodeEnumVO.valueOf(command.getComponentCode());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported node component: " + command.getComponentCode(), exception);
        }
        String expectedVersion = contractRegistry.getRequired(component).getVersion();
        if (!expectedVersion.equals(command.getContractVersion())) {
            throw new IllegalArgumentException("Unsupported " + component + " contract version: "
                    + command.getContractVersion() + "; expected " + expectedVersion);
        }
    }

    private String rawOutput(NodeInvocationCommand command, NodeClientResponse response, boolean repairAttempt) {
        if (NodeInvocationModeEnumVO.FUNCTION_CALL.equals(invocationMode(command, repairAttempt))) {
            return functionCallMapper.mapToRawOutput(command.getComponentCode(), response == null ? null : response.getFunctionCall());
        }
        return response == null ? null : response.getRawOutput();
    }

    private NodeInvocationModeEnumVO invocationMode(NodeInvocationCommand command, boolean repairAttempt) {
        if (repairAttempt) {
            return NodeInvocationModeEnumVO.TEXT_JSON;
        }
        return command.getInvocationMode() == null ? NodeInvocationModeEnumVO.TEXT_JSON : command.getInvocationMode();
    }

    private List<NodeFunctionSpecVO> functionSpecs(NodeInvocationCommand command, boolean repairAttempt) {
        if (!NodeInvocationModeEnumVO.FUNCTION_CALL.equals(invocationMode(command, repairAttempt))) {
            return List.of();
        }
        if (command.getFunctionSpecs() != null && !command.getFunctionSpecs().isEmpty()) {
            return command.getFunctionSpecs();
        }
        return functionSpecRegistry.resolve(command.getComponentCode());
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

    private String boundedPreview(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 20)) + "... (" + normalized.length() + " chars)";
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

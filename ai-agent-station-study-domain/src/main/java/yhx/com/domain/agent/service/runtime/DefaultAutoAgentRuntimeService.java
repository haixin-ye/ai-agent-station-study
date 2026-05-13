package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.model.valobj.enums.RunStatusEnumVO;
import yhx.com.domain.agent.service.contract.ContractValidationResult;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.contract.RawOutputParseResult;
import yhx.com.domain.agent.service.contract.RawOutputParser;

public class DefaultAutoAgentRuntimeService implements AutoAgentRuntimeService {

    private static final String SAFE_FAILURE_ANSWER = "The answer could not be safely delivered.";

    private final MainAgentNodePort mainAgentNodePort;
    private final ContractValidator contractValidator;
    private final RawOutputParser rawOutputParser;
    private final FinalResponseGuard finalResponseGuard;

    public DefaultAutoAgentRuntimeService(MainAgentNodePort mainAgentNodePort) {
        this(mainAgentNodePort, ContractValidator.defaultValidator(), RawOutputParser.defaultParser(), new FinalResponseGuard());
    }

    public DefaultAutoAgentRuntimeService(MainAgentNodePort mainAgentNodePort,
                                          ContractValidator contractValidator,
                                          RawOutputParser rawOutputParser,
                                          FinalResponseGuard finalResponseGuard) {
        this.mainAgentNodePort = mainAgentNodePort;
        this.contractValidator = contractValidator;
        this.rawOutputParser = rawOutputParser;
        this.finalResponseGuard = finalResponseGuard;
    }

    @Override
    public RuntimeResult start(RuntimeStartCommand command) {
        if (command == null) {
            return failed(null, null, "MISSING_COMMAND");
        }

        String stateViewJson = buildMinimalStateView(command);
        String rawAction = mainAgentNodePort.call(stateViewJson);
        ContractValidationResult validationResult = contractValidator.validateMainAgentAction(rawAction);
        if (!validationResult.isPassed()) {
            return failed(command.getRunId(), command.getSessionId(), validationResult.getViolations().get(0).getCode());
        }

        RawOutputParseResult parseResult = rawOutputParser.parse(rawAction);
        if (!parseResult.isSuccess()) {
            return failed(command.getRunId(), command.getSessionId(), parseResult.getErrorCode());
        }

        String finalAnswer = extractFinalAnswer(parseResult.getJsonObject());
        if (!finalResponseGuard.isSafe(finalAnswer)) {
            return failed(command.getRunId(), command.getSessionId(), "FINAL_INTERNAL_LEAK");
        }

        return RuntimeResult.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .runStatus(RunStatusEnumVO.COMPLETED)
                .finalAnswer(finalAnswer)
                .build();
    }

    private String buildMinimalStateView(RuntimeStartCommand command) {
        JSONObject root = new JSONObject();
        root.put("runId", command.getRunId());
        root.put("sessionId", command.getSessionId());
        root.put("userId", command.getUserId());
        root.put("userInput", command.getUserInput());
        return root.toJSONString();
    }

    private String extractFinalAnswer(JSONObject action) {
        JSONObject stateDelta = action.getJSONObject("stateDelta");
        if (stateDelta == null) {
            return null;
        }
        JSONObject finalAnswerCandidate = stateDelta.getJSONObject("finalAnswerCandidate");
        if (finalAnswerCandidate == null) {
            return null;
        }
        String content = finalAnswerCandidate.getString("content");
        if (content != null) {
            return content;
        }
        return finalAnswerCandidate.getString("text");
    }

    private RuntimeResult failed(String runId, String sessionId, String failureCode) {
        return RuntimeResult.builder()
                .runId(runId)
                .sessionId(sessionId)
                .runStatus(RunStatusEnumVO.FAILED)
                .failureCode(failureCode)
                .finalAnswer(SAFE_FAILURE_ANSWER)
                .build();
    }
}

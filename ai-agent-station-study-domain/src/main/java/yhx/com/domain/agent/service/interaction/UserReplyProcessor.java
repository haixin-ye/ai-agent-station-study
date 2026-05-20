package yhx.com.domain.agent.service.interaction;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.UserAnswerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.UserAnswerVO;
import yhx.com.domain.agent.model.valobj.interaction.UserInputResolveCommand;

import java.util.List;
import java.util.Map;

public class UserReplyProcessor {

    private static final String SINGLE_CHOICE = "SINGLE_CHOICE";
    private static final String SINGLE_CHOICE_OR_FREE_TEXT = "SINGLE_CHOICE_OR_FREE_TEXT";
    private static final String FREE_TEXT = "FREE_TEXT";

    private final IPayloadRepository payloadRepository;

    public UserReplyProcessor(IPayloadRepository payloadRepository) {
        this.payloadRepository = payloadRepository;
    }

    public UserAnswerVO process(AgentPendingInputEntity pendingInput, UserInputResolveCommand command) {
        if (pendingInput == null) {
            return failed(command, "Missing pending input.");
        }
        if (Boolean.TRUE.equals(command.getCancelled())) {
            return UserAnswerVO.builder()
                    .pendingId(pendingInput.getPendingId())
                    .runId(pendingInput.getRunId())
                    .status(UserAnswerStatusEnumVO.CANCELLED)
                    .answerType(UserAnswerTypeEnumVO.CANCEL)
                    .build();
        }
        if (command.getSelectedOptionId() != null && !command.getSelectedOptionId().isBlank()) {
            return resolveOption(pendingInput, command);
        }
        if (command.getFreeText() != null && !command.getFreeText().isBlank()) {
            return resolveFreeText(pendingInput, command);
        }
        return failed(command, "User answer is empty.");
    }

    private UserAnswerVO resolveOption(AgentPendingInputEntity pendingInput, UserInputResolveCommand command) {
        Map<String, Object> option = findOption(pendingInput.getOptionsRef(), command.getSelectedOptionId());
        if (option == null) {
            return failed(command, "Unknown option id.");
        }
        if (PendingInputTypeEnumVO.TOOL_APPROVAL.code().equals(pendingInput.getPendingType())) {
            Object value = option.get("value");
            if (!(value instanceof Map<?, ?> valueMap)
                    || (!"APPROVED".equals(String.valueOf(valueMap.get("decision")))
                    && !"REJECTED".equals(String.valueOf(valueMap.get("decision"))))) {
                return failed(command, "Tool approval option must explicitly approve or reject.");
            }
        }
        return UserAnswerVO.builder()
                .pendingId(pendingInput.getPendingId())
                .runId(pendingInput.getRunId())
                .status(UserAnswerStatusEnumVO.RESOLVED)
                .answerType(UserAnswerTypeEnumVO.OPTION)
                .selectedOptionId(command.getSelectedOptionId())
                .value(optionValue(option, command.getSelectedOptionId()))
                .metadata(command.getRequestMetadata())
                .build();
    }

    private Object optionValue(Map<String, Object> option, String selectedOptionId) {
        if (option == null) {
            return selectedOptionId;
        }
        Object value = option.get("value");
        if (value != null) {
            return value;
        }
        Object label = option.get("label");
        if (label != null) {
            return label;
        }
        return selectedOptionId;
    }

    private UserAnswerVO resolveFreeText(AgentPendingInputEntity pendingInput, UserInputResolveCommand command) {
        if (PendingInputTypeEnumVO.TOOL_APPROVAL.code().equals(pendingInput.getPendingType())) {
            return failed(command, "Tool approval does not accept free text.");
        }
        String inputMode = pendingInput.getInputMode();
        if (SINGLE_CHOICE.equals(inputMode)) {
            return failed(command, "Single-choice pending input does not accept free text.");
        }
        if (!FREE_TEXT.equals(inputMode) && !SINGLE_CHOICE_OR_FREE_TEXT.equals(inputMode)) {
            return failed(command, "Pending input does not accept free text.");
        }
        return UserAnswerVO.builder()
                .pendingId(pendingInput.getPendingId())
                .runId(pendingInput.getRunId())
                .status(UserAnswerStatusEnumVO.RESOLVED)
                .answerType(UserAnswerTypeEnumVO.FREE_TEXT)
                .freeText(command.getFreeText())
                .value(command.getFreeText())
                .metadata(command.getRequestMetadata())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findOption(String optionsRef, String selectedOptionId) {
        if (payloadRepository == null || optionsRef == null || selectedOptionId == null) {
            return null;
        }
        return payloadRepository.findContent(optionsRef)
                .map(JSON::parseArray)
                .stream()
                .flatMap(List::stream)
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> (Map<String, Object>) item)
                .filter(option -> selectedOptionId.equals(String.valueOf(option.get("optionId")))
                        || selectedOptionId.equals(String.valueOf(option.get("id"))))
                .findFirst()
                .orElse(null);
    }

    private UserAnswerVO failed(UserInputResolveCommand command, String message) {
        return UserAnswerVO.builder()
                .pendingId(command == null ? null : command.getPendingId())
                .runId(command == null ? null : command.getRunId())
                .status(UserAnswerStatusEnumVO.FAILED)
                .answerType(UserAnswerTypeEnumVO.INVALID)
                .failureMessage(message)
                .build();
    }
}

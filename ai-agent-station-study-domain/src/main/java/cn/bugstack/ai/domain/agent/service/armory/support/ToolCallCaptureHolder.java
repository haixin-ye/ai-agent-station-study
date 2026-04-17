package cn.bugstack.ai.domain.agent.service.armory.support;

import cn.bugstack.ai.domain.agent.model.entity.ToolExecutionRecordVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local holder used to capture real tool callback executions during one chat invocation.
 */
public final class ToolCallCaptureHolder {

    private static final ThreadLocal<List<ToolExecutionRecordVO>> RECORDS =
            ThreadLocal.withInitial(ArrayList::new);

    private ToolCallCaptureHolder() {
    }

    public static void start() {
        RECORDS.set(new ArrayList<>());
    }

    public static void record(String toolName,
                              String requestPayload,
                              String responsePayload,
                              boolean success,
                              String errorType,
                              String errorMessage) {
        RECORDS.get().add(ToolExecutionRecordVO.builder()
                .toolName(toolName)
                .requestPayload(requestPayload)
                .responsePayload(responsePayload)
                .normalizedOutcome(success ? "SUCCESS" : "FAILED")
                .success(success)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now().toString())
                .build());
    }

    public static List<ToolExecutionRecordVO> finish() {
        List<ToolExecutionRecordVO> records = new ArrayList<>(RECORDS.get());
        RECORDS.remove();
        return records;
    }
}

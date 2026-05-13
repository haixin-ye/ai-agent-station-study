package yhx.com.domain.agent.model.valobj.enums.armory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum AiClientTypeEnumVO {

    DEFAULT("DEFAULT", "Default client"),
    TASK_ANALYZER_CLIENT("TASK_ANALYZER_CLIENT", "Task analyzer client"),
    PRECISION_EXECUTOR_CLIENT("PRECISION_EXECUTOR_CLIENT", "Precision executor client"),
    QUALITY_SUPERVISOR_CLIENT("QUALITY_SUPERVISOR_CLIENT", "Quality supervisor client"),
    RESPONSE_ASSISTANT("RESPONSE_ASSISTANT", "Response assistant");

    private String code;
    private String info;
}

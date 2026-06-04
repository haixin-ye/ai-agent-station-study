package yhx.com.domain.agent.model.valobj.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeFunctionSpecVO {

    private String name;
    private String description;
    private Map<String, Object> parameterSchema;
    private Boolean strict;
    private Map<String, Object> metadata;
}

package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;

/** Classifies failures that escape the normal Runtime lifecycle. */
public class UnexpectedRuntimeFailureClassifier {

    public RuntimeFailureCodeEnumVO classify(Throwable error) {
        if (error instanceof OutOfMemoryError) {
            return RuntimeFailureCodeEnumVO.BACKEND_OUT_OF_MEMORY;
        }
        return RuntimeFailureCodeEnumVO.UNEXPECTED_RUNTIME_ERROR;
    }
}

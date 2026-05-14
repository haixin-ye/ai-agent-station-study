package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;

public class ContextPreparationService {

    private final ContextCandidatePreselector contextCandidatePreselector;

    public ContextPreparationService(ContextCandidatePreselector contextCandidatePreselector) {
        this.contextCandidatePreselector = contextCandidatePreselector;
    }

    public ContextCandidateBundleVO prepare(ContextPreparationCommand command) {
        return contextCandidatePreselector.buildCandidates(command);
    }
}

package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.valobj.artifact.ArtifactUpdateCommandVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ArtifactUpdateModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.artifact.ArtifactManager;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.FinalDeliveryPort;

import java.util.List;
import java.util.Map;

public class UpdateArtifactActionHandler extends FinalActionHandler {

    private final ArtifactManager artifactManager;
    private final RunEventPublisher eventPublisher;

    public UpdateArtifactActionHandler(ArtifactManager artifactManager,
                                       FinalDeliveryPort finalDeliveryPort,
                                       RuntimeFailureFactory failureFactory,
                                       DeveloperTraceRecorder traceRecorder) {
        this(artifactManager, finalDeliveryPort, failureFactory, traceRecorder, null);
    }

    public UpdateArtifactActionHandler(ArtifactManager artifactManager,
                                       FinalDeliveryPort finalDeliveryPort,
                                       RuntimeFailureFactory failureFactory,
                                       DeveloperTraceRecorder traceRecorder,
                                       RunEventPublisher eventPublisher) {
        super(finalDeliveryPort, failureFactory, traceRecorder);
        this.artifactManager = artifactManager;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.UPDATE_ARTIFACT;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        try {
            Map<String, Object> patch = requireArtifactPatch(action);
            String artifactId = stringValue(patch, "artifactId");
            String updateMode = stringValue(patch, "updateMode");
            String content = stringValue(patch, "content");
            if (isBlank(artifactId)) {
                throw new IllegalArgumentException("artifactPatch.artifactId is required.");
            }
            if (ArtifactUpdateModeEnumVO.ofCode(updateMode).isEmpty()) {
                throw new IllegalArgumentException("artifactPatch.updateMode is invalid.");
            }
            if (isBlank(content)) {
                throw new IllegalArgumentException("artifactPatch.content is required.");
            }
            AgentArtifactEntity artifact = artifactManager.updateArtifact(ArtifactUpdateCommandVO.builder()
                    .artifactId(artifactId)
                    .title(stringValue(patch, "title"))
                    .summary(stringValue(patch, "summary"))
                    .content(content)
                    .updateMode(updateMode)
                    .build());
            if (eventPublisher != null) {
                eventPublisher.phase(context.getRunId(), "ARTIFACT_UPDATED", "Artifact updated: " + artifact.getArtifactId());
            }
            FinalAnswerCandidateVO candidate = optionalFinalAnswerCandidate(action);
            if (candidate != null) {
                MainActionHandlerResult delivered = routeDelivery(context, actionType(), candidate);
                delivered.setCreatedArtifactIds(List.of(artifact.getArtifactId()));
                return delivered;
            }
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                    .nextPhase(RuntimePhaseEnumVO.PREPARING_CONTEXT)
                    .createdArtifactIds(List.of(artifact.getArtifactId()))
                    .message("Artifact updated.")
                    .build();
        } catch (Exception e) {
            return validationFailure(context, e.getMessage());
        }
    }
}

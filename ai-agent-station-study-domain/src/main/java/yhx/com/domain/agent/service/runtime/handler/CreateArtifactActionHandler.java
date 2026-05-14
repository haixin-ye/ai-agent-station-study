package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.valobj.artifact.ArtifactCreateCommandVO;
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

public class CreateArtifactActionHandler extends FinalActionHandler {

    private final ArtifactManager artifactManager;
    private final RunEventPublisher eventPublisher;

    public CreateArtifactActionHandler(ArtifactManager artifactManager,
                                       FinalDeliveryPort finalDeliveryPort,
                                       RuntimeFailureFactory failureFactory,
                                       DeveloperTraceRecorder traceRecorder) {
        this(artifactManager, finalDeliveryPort, failureFactory, traceRecorder, null);
    }

    public CreateArtifactActionHandler(ArtifactManager artifactManager,
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
        return MainAgentActionTypeEnumVO.CREATE_ARTIFACT;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        try {
            Map<String, Object> draft = requireArtifactDraft(action);
            String artifactType = stringValue(draft, "artifactType");
            String title = stringValue(draft, "title");
            String content = stringValue(draft, "content");
            if (isBlank(artifactType) || isBlank(title) || isBlank(content)) {
                throw new IllegalArgumentException("artifactDraft.artifactType, title, and content are required.");
            }
            AgentArtifactEntity artifact = artifactManager.createArtifact(ArtifactCreateCommandVO.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .artifactType(artifactType)
                    .title(title)
                    .summary(stringValue(draft, "summary"))
                    .content(content)
                    .build());
            if (eventPublisher != null) {
                eventPublisher.phase(context.getRunId(), "ARTIFACT_CREATED", "Artifact created: " + title);
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
                    .message("Artifact created.")
                    .build();
        } catch (Exception e) {
            return validationFailure(context, e.getMessage());
        }
    }
}

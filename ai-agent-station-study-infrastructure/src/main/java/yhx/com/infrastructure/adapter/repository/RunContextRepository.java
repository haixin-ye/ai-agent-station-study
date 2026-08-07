package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IRunContextRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentRunContextEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunLoopEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.infrastructure.dao.IAgentRunContextDao;
import yhx.com.infrastructure.dao.IAgentRunLoopDao;
import yhx.com.infrastructure.dao.po.AgentRunContextPO;
import yhx.com.infrastructure.dao.po.AgentRunLoopPO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RunContextRepository implements IRunContextRepository {

    @Resource
    private IAgentRunContextDao contextDao;
    @Resource
    private IAgentRunLoopDao loopDao;

    @Override
    public void createContext(AgentRunContextEntity context) {
        LocalDateTime now = LocalDateTime.now();
        if (context.getCreatedAt() == null) {
            context.setCreatedAt(now);
        }
        if (context.getUpdatedAt() == null) {
            context.setUpdatedAt(now);
        }
        contextDao.insert(toPO(context));
    }

    @Override
    public boolean updateContext(AgentRunContextEntity context, long expectedVersion) {
        context.setUpdatedAt(LocalDateTime.now());
        return contextDao.updateWithVersion(toPO(context), expectedVersion) == 1;
    }

    @Override
    public Optional<AgentRunContextEntity> findContext(String runId) {
        return Optional.ofNullable(contextDao.queryByRunId(runId)).map(this::toEntity);
    }

    @Override
    public void saveLoop(AgentRunLoopEntity loop) {
        loopDao.upsert(toPO(loop));
    }

    @Override
    public Optional<AgentRunLoopEntity> findLoop(String runId, Integer loopIndex) {
        return Optional.ofNullable(loopDao.query(runId, loopIndex)).map(this::toEntity);
    }

    @Override
    public List<AgentRunLoopEntity> listLoops(String runId) {
        List<AgentRunLoopPO> loops = loopDao.listByRunId(runId);
        return loops == null ? List.of() : loops.stream().map(this::toEntity).toList();
    }

    private AgentRunContextPO toPO(AgentRunContextEntity value) {
        return AgentRunContextPO.builder()
                .runId(value.getRunId())
                .schemaVersion(value.getSchemaVersion())
                .mainAgentStage(value.getMainAgentStage().name())
                .baseContextRef(value.getBaseContextRef())
                .taskLedgerRef(value.getTaskLedgerRef())
                .runtimeControlRef(value.getRuntimeControlRef())
                .contextVersion(value.getContextVersion())
                .createdAt(value.getCreatedAt())
                .updatedAt(value.getUpdatedAt())
                .build();
    }

    private AgentRunContextEntity toEntity(AgentRunContextPO value) {
        return AgentRunContextEntity.builder()
                .runId(value.getRunId())
                .schemaVersion(value.getSchemaVersion())
                .mainAgentStage(MainAgentStageEnumVO.valueOf(value.getMainAgentStage()))
                .baseContextRef(value.getBaseContextRef())
                .taskLedgerRef(value.getTaskLedgerRef())
                .runtimeControlRef(value.getRuntimeControlRef())
                .contextVersion(value.getContextVersion())
                .createdAt(value.getCreatedAt())
                .updatedAt(value.getUpdatedAt())
                .build();
    }

    private AgentRunLoopPO toPO(AgentRunLoopEntity value) {
        return AgentRunLoopPO.builder()
                .runId(value.getRunId())
                .loopIndex(value.getLoopIndex())
                .mainAgentStage(value.getMainAgentStage().name())
                .status(value.getStatus())
                .recordRef(value.getRecordRef())
                .recordVersion(value.getRecordVersion())
                .startedAt(value.getStartedAt())
                .completedAt(value.getCompletedAt())
                .build();
    }

    private AgentRunLoopEntity toEntity(AgentRunLoopPO value) {
        return AgentRunLoopEntity.builder()
                .runId(value.getRunId())
                .loopIndex(value.getLoopIndex())
                .mainAgentStage(MainAgentStageEnumVO.valueOf(value.getMainAgentStage()))
                .status(value.getStatus())
                .recordRef(value.getRecordRef())
                .recordVersion(value.getRecordVersion())
                .startedAt(value.getStartedAt())
                .completedAt(value.getCompletedAt())
                .build();
    }
}

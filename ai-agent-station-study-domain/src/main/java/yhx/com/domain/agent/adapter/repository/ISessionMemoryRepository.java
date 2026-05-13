package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.SessionMemoryEntity;

import java.util.List;

public interface ISessionMemoryRepository {

    List<SessionMemoryEntity> queryLatestBySessionId(String sessionId, int limit);

    Integer queryMaxRoundNo(String sessionId);

    void save(SessionMemoryEntity sessionMemoryEntity);
}

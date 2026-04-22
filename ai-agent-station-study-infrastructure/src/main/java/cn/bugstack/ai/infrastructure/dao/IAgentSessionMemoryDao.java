package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AgentSessionMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IAgentSessionMemoryDao {

    int insert(AgentSessionMemory agentSessionMemory);

    Integer queryMaxRoundNo(@Param("sessionId") String sessionId);

    List<AgentSessionMemory> queryLatestBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);
}

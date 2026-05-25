package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentMemoryTaskPO;

import java.util.List;

@Mapper
public interface IAgentMemoryTaskDao {

    int insert(AgentMemoryTaskPO task);

    AgentMemoryTaskPO queryByTaskId(@Param("taskId") String taskId);

    int countOpenTask(@Param("taskType") String taskType, @Param("sessionId") String sessionId);

    List<AgentMemoryTaskPO> listRetryableFailedTasks(@Param("maxAttempts") int maxAttempts, @Param("limit") int limit);

    int updateRunning(@Param("taskId") String taskId);

    int updateSucceeded(@Param("taskId") String taskId, @Param("outputRef") String outputRef);

    int updateFailed(@Param("taskId") String taskId, @Param("failureCode") String failureCode, @Param("failureMessage") String failureMessage);
}

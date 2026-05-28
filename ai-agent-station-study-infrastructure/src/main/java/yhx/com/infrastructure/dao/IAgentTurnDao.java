package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import yhx.com.infrastructure.dao.po.AgentTurnPO;

import java.util.List;

@Mapper
public interface IAgentTurnDao {

    int insert(AgentTurnPO turn);

    Long nextTurnNo(@Param("sessionId") String sessionId);

    AgentTurnPO queryByTurnId(@Param("turnId") String turnId);

    AgentTurnPO queryByRunId(@Param("runId") String runId);

    List<AgentTurnPO> listRecentCompleted(@Param("sessionId") String sessionId, @Param("limit") int limit);

    @Select("""
            SELECT turn_id AS turnId,
                   session_id AS sessionId,
                   run_id AS runId,
                   user_id AS userId,
                   agent_id AS agentId,
                   turn_no AS turnNo,
                   user_message_id AS userMessageId,
                   assistant_message_id AS assistantMessageId,
                   user_payload_ref AS userPayloadRef,
                   assistant_payload_ref AS assistantPayloadRef,
                   status,
                   started_at AS startedAt,
                   completed_at AS completedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM agent_turn
            WHERE status = 'COMPLETED'
            ORDER BY completed_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<AgentTurnPO> listRecentCompletedGlobal(@Param("limit") int limit);

    List<AgentTurnPO> listCompletedBefore(@Param("sessionId") String sessionId, @Param("beforeTurnNo") Long beforeTurnNo, @Param("limit") int limit);
}

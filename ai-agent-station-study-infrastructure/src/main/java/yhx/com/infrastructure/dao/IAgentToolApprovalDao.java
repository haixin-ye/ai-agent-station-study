package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentToolApprovalPO;

import java.time.LocalDateTime;

@Mapper
public interface IAgentToolApprovalDao {
    int insert(AgentToolApprovalPO approval);
    AgentToolApprovalPO queryPendingByRunId(String runId);
    AgentToolApprovalPO queryByApprovalKey(String approvalKey);
    int markDecision(@Param("approvalId") String approvalId, @Param("status") String status, @Param("userAnswerRef") String userAnswerRef, @Param("decidedAt") LocalDateTime decidedAt);
}

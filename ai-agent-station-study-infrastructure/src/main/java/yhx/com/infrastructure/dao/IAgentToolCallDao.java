package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentToolCallPO;

@Mapper
public interface IAgentToolCallDao {
    int insert(AgentToolCallPO toolCall);
    int updateStatus(@Param("toolCallId") String toolCallId, @Param("status") String status);
    int saveReceipt(@Param("toolCallId") String toolCallId, @Param("argumentsRef") String argumentsRef, @Param("receiptRef") String receiptRef);
}

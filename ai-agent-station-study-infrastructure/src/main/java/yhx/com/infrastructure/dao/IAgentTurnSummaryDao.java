package yhx.com.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yhx.com.infrastructure.dao.po.AgentTurnSummaryPO;

import java.util.List;

@Mapper
public interface IAgentTurnSummaryDao {

    int insert(AgentTurnSummaryPO summary);

    AgentTurnSummaryPO queryByTurnId(@Param("turnId") String turnId);

    List<AgentTurnSummaryPO> listByTurnIds(@Param("turnIds") List<String> turnIds);
}

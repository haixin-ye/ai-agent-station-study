package yhx.com.infrastructure.adapter.repository;

import yhx.com.domain.agent.adapter.repository.IAgentRepository;
import yhx.com.domain.agent.model.valobj.armory.AiAgentClientFlowConfigVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientAdvisorVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientApiVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientModelVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientSystemPromptVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientToolMcpVO;
import yhx.com.domain.agent.model.valobj.armory.AiClientVO;
import yhx.com.infrastructure.dao.*;
import yhx.com.infrastructure.dao.po.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

import static yhx.com.domain.agent.model.valobj.enums.armory.AiAgentEnumVO.*;

/**
 * Agent repository adapter.
 *
 * @author yhx
 */
@Slf4j
@Repository
public class AgentRepository implements IAgentRepository {

    @Resource
    private IAiAgentDao aiAgentDao;

    @Resource
    private IAiAgentFlowConfigDao aiAgentFlowConfigDao;

    @Resource
    private IAiAgentTaskScheduleDao aiAgentTaskScheduleDao;

    @Resource
    private IAiClientAdvisorDao aiClientAdvisorDao;

    @Resource
    private IAiClientApiDao aiClientApiDao;

    @Resource
    private IAiClientConfigDao aiClientConfigDao;

    @Resource
    private IAiClientDao aiClientDao;

    @Resource
    private IAiClientModelDao aiClientModelDao;

    @Resource
    private IAiClientRagOrderDao aiClientRagOrderDao;

    @Resource
    private IAiClientSystemPromptDao aiClientSystemPromptDao;

    @Resource
    private IAiClientToolMcpDao aiClientToolMcpDao;

    @Override
    public List<AiClientApiVO> queryAiClientApiVOListByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientApiVO> result = new ArrayList<>();

        for (String clientId : clientIdList) {
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String modelId = config.getTargetId();
                    AiClientModel model = aiClientModelDao.queryByModelId(modelId);
                    if (model != null && model.getStatus() == 1) {
                        AiClientApi apiConfig = aiClientApiDao.queryByApiId(model.getApiId());
                        addApiVOIfActiveAndAbsent(result, apiConfig);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public List<AiClientModelVO> AiClientModelVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientModelVO> result = new ArrayList<>();

        for (String clientId : clientIdList) {
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String modelId = config.getTargetId();
                    AiClientModel model = aiClientModelDao.queryByModelId(modelId);
                    if (model != null && model.getStatus() == 1) {
                        List<String> toolMcpIds = queryModelToolMcpIds(modelId);
                        AiClientModelVO modelVO = AiClientModelVO.builder()
                                .modelId(model.getModelId())
                                .apiId(model.getApiId())
                                .modelName(model.getModelName())
                                .modelType(model.getModelType())
                                .toolMcpIds(toolMcpIds)
                                .build();

                        if (result.stream().noneMatch(vo -> vo.getModelId().equals(modelVO.getModelId()))) {
                            result.add(modelVO);
                        }
                    }
                }
            }
        }

        return result;
    }

    @Override
    public List<AiClientToolMcpVO> AiClientToolMcpVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientToolMcpVO> result = new ArrayList<>();
        Set<String> processedMcpIds = new HashSet<>();

        for (String clientId : clientIdList) {
            List<AiClientConfig> clientConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);
            loadDirectClientMcp(result, processedMcpIds, clientConfigs);
            loadModelMcp(result, processedMcpIds, clientConfigs);
        }

        return result;
    }

    @Override
    public List<AiClientSystemPromptVO> AiClientSystemPromptVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientSystemPromptVO> result = new ArrayList<>();
        Set<String> processedPromptIds = new HashSet<>();

        for (String clientId : clientIdList) {
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if ("prompt".equals(config.getTargetType()) && config.getStatus() == 1) {
                    String promptId = config.getTargetId();
                    if (!processedPromptIds.add(promptId)) {
                        continue;
                    }

                    AiClientSystemPrompt systemPrompt = aiClientSystemPromptDao.queryByPromptId(promptId);
                    if (systemPrompt != null && systemPrompt.getStatus() == 1) {
                        result.add(AiClientSystemPromptVO.builder()
                                .promptId(systemPrompt.getPromptId())
                                .promptName(systemPrompt.getPromptName())
                                .promptContent(systemPrompt.getPromptContent())
                                .description(systemPrompt.getDescription())
                                .build());
                    }
                }
            }
        }

        return result;
    }

    @Override
    public Map<String, AiClientSystemPromptVO> queryAiClientSystemPromptMapByClientIds(List<String> clientIdList) {
        List<AiClientSystemPromptVO> aiClientSystemPrompts = AiClientSystemPromptVOByClientIds(clientIdList);

        if (aiClientSystemPrompts == null || aiClientSystemPrompts.isEmpty()) {
            return Collections.emptyMap();
        }

        return aiClientSystemPrompts.stream()
                .map(prompt -> AiClientSystemPromptVO.builder()
                        .promptId(prompt.getPromptId())
                        .promptContent(prompt.getPromptContent())
                        .build())
                .collect(Collectors.toMap(
                        AiClientSystemPromptVO::getPromptId,
                        prompt -> prompt,
                        (existing, replacement) -> existing
                ));
    }

    @Override
    public List<AiClientAdvisorVO> AiClientAdvisorVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientAdvisorVO> result = new ArrayList<>();
        Set<String> processedAdvisorIds = new HashSet<>();

        for (String clientId : clientIdList) {
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId("client", clientId);

            for (AiClientConfig config : configs) {
                if (config.getStatus() != 1 || !"advisor".equals(config.getTargetType())) {
                    continue;
                }

                String advisorId = config.getTargetId();
                if (!processedAdvisorIds.add(advisorId)) {
                    continue;
                }

                AiClientAdvisor aiClientAdvisor = aiClientAdvisorDao.queryByAdvisorId(advisorId);
                if (aiClientAdvisor == null || aiClientAdvisor.getStatus() != 1) {
                    continue;
                }

                result.add(buildAdvisorVO(aiClientAdvisor));
            }
        }

        return result;
    }

    @Override
    public List<AiClientVO> AiClientVOByClientIds(List<String> clientIdList) {
        if (clientIdList == null || clientIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientVO> result = new ArrayList<>();
        Set<String> processedClientIds = new HashSet<>();

        for (String clientId : clientIdList) {
            if (!processedClientIds.add(clientId)) {
                continue;
            }

            AiClient aiClient = aiClientDao.queryByClientId(clientId);
            if (aiClient == null || aiClient.getStatus() != 1) {
                continue;
            }

            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId("client", clientId);

            String modelId = null;
            List<String> promptIdList = new ArrayList<>();
            List<String> mcpIdList = new ArrayList<>();
            List<String> advisorIdList = new ArrayList<>();

            for (AiClientConfig config : configs) {
                if (config.getStatus() != 1) {
                    continue;
                }

                switch (config.getTargetType()) {
                    case "model":
                        modelId = config.getTargetId();
                        break;
                    case "prompt":
                        promptIdList.add(config.getTargetId());
                        break;
                    case "tool_mcp":
                        mcpIdList.add(config.getTargetId());
                        break;
                    case "advisor":
                        advisorIdList.add(config.getTargetId());
                        break;
                    default:
                        break;
                }
            }

            result.add(AiClientVO.builder()
                    .clientId(aiClient.getClientId())
                    .clientName(aiClient.getClientName())
                    .description(aiClient.getDescription())
                    .modelId(modelId)
                    .promptIdList(promptIdList)
                    .mcpIdList(mcpIdList)
                    .advisorIdList(advisorIdList)
                    .build());
        }

        return result;
    }

    @Override
    public List<AiClientApiVO> queryAiClientApiVOListByModelIds(List<String> modelIdList) {
        if (modelIdList == null || modelIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientApiVO> result = new ArrayList<>();

        for (String modelId : modelIdList) {
            AiClientModel model = aiClientModelDao.queryByModelId(modelId);
            if (model != null && model.getStatus() == 1) {
                AiClientApi apiConfig = aiClientApiDao.queryByApiId(model.getApiId());
                addApiVOIfActiveAndAbsent(result, apiConfig);
            }
        }

        return result;
    }

    @Override
    public List<AiClientModelVO> AiClientModelVOByModelIds(List<String> modelIdList) {
        if (modelIdList == null || modelIdList.isEmpty()) {
            return List.of();
        }

        List<AiClientModelVO> result = new ArrayList<>();

        for (String modelId : modelIdList) {
            AiClientModel model = aiClientModelDao.queryByModelId(modelId);
            if (model != null && model.getStatus() == 1) {
                AiClientModelVO modelVO = AiClientModelVO.builder()
                        .modelId(model.getModelId())
                        .apiId(model.getApiId())
                        .modelName(model.getModelName())
                        .modelType(model.getModelType())
                        .build();

                if (result.stream().noneMatch(vo -> vo.getModelId().equals(modelVO.getModelId()))) {
                    result.add(modelVO);
                }
            }
        }

        return result;
    }

    @Override
    public Map<String, AiAgentClientFlowConfigVO> queryAiAgentClientFlowConfig(String aiAgentId) {
        if (aiAgentId == null || aiAgentId.trim().isEmpty()) {
            return Map.of();
        }

        try {
            List<AiAgentFlowConfig> flowConfigs = aiAgentFlowConfigDao.queryByAgentId(aiAgentId);
            if (flowConfigs == null || flowConfigs.isEmpty()) {
                return Map.of();
            }

            Map<String, AiAgentClientFlowConfigVO> result = new HashMap<>();
            for (AiAgentFlowConfig flowConfig : flowConfigs) {
                AiAgentClientFlowConfigVO configVO = AiAgentClientFlowConfigVO.builder()
                        .clientId(flowConfig.getClientId())
                        .clientName(flowConfig.getClientName())
                        .clientType(flowConfig.getClientType())
                        .sequence(flowConfig.getSequence())
                        .build();

                result.put(flowConfig.getClientType(), configVO);
            }

            return result;
        } catch (NumberFormatException e) {
            log.error("Invalid aiAgentId format: {}", aiAgentId, e);
            return Map.of();
        } catch (Exception e) {
            log.error("Query ai agent client flow config failed, aiAgentId: {}", aiAgentId, e);
            return Map.of();
        }
    }

    private void addApiVOIfActiveAndAbsent(List<AiClientApiVO> result, AiClientApi apiConfig) {
        if (apiConfig == null || apiConfig.getStatus() != 1) {
            return;
        }

        AiClientApiVO apiVO = AiClientApiVO.builder()
                .apiId(apiConfig.getApiId())
                .baseUrl(apiConfig.getBaseUrl())
                .apiKey(apiConfig.getApiKey())
                .completionsPath(apiConfig.getCompletionsPath())
                .embeddingsPath(apiConfig.getEmbeddingsPath())
                .build();

        if (result.stream().noneMatch(vo -> vo.getApiId().equals(apiVO.getApiId()))) {
            result.add(apiVO);
        }
    }

    private List<String> queryModelToolMcpIds(String modelId) {
        List<AiClientConfig> toolMcpConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
        List<String> toolMcpIds = new ArrayList<>();
        for (AiClientConfig toolMcpConfig : toolMcpConfigs) {
            if (AI_CLIENT_TOOL_MCP.getCode().equals(toolMcpConfig.getTargetType()) && toolMcpConfig.getStatus() == 1) {
                toolMcpIds.add(toolMcpConfig.getTargetId());
            }
        }
        return toolMcpIds;
    }

    private void loadDirectClientMcp(List<AiClientToolMcpVO> result,
                                     Set<String> processedMcpIds,
                                     List<AiClientConfig> clientConfigs) {
        for (AiClientConfig clientConfig : clientConfigs) {
            if (AI_CLIENT_TOOL_MCP.getCode().equals(clientConfig.getTargetType()) && clientConfig.getStatus() == 1) {
                addMcpVOIfActiveAndAbsent(result, processedMcpIds, clientConfig.getTargetId());
            }
        }
    }

    private void loadModelMcp(List<AiClientToolMcpVO> result,
                              Set<String> processedMcpIds,
                              List<AiClientConfig> clientConfigs) {
        for (AiClientConfig clientConfig : clientConfigs) {
            if (AI_CLIENT_MODEL.getCode().equals(clientConfig.getTargetType()) && clientConfig.getStatus() == 1) {
                String modelId = clientConfig.getTargetId();
                List<AiClientConfig> modelConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);

                for (AiClientConfig modelConfig : modelConfigs) {
                    if (AI_CLIENT_TOOL_MCP.getCode().equals(modelConfig.getTargetType()) && modelConfig.getStatus() == 1) {
                        addMcpVOIfActiveAndAbsent(result, processedMcpIds, modelConfig.getTargetId());
                    }
                }
            }
        }
    }

    private void addMcpVOIfActiveAndAbsent(List<AiClientToolMcpVO> result,
                                           Set<String> processedMcpIds,
                                           String mcpId) {
        if (!processedMcpIds.add(mcpId)) {
            return;
        }

        AiClientToolMcp toolMcp = aiClientToolMcpDao.queryByMcpId(mcpId);
        if (toolMcp == null || toolMcp.getStatus() != 1) {
            return;
        }

        AiClientToolMcpVO mcpVO = AiClientToolMcpVO.builder()
                .mcpId(toolMcp.getMcpId())
                .mcpName(toolMcp.getMcpName())
                .transportType(toolMcp.getTransportType())
                .transportConfig(toolMcp.getTransportConfig())
                .requestTimeout(toolMcp.getRequestTimeout())
                .build();

        parseTransportConfig(mcpVO, toolMcp.getTransportConfig(), toolMcp.getTransportType());
        result.add(mcpVO);
    }

    private void parseTransportConfig(AiClientToolMcpVO mcpVO, String transportConfig, String transportType) {
        try {
            JSONObject transportConfigJson = JSON.parseObject(transportConfig);
            AiClientToolMcpVO.ToolPolicy toolPolicy = transportConfigJson == null
                    ? null
                    : transportConfigJson.getObject("policy", AiClientToolMcpVO.ToolPolicy.class);
            mcpVO.setToolPolicy(toolPolicy);

            if (transportConfigJson != null) {
                transportConfigJson.remove("policy");
            }

            String transportConfigWithoutPolicy = transportConfigJson == null ? "{}" : transportConfigJson.toJSONString();
            if ("sse".equals(transportType)) {
                AiClientToolMcpVO.TransportConfigSse transportConfigSse = JSON.parseObject(
                        transportConfigWithoutPolicy,
                        AiClientToolMcpVO.TransportConfigSse.class);
                mcpVO.setTransportConfigSse(transportConfigSse);
            } else if ("stdio".equals(transportType)) {
                Map<String, AiClientToolMcpVO.TransportConfigStdio.Stdio> stdio = JSON.parseObject(
                        transportConfigWithoutPolicy,
                        new TypeReference<>() {
                        });

                AiClientToolMcpVO.TransportConfigStdio transportConfigStdio = new AiClientToolMcpVO.TransportConfigStdio();
                transportConfigStdio.setStdio(stdio);
                mcpVO.setTransportConfigStdio(transportConfigStdio);
            }
        } catch (Exception e) {
            log.error("Parse transport configuration failed: {}", e.getMessage(), e);
        }
    }

    private AiClientAdvisorVO buildAdvisorVO(AiClientAdvisor aiClientAdvisor) {
        AiClientAdvisorVO.ChatMemory chatMemory = null;
        AiClientAdvisorVO.RagAnswer ragAnswer = null;
        AiClientAdvisorVO.PromptInjectionSanitizer promptInjectionSanitizer = null;

        String extParam = aiClientAdvisor.getExtParam();
        if (extParam != null && !extParam.trim().isEmpty()) {
            try {
                if ("ChatMemory".equals(aiClientAdvisor.getAdvisorType())) {
                    chatMemory = JSON.parseObject(extParam, AiClientAdvisorVO.ChatMemory.class);
                } else if ("RagAnswer".equals(aiClientAdvisor.getAdvisorType())) {
                    ragAnswer = JSON.parseObject(extParam, AiClientAdvisorVO.RagAnswer.class);
                } else if ("PromptInjectionSanitizer".equals(aiClientAdvisor.getAdvisorType())) {
                    promptInjectionSanitizer = JSON.parseObject(extParam, AiClientAdvisorVO.PromptInjectionSanitizer.class);
                }
            } catch (Exception ignored) {
                // Keep default null extension config when ext_param cannot be parsed.
            }
        }

        return AiClientAdvisorVO.builder()
                .advisorId(aiClientAdvisor.getAdvisorId())
                .advisorName(aiClientAdvisor.getAdvisorName())
                .advisorType(aiClientAdvisor.getAdvisorType())
                .orderNum(aiClientAdvisor.getOrderNum())
                .chatMemory(chatMemory)
                .ragAnswer(ragAnswer)
                .promptInjectionSanitizer(promptInjectionSanitizer)
                .build();
    }

}

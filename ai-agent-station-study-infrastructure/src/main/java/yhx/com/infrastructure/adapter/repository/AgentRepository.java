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
 * AiAgent 浠撳偍鏈嶅姟
 *
 * @author yhx
 * 2025/6/28 18:09
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
            // 1. 閫氳繃clientId鏌ヨ鍏宠仈鐨刴odelId
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String modelId = config.getTargetId();

                    // 2. 閫氳繃modelId鏌ヨ妯″瀷閰嶇疆锛岃幏鍙朼piId
                    AiClientModel model = aiClientModelDao.queryByModelId(modelId);
                    if (model != null && model.getStatus() == 1) {
                        String apiId = model.getApiId();

                        // 3. 閫氳繃apiId鏌ヨAPI閰嶇疆淇℃伅
                        AiClientApi apiConfig = aiClientApiDao.queryByApiId(apiId);
                        if (apiConfig != null && apiConfig.getStatus() == 1) {
                            // 4. 杞崲涓篤O瀵硅薄
                            AiClientApiVO apiVO = AiClientApiVO.builder()
                                    .apiId(apiConfig.getApiId())
                                    .baseUrl(apiConfig.getBaseUrl())
                                    .apiKey(apiConfig.getApiKey())
                                    .completionsPath(apiConfig.getCompletionsPath())
                                    .embeddingsPath(apiConfig.getEmbeddingsPath())
                                    .build();

                            // 閬垮厤閲嶅娣诲姞鐩稿悓鐨凙PI閰嶇疆
                            if (result.stream().noneMatch(vo -> vo.getApiId().equals(apiVO.getApiId()))) {
                                result.add(apiVO);
                            }
                        }
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
            // 1. 閫氳繃clientId鏌ヨ鍏宠仈鐨刴odelId
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if (AI_CLIENT_MODEL.getCode().equals(config.getTargetType()) && config.getStatus() == 1) {
                    String modelId = config.getTargetId();

                    // 2. 閫氳繃modelId鏌ヨ妯″瀷閰嶇疆
                    AiClientModel model = aiClientModelDao.queryByModelId(modelId);
                    if (model != null && model.getStatus() == 1) {

                        // 3. 鏌ヨ璇ユā鍨嬪叧鑱旂殑tool_mcp閰嶇疆
                        List<AiClientConfig> toolMcpConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);
                        List<String> toolMcpIds = new ArrayList<>();

                        for (AiClientConfig toolMcpConfig : toolMcpConfigs) {
                            if (AI_CLIENT_TOOL_MCP.getCode().equals(toolMcpConfig.getTargetType()) && toolMcpConfig.getStatus() == 1) {
                                toolMcpIds.add(toolMcpConfig.getTargetId());
                            }
                        }

                        // 4. 杞崲涓篤O瀵硅薄
                        AiClientModelVO modelVO = AiClientModelVO.builder()
                                .modelId(model.getModelId())
                                .apiId(model.getApiId())
                                .modelName(model.getModelName())
                                .modelType(model.getModelType())
                                .toolMcpIds(toolMcpIds)
                                .build();

                        // 閬垮厤閲嶅娣诲姞鐩稿悓鐨勬ā鍨嬮厤缃?
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
            // 1. 閫氳繃clientId鏌ヨ鍏宠仈鐨刴odel閰嶇疆
            List<AiClientConfig> clientConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            // 鍏堝姞杞?client -> tool_mcp锛坈lient 缁村害鎸傝浇锛?
            for (AiClientConfig clientConfig : clientConfigs) {
                if (AI_CLIENT_TOOL_MCP.getCode().equals(clientConfig.getTargetType()) && clientConfig.getStatus() == 1) {
                    String mcpId = clientConfig.getTargetId();

                    if (processedMcpIds.contains(mcpId)) {
                        continue;
                    }
                    processedMcpIds.add(mcpId);

                    AiClientToolMcp toolMcp = aiClientToolMcpDao.queryByMcpId(mcpId);
                    if (toolMcp != null && toolMcp.getStatus() == 1) {
                        AiClientToolMcpVO mcpVO = AiClientToolMcpVO.builder()
                                .mcpId(toolMcp.getMcpId())
                                .mcpName(toolMcp.getMcpName())
                                .transportType(toolMcp.getTransportType())
                                .transportConfig(toolMcp.getTransportConfig())
                                .requestTimeout(toolMcp.getRequestTimeout())
                                .build();

                        String transportConfig = toolMcp.getTransportConfig();
                        String transportType = toolMcp.getTransportType();

                        try {
                            JSONObject transportConfigJson = JSON.parseObject(transportConfig);
                            AiClientToolMcpVO.ToolPolicy toolPolicy = transportConfigJson == null
                                    ? null
                                    : transportConfigJson.getObject("policy", AiClientToolMcpVO.ToolPolicy.class);
                            mcpVO.setToolPolicy(toolPolicy);
                            if ("sse".equals(transportType)) {
                                if (transportConfigJson != null) {
                                    transportConfigJson.remove("policy");
                                }
                                AiClientToolMcpVO.TransportConfigSse transportConfigSse = JSON.parseObject(
                                        transportConfigJson == null ? "{}" : transportConfigJson.toJSONString(),
                                        AiClientToolMcpVO.TransportConfigSse.class);
                                mcpVO.setTransportConfigSse(transportConfigSse);
                            } else if ("stdio".equals(transportType)) {
                                if (transportConfigJson != null) {
                                    transportConfigJson.remove("policy");
                                }
                                Map<String, AiClientToolMcpVO.TransportConfigStdio.Stdio> stdio = JSON.parseObject(
                                        transportConfigJson == null ? "{}" : transportConfigJson.toJSONString(),
                                        new TypeReference<>() {
                                        });

                                AiClientToolMcpVO.TransportConfigStdio transportConfigStdio = new AiClientToolMcpVO.TransportConfigStdio();
                                transportConfigStdio.setStdio(stdio);
                                mcpVO.setTransportConfigStdio(transportConfigStdio);
                            }
                        } catch (Exception e) {
                            log.error("瑙ｆ瀽浼犺緭閰嶇疆澶辫触: {}", e.getMessage(), e);
                        }
                        result.add(mcpVO);
                    }
                }
            }

            for (AiClientConfig clientConfig : clientConfigs) {
                if (AI_CLIENT_MODEL.getCode().equals(clientConfig.getTargetType()) && clientConfig.getStatus() == 1) {
                    String modelId = clientConfig.getTargetId();

                    // 2. 閫氳繃modelId鏌ヨ鍏宠仈鐨則ool_mcp閰嶇疆
                    List<AiClientConfig> modelConfigs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT_MODEL.getCode(), modelId);

                    for (AiClientConfig modelConfig : modelConfigs) {
                        if (AI_CLIENT_TOOL_MCP.getCode().equals(modelConfig.getTargetType()) && modelConfig.getStatus() == 1) {
                            String mcpId = modelConfig.getTargetId();

                            // 閬垮厤閲嶅澶勭悊鐩稿悓鐨刴cpId
                            if (processedMcpIds.contains(mcpId)) {
                                continue;
                            }
                            processedMcpIds.add(mcpId);

                            // 3. 閫氳繃mcpId鏌ヨai_client_tool_mcp琛ㄨ幏鍙朚CP宸ュ叿閰嶇疆
                            AiClientToolMcp toolMcp = aiClientToolMcpDao.queryByMcpId(mcpId);
                            if (toolMcp != null && toolMcp.getStatus() == 1) {
                                // 4. 杞崲涓篤O瀵硅薄
                                AiClientToolMcpVO mcpVO = AiClientToolMcpVO.builder()
                                        .mcpId(toolMcp.getMcpId())
                                        .mcpName(toolMcp.getMcpName())
                                        .transportType(toolMcp.getTransportType())
                                        .transportConfig(toolMcp.getTransportConfig())
                                        .requestTimeout(toolMcp.getRequestTimeout())
                                        .build();

                                String transportConfig = toolMcp.getTransportConfig();
                                String transportType = toolMcp.getTransportType();

                                try {
                                    JSONObject transportConfigJson = JSON.parseObject(transportConfig);
                                    AiClientToolMcpVO.ToolPolicy toolPolicy = transportConfigJson == null
                                            ? null
                                            : transportConfigJson.getObject("policy", AiClientToolMcpVO.ToolPolicy.class);
                                    mcpVO.setToolPolicy(toolPolicy);
                                    if ("sse".equals(transportType)) {
                                        // 瑙ｆ瀽SSE閰嶇疆
                                        if (transportConfigJson != null) {
                                            transportConfigJson.remove("policy");
                                        }
                                        AiClientToolMcpVO.TransportConfigSse transportConfigSse = JSON.parseObject(
                                                transportConfigJson == null ? "{}" : transportConfigJson.toJSONString(),
                                                AiClientToolMcpVO.TransportConfigSse.class);
                                        mcpVO.setTransportConfigSse(transportConfigSse);
                                    } else if ("stdio".equals(transportType)) {
                                        // 瑙ｆ瀽STDIO閰嶇疆
                                        if (transportConfigJson != null) {
                                            transportConfigJson.remove("policy");
                                        }
                                        Map<String, AiClientToolMcpVO.TransportConfigStdio.Stdio> stdio = JSON.parseObject(
                                                transportConfigJson == null ? "{}" : transportConfigJson.toJSONString(),
                                                new TypeReference<>() {
                                                });

                                        AiClientToolMcpVO.TransportConfigStdio transportConfigStdio = new AiClientToolMcpVO.TransportConfigStdio();
                                        transportConfigStdio.setStdio(stdio);

                                        mcpVO.setTransportConfigStdio(transportConfigStdio);
                                    }
                                } catch (Exception e) {
                                    log.error("瑙ｆ瀽浼犺緭閰嶇疆澶辫触: {}", e.getMessage(), e);
                                }
                                result.add(mcpVO);
                            }
                        }
                    }
                }
            }
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
            // 1. 閫氳繃clientId鏌ヨ鍏宠仈鐨刾rompt閰嶇疆
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId(AI_CLIENT.getCode(), clientId);

            for (AiClientConfig config : configs) {
                if ("prompt".equals(config.getTargetType()) && config.getStatus() == 1) {
                    String promptId = config.getTargetId();

                    // 閬垮厤閲嶅澶勭悊鐩稿悓鐨刾romptId
                    if (processedPromptIds.contains(promptId)) {
                        continue;
                    }
                    processedPromptIds.add(promptId);

                    // 2. 閫氳繃promptId鏌ヨai_client_system_prompt琛ㄨ幏鍙栫郴缁熸彁绀鸿瘝閰嶇疆
                    AiClientSystemPrompt systemPrompt = aiClientSystemPromptDao.queryByPromptId(promptId);
                    if (systemPrompt != null && systemPrompt.getStatus() == 1) {
                        // 3. 杞崲涓篤O瀵硅薄
                        AiClientSystemPromptVO promptVO = AiClientSystemPromptVO.builder()
                                .promptId(systemPrompt.getPromptId())
                                .promptName(systemPrompt.getPromptName())
                                .promptContent(systemPrompt.getPromptContent())
                                .description(systemPrompt.getDescription())
                                .build();

                        result.add(promptVO);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public Map<String, AiClientSystemPromptVO> queryAiClientSystemPromptMapByClientIds(List<String> clientIdList) {
        List<AiClientSystemPromptVO> aiClientSystemPrompts = AiClientSystemPromptVOByClientIds(clientIdList);

        if (null == aiClientSystemPrompts || aiClientSystemPrompts.isEmpty()) {
            return Collections.emptyMap();
        }

        // 灏哖O瀵硅薄杞崲涓篤O瀵硅薄锛屽苟鏋勫缓Map缁撴瀯
        return aiClientSystemPrompts.stream()
                .map(prompt -> AiClientSystemPromptVO.builder()
                        .promptId(prompt.getPromptId())
                        .promptContent(prompt.getPromptContent())
                        .build())
                .collect(Collectors.toMap(
                        AiClientSystemPromptVO::getPromptId,  // key: id
                        prompt -> prompt,               // value: AiClientSystemPromptVO瀵硅薄
                        (existing, replacement) -> existing  // 濡傛灉鏈夐噸澶峩ey锛屼繚鐣欑涓€涓?
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
            // 1. 鏌ヨ瀹㈡埛绔浉鍏崇殑advisor閰嶇疆
            List<AiClientConfig> configs = aiClientConfigDao.queryBySourceTypeAndId("client", clientId);

            for (AiClientConfig config : configs) {
                if (config.getStatus() != 1 || !"advisor".equals(config.getTargetType())) {
                    continue;
                }

                String advisorId = config.getTargetId();
                if (processedAdvisorIds.contains(advisorId)) {
                    continue;
                }
                processedAdvisorIds.add(advisorId);

                // 2. 鏌ヨadvisor璇︾粏淇℃伅
                AiClientAdvisor aiClientAdvisor = aiClientAdvisorDao.queryByAdvisorId(advisorId);
                if (aiClientAdvisor == null || aiClientAdvisor.getStatus() != 1) {
                    continue;
                }

                // 3. 瑙ｆ瀽extParam涓殑閰嶇疆
                AiClientAdvisorVO.ChatMemory chatMemory = null;
                AiClientAdvisorVO.RagAnswer ragAnswer = null;
                AiClientAdvisorVO.PromptInjectionSanitizer promptInjectionSanitizer = null;

                String extParam = aiClientAdvisor.getExtParam();
                if (extParam != null && !extParam.trim().isEmpty()) {
                    try {
                        if ("ChatMemory".equals(aiClientAdvisor.getAdvisorType())) {
                            // 瑙ｆ瀽chatMemory閰嶇疆
                            chatMemory = JSON.parseObject(extParam, AiClientAdvisorVO.ChatMemory.class);
                        } else if ("RagAnswer".equals(aiClientAdvisor.getAdvisorType())) {
                            // 瑙ｆ瀽ragAnswer閰嶇疆
                            ragAnswer = JSON.parseObject(extParam, AiClientAdvisorVO.RagAnswer.class);
                        } else if ("PromptInjectionSanitizer".equals(aiClientAdvisor.getAdvisorType())) {
                            // 瑙ｆ瀽 Prompt 娉ㄥ叆鎶ゆ爮閰嶇疆
                            promptInjectionSanitizer = JSON.parseObject(extParam, AiClientAdvisorVO.PromptInjectionSanitizer.class);
                        }
                    } catch (Exception e) {
                        // 瑙ｆ瀽澶辫触鏃跺拷鐣ワ紝浣跨敤榛樿鍊糿ull
                    }
                }

                // 4. 鏋勫缓AiClientAdvisorVO瀵硅薄
                AiClientAdvisorVO advisorVO = AiClientAdvisorVO.builder()
                        .advisorId(aiClientAdvisor.getAdvisorId())
                        .advisorName(aiClientAdvisor.getAdvisorName())
                        .advisorType(aiClientAdvisor.getAdvisorType())
                        .orderNum(aiClientAdvisor.getOrderNum())
                        .chatMemory(chatMemory)
                        .ragAnswer(ragAnswer)
                        .promptInjectionSanitizer(promptInjectionSanitizer)
                        .build();

                result.add(advisorVO);
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
            if (processedClientIds.contains(clientId)) {
                continue;
            }
            processedClientIds.add(clientId);

            // 1. 鏌ヨ瀹㈡埛绔熀鏈俊鎭?
            AiClient aiClient = aiClientDao.queryByClientId(clientId);
            if (aiClient == null || aiClient.getStatus() != 1) {
                continue;
            }

            // 2. 鏌ヨ瀹㈡埛绔浉鍏抽厤缃?
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
                }
            }

            // 3. 鏋勫缓AiClientVO瀵硅薄
            AiClientVO aiClientVO = AiClientVO.builder()
                    .clientId(aiClient.getClientId())
                    .clientName(aiClient.getClientName())
                    .description(aiClient.getDescription())
                    .modelId(modelId)
                    .promptIdList(promptIdList)
                    .mcpIdList(mcpIdList)
                    .advisorIdList(advisorIdList)
                    .build();

            result.add(aiClientVO);
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
            // 1. 閫氳繃modelId鏌ヨ妯″瀷閰嶇疆锛岃幏鍙朼piId
            AiClientModel model = aiClientModelDao.queryByModelId(modelId);
            if (model != null && model.getStatus() == 1) {
                String apiId = model.getApiId();

                // 2. 閫氳繃apiId鏌ヨAPI閰嶇疆淇℃伅
                AiClientApi apiConfig = aiClientApiDao.queryByApiId(apiId);
                if (apiConfig != null && apiConfig.getStatus() == 1) {
                    // 3. 杞崲涓篤O瀵硅薄
                    AiClientApiVO apiVO = AiClientApiVO.builder()
                            .apiId(apiConfig.getApiId())
                            .baseUrl(apiConfig.getBaseUrl())
                            .apiKey(apiConfig.getApiKey())
                            .completionsPath(apiConfig.getCompletionsPath())
                            .embeddingsPath(apiConfig.getEmbeddingsPath())
                            .build();

                    // 閬垮厤閲嶅娣诲姞鐩稿悓鐨凙PI閰嶇疆
                    if (result.stream().noneMatch(vo -> vo.getApiId().equals(apiVO.getApiId()))) {
                        result.add(apiVO);
                    }
                }
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
            // 閫氳繃modelId鏌ヨ妯″瀷閰嶇疆
            AiClientModel model = aiClientModelDao.queryByModelId(modelId);
            if (model != null && model.getStatus() == 1) {
                // 杞崲涓篤O瀵硅薄
                AiClientModelVO modelVO = AiClientModelVO.builder()
                        .modelId(model.getModelId())
                        .apiId(model.getApiId())
                        .modelName(model.getModelName())
                        .modelType(model.getModelType())
                        .build();

                // 閬垮厤閲嶅娣诲姞鐩稿悓鐨勬ā鍨嬮厤缃?
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
            // 鏍规嵁鏅鸿兘浣揑D鏌ヨ娴佺▼閰嶇疆鍒楄〃
            List<AiAgentFlowConfig> flowConfigs = aiAgentFlowConfigDao.queryByAgentId(aiAgentId);
            
            if (flowConfigs == null || flowConfigs.isEmpty()) {
                return Map.of();
            }
            
            // 杞崲涓篗ap缁撴瀯锛宬ey涓篶lientId锛寁alue涓篈iAgentClientFlowConfigVO
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

}


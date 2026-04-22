package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.entity.MasterPlanVO;
import cn.bugstack.ai.domain.agent.model.entity.PlanStepVO;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.entity.TaskBoardItemVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.NextRoundDirectiveTypeEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.StepStatusEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Node1闂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弮鍫熸殰闁稿鎸剧划顓炩槈濡娅ч梺娲诲幗閻熲晠寮婚悢鍏煎€绘慨妤€妫欓悾鐑芥⒑缁嬪灝顒㈡い銊ユ婵＄敻宕熼姘祮濠德板€愰崑鎾趁瑰鍕姢閾绘牠鏌ｅ鈧褎绂掗敃鍌涚厱闁靛绠戦婊堟煙娓氬灝濮傞柛鈹惧亾濡炪倖甯掔€氼參鎮¤箛娑欑厱妞ゆ劧绲跨粻鏍煕閿濆牆袚闁靛洤瀚伴弫鍌滄嫚閸欏褰庢繝娈垮枛閿曘倝鈥﹀畡鎵殾闁绘梻鈷堥弫鍐煥濠靛棙鍣洪柣蹇撻叄濮婄粯鎷呴悷閭﹀殝缂備浇顕х€氭澘鐣烽幋锔藉€风€瑰壊鍠栧▓顐︽⒑閸涘﹥澶勯柛銊﹀缁鈽夊▎宥勭盎闂佸湱鍎ら崹鍨閻愮繝绻嗛柛娆忣槹鐏忥附鎱ㄦ繝鍕笡闁瑰嘲鎳樺畷銊︾節閸涱垼鏀ㄩ梻鍌欒兌椤牏鑺卞ú顏勭９闁哄洨濮村鏌ユ⒒娴ｅ憡鎯堢紒瀣╃窔瀹曟粌鈽夊▎鎴锤閻熸粎澧楃敮妤呭煕閹烘鐓曢悘鐐插⒔閹冲棝鏌涜箛鎾剁伇缂佽鲸甯￠、姘跺川椤撶姳妗撻柣搴ゎ潐濞叉鎹㈤崼婵愬殨闁圭虎鍠楅崑鎰版煕韫囨挻鎲搁柣鐔哥箞閺岋絾鎯旈敍鍕殯闂佺閰ｆ禍鎯版濡炪倖鐗滈崑鐐哄磹?
 * 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸ゅ嫰鏌涢锝嗙闁稿被鍔庨幉绋款吋婢跺浠奸梺缁樺灩閻℃棃寮崱娑欑厱闁哄洢鍔屾晶顕€鏌涢幘璇℃綈缂佺粯鐩獮姗€寮堕幋鐘插Р闂備胶顭堥鍡涘箲閸ヮ剙钃熼柣鏃傗拡閺佸秵鎱ㄥΟ鍝勬毐妞ゅ浚鍙冮弻褏绱掑Ο鐓庘拰闂佸搫鏈粙鎴﹀煡婢舵劕纭€闁绘劕顕禍鑸电節?
 * 1. 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸ゅ嫰鏌涢幘鑼妽闁稿繑绮撻弻娑㈩敃閿濆棛顦ラ梺姹囧€濈粻鏍蓟閿濆绠涙い鎺嶈閺嬫瑥鈹戦悙鑼憼闁绘濞€瀵寮撮悢铏诡啎閻熸粌绉磋灋婵°倐鍋撻柣顭戝墮閳规垿鏁嶉崟顐℃澀闂佺锕ラ悧婊堝极椤曗偓楠炴帡寮崫鍕濠殿喗顭囬崢褎鏅堕幍顔剧＜妞ゆ棁鍋愭晶锔锯偓瑙勬礃鐢繝骞冨▎鎴斿亾閻㈡鐒鹃悽顖涘劤閳规垿鎮╅崹顐ｆ瘎闂佺顑嗛惄顖炲箖濡　鏀介悗锝庝簽椤︻噣姊洪棃娑氬婵☆偅绋撳褔鍩€椤掑嫭鈷戦梺顐ゅ仜閼活垱鏅堕鐐村€靛ù锝呭暙娴滃綊鏌嶈閸撴氨绮欓幒妞烩偓锕傚炊椤掆偓閸屻劌霉閻樺樊鍎愰柍閿嬪灴閺屾盯骞囬鈧痪褔鏌熼姘卞ⅵ闁哄被鍔戝鏉懳熺悰鈥充壕婵犻潧妫崵鏇㈡煙闂傚鍔嶉柛瀣閺屾稖绠涘顑挎睏闂佸憡眉缁瑥顫忓ú顏勪紶闁告洦鍘滈妶澶嬬厸濞达絽鎲￠幉鍝ョ磼椤旇姤顥堟い銏＄懇閺屻劑顢涘顐㈩棜闂佽崵鍠愰悷銉р偓姘煎幗瀵板嫰宕熼鈧悷閭︾叆闁告侗鍘哄▽顏嗙磽娴ｇ鈧湱鏁敓鐘叉瀬闁稿瞼鍋涚粈鍫㈡喐閺冨牆鐓橀煫鍥ㄧ⊕閻撶喖骞栭幖顓炵仯缂佸鏁婚弻娑氣偓锛卞啩澹曢梻鍌欑閹碱偆鎮锕€纾规繝闈涙－濞兼牗绻涘顔荤盎濞磋偐濞€閺屾盯寮撮悙鍏哥驳婵°倖妫冨缁樻媴娓氼垳鍔搁柣搴㈢▓閺呯姴鐣峰┑鍡忔瀻闊洦娲樺▓鐐箾閺夋垵鎮戞繛鍏肩懇瀹曟﹢鍩€椤掑嫭鍋℃繝濠傚閻帞鈧娲﹂崹璺虹暦閵娾晩鏁囨繛鎴炵懄閺夋悂姊绘担铏瑰笡闁挎岸鏌ｉ妶鍛缂佹梻鍠庨～婊堝焵椤掑嫬钃熼柨婵嗩樈閺佸洭鏌ｉ弴姘卞妽闁汇倓绶氬铏规嫚閳ヨ櫕鐏堢紓鍌氱Т閿曘倝鎮鹃悜钘夐唶闁哄洨鍋熼崐鐐烘偡濠婂啴鍙勭€规洘濞婇幊鐐哄Ψ閿濆嫮鐩庨梻浣瑰濡線顢氳閳诲秴顓兼径瀣幐閻庡厜鍋撻悗锝庡墰琚﹂梻浣筋嚃閸犳帡寮查悩鑼殾闁挎繂妫楃欢鐐烘倵閿濆骸浜滈柍褜鍓涢崗妯侯潖閾忚瀚氶柟缁樺俯閸斿姊洪崨濠傜伇妞ゎ偄顦辩划瀣吋婢舵ɑ鏅滈梺鍛婃处閸樿棄鈻撴ィ鍐┾拺闁圭娴风粻鎾翠繆椤愶絿銆掔€殿啫鍥х劦妞ゆ帒瀚埛鎴︽煕濞戞﹫姊楃紒鍫曚憾閺屾稓鈧綆浜滈埀顒€鎲￠弲銉モ攽鎺抽崐鏇㈠箠韫囨稑鐓曢柟杈鹃檮閸嬧剝绻涢崱妤冪妞ゅ繆鏅犻弻娑㈠棘鐠囨祴鍋撳┑瀣摕婵炴垯鍨洪崑鍕⒑閸噮鍎忓ù鐘虫尦閹绗熼姘变桓闂佸搫鏈粙鎴﹀煡婢跺ň鏋庨柟閭﹀枤閳诲繒绱撻崒娆掑厡閻庢艾绻樺畷鍫曞Ω瑜嶇敮妤呮⒒娴ｅ憡鍟炵紒璇插€婚埀顒佸嚬閸撶喖宕洪埀顒併亜閹哄棗浜惧銈庡幘閸忔ê鐣峰ú顏勎ㄩ柨鏇楀亾缂佸墎鍋ら弻鐔兼焽閿曗偓楠炴牠鏌?
 * 2. 缂?Node2 婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柟闂寸绾惧鏌ｉ幇顒佹儓闁搞劌鍊块弻娑㈩敃閿濆棛顦ョ紓浣哄С閸楁娊寮婚悢鍏尖拻閻庡灚鐡曠粣妤呮⒑鏉炴壆顦﹂悗姘嵆瀵鈽夊Ο閿嬵潔濠电姴锕ら崯浼村礉闁垮绠鹃悗娑欘焽閻﹦绱撳鍜冭含鐎殿喛顕ч埥澶娢熼崗鍏肩暦闂備線鈧偛鑻晶瀵糕偓瑙勬礃閸ㄧ敻鍩ユ径濠庢僵闁挎繂鎳嶆竟鏇炩攽椤旀枻渚涢柛鎿勭畱鍗辩憸鐗堝笚閻撳繘鏌涢妷鎴濆枤娴煎啫螖閻橀潧浠﹂悽顖ょ節閻涱喚鈧綆浜栭弨浠嬫煕閵夘喚鍘涢柛鐔插亾闂傚倸鍊搁崐椋庣矆娓氣偓楠炴牠顢曢敂缁樻櫈闂佸憡绋掑娆戝瑜版帗鐓曠憸搴ㄣ€冮崨瀛樺珔闁绘柨鎽滅粻楣冩煙鐎涙鎳冮柣蹇ｄ邯閺屾稒绻濋崒銈囧悑闂佸搫鏈惄顖炲春閸曨垰绀冮柣鎰靛墰閺嗩厼鈹戦悙宸殶濠殿喖绉瑰畷銊╊敍濠婃劗闂繝鐢靛仩閹活亞绱為埀顒併亜椤愩埄妯€闁糕晜鐩獮鍥偋閸垹骞嶉梻浣告啞缁嬫垿鏁冮妶澶婄厺闁哄洢鍨洪悡鐔哥箾閹存繂鑸归柡瀣⊕閵囧嫰骞橀悙钘変划閻庤娲栭妶鎼併€侀弴銏℃櫜闁稿本绋撻埀顒冮哺缁绘繄鍠婃径宀€锛熼梺绋款儐閸ㄥ灝鐣烽幇鏉跨闁挎洍鍋撻柛銊ュ€圭换娑橆啅椤旇崵鐩庨梺鎼炲妼閸婂骞夐幖浣瑰亱闁割偅绻勯悷銊х磽娴ｅ搫顎岄柛銊ョ埣楠炲啫螖閸涱喗娅滈柟鑲╄ˉ閸撴繈鎮橀崼銉︹拺閻犲洩灏欑粻鎻掆攽閻愯韬€殿喛顕ч埥澶愬閻樼數鏉告俊鐐€栭悧妤€顫濋妸銉愭帡濮€閿涘嫮顔曢柣搴㈢⊕椤洭鎯岄崱娑欑厱婵°倐鍋撻柛鐔锋健閿濈偠绠涢幘浣规そ椤㈡棃宕熼褍鏁归梻浣侯攰婢瑰牓骞撻鍡楃筏闁告繂瀚€閿濆閱囨繝闈涘暞閺傗偓闂備胶绮敃鈺呭磻閸曨剛顩查柟顖嗏偓閺€浠嬫煟濡櫣浠涢柡鍡忔櫊閺岋綁顢曢埗鈺傛暫闂?
 */
@Slf4j
@Service
public class Step1AnalyzerNode extends AbstractExecuteSupport {

    private static final Pattern LEGACY_NEXT_STEP_PATTERN =
            Pattern.compile("(?is)(?:next\\s*step|taskgoal|task goal|下一步|当前任务|本轮任务|任务目标)\\s*[:：]\\s*(.+?)(?:\\n\\s*\\n|$)");
    private static final Pattern LEGACY_STATUS_PATTERN =
            Pattern.compile("(?is)(?:pass|status|completionhint|completion hint|完成状态|完成判断|状态|通过情况)\\s*[:：]\\s*(.+?)(?:\\n\\s*\\n|$)");

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter,
                             DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        int round = dynamicContext.getStep();
        log.info("=== Round {} planning(Node1) ===", round);

        AiAgentClientFlowConfigVO flowConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                .get(AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(flowConfig.getClientId());

        String rawUserGoal = dynamicContext.getRawUserGoal();
        String existingSanitizedGoal = dynamicContext.getSanitizedUserGoal();
        String executionHistory = dynamicContext.getExecutionHistory() == null
                ? ""
                : dynamicContext.getExecutionHistory().toString();
        String currentTask = dynamicContext.getCurrentTask();
        String latestSupervision = dynamicContext.getValue("supervisionResult");
        String latestExecution = dynamicContext.getValue("executionResult");
        String planHistoryJson = JSON.toJSONString(safePlanHistory(dynamicContext.getPlanHistory()));

        Set<String> allowedTools = loadAllowedToolNames(flowConfig.getClientId());
        String planningPrompt = buildPlanningPrompt(
                round,
                dynamicContext.getMaxStep(),
                rawUserGoal,
                existingSanitizedGoal,
                requestParameter.getKnowledgeName(),
                currentTask,
                latestSupervision,
                latestExecution,
                planHistoryJson,
                allowedTools,
                dynamicContext
        );

        String planningResult = chatClient
                .prompt(buildPlanningRequestPrompt(planningPrompt))
                .advisors(a -> {
                    a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, buildNodeConversationId(requestParameter.getSessionId(), "node1"))
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 8);
                    applyTokenStatParams(
                            a, dynamicContext, requestParameter,
                            flowConfig.getClientId(),
                            AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode()
                    );
                })
                .call()
                .content();

        StepExecutionPlanVO plan = parsePlanOrFallback(planningResult, round, dynamicContext, allowedTools);
        normalizePlan(plan, round, dynamicContext);
        enforceToolNameWhitelist(plan, allowedTools);

        dynamicContext.setCurrentStepPlan(plan);
        dynamicContext.getPlanHistory().put(round, plan);
        dynamicContext.setCurrentTask(plan.getTaskGoal());
        syncStructuredPlanningState(dynamicContext, plan);

        String planJson = JSON.toJSONString(plan);
        dynamicContext.getExecutionHistory().append(String.format("""
                === 缂?d闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偞鐗犻、鏇氱秴闁搞儺鍓氶悞鑲┾偓骞垮劚閹虫劙鏁嶉悢鍏尖拺闁革富鍘奸。鍏肩節閵忊槅鐒界紒顕嗙到铻栧ù锝堟椤旀洟姊虹憴鍕剹闁告鏅▎銏犫槈濮樿京锛濋悗骞垮劚濡稒鏅堕鍛簻妞ゆ挾鍋為崑銉╂煙閾忣偆鐭掓俊顐㈠暙閳藉顫滈崱妯肩Ъ缂傚倸鍊搁崐椋庢媼閺屻儱纾?Node1) ===
                %s
                """, round, planJson));

        sendAnalysisSubResult(dynamicContext, "analysis_round",
                "round=" + round + ", maxStep=" + dynamicContext.getMaxStep(),
                requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_current_task",
                safe(dynamicContext.getCurrentTask()),
                requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_last_supervision",
                safe(latestSupervision),
                requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_last_execution",
                safe(latestExecution),
                requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_sanitized_goal", plan.getSanitizedUserGoal(), requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_step_plan", planJson, requestParameter.getSessionId());
        sendAnalysisSubResult(dynamicContext, "analysis_todo_list", buildTodoListText(dynamicContext), requestParameter.getSessionId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity requestParameter,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step2PrecisionExecutorNode");
    }

    private Set<String> loadAllowedToolNames(String clientId) {
        List<AiClientToolMcpVO> tools = repository.AiClientToolMcpVOByClientIds(List.of(clientId));
        return tools.stream()
                .map(AiClientToolMcpVO::getMcpName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private static Prompt buildPlanningRequestPrompt(String planningPrompt) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.NONE)
                .build();
        return new Prompt(planningPrompt, options);
    }

    private static String buildPlanningPrompt(int round,
                                              int maxStep,
                                              String rawUserGoal,
                                              String existingSanitizedGoal,
                                              String knowledgeName,
                                              String currentTask,
                                              String latestSupervision,
                                              String latestExecution,
                                              String planHistoryJson,
                                              Set<String> allowedTools,
                                              DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("planId", "plan-1-xxx");
        example.put("round", 1);
        example.put("sanitizedUserGoal", "...");
        example.put("taskGoal", "...");
        example.put("toolRequired", false);
        example.put("toolName", "");
        example.put("toolPurpose", "");
        example.put("toolArgsHint", "");
        example.put("expectedOutput", "...");
        example.put("completionHint", "...");

        Map<String, Object> planningContext = new LinkedHashMap<>();
        planningContext.put("round", round);
        planningContext.put("maxStep", maxStep);
        planningContext.put("rawUserGoal", safe(rawUserGoal));
        planningContext.put("existingSanitizedGoal", safe(existingSanitizedGoal));
        planningContext.put("knowledgeName", safe(knowledgeName));
        planningContext.put("sessionHistory", dynamicContext == null
                ? ""
                : safe(dynamicContext.getValue(SESSION_HISTORY_PROMPT_KEY)));
        planningContext.put("planningDigest", buildPlanningDigest(
                dynamicContext,
                currentTask,
                latestSupervision,
                latestExecution,
                planHistoryJson
        ));
        planningContext.put("currentRound", dynamicContext.getCurrentRound());
        planningContext.put("masterPlan", dynamicContext.getMasterPlan());
        planningContext.put("taskBoard", dynamicContext.getTaskBoard());
        planningContext.put("roundArchive", dynamicContext.getRoundArchive());
        planningContext.put("nextRoundDirective", dynamicContext.getNextRoundDirective());
        planningContext.put("overallStatus", dynamicContext.getOverallStatus());
        planningContext.put("allowedTools", allowedTools);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", "generate_current_round_plan");
        payload.put("outputSchema", List.of(
                "planId",
                "round",
                "sanitizedUserGoal",
                "taskGoal",
                "toolRequired",
                "toolName",
                "toolPurpose",
                "toolArgsHint",
                "expectedOutput",
                "completionHint"
        ));
        payload.put("constraints", List.of(
                "Return exactly one JSON object and nothing else.",
                "Only plan the current round, not the final answer.",
                "If toolRequired is false, toolName must be empty.",
                "If toolRequired is true, toolName must be chosen from allowedTools.",
                "toolArgsHint should contain argument hints only, not fabricated concrete results.",
                "If knowledgeName is present and this is mainly QA or explanation, prefer toolRequired=false so Node2 can rely on RAG.",
                "Only require a tool when external retrieval or side-effect operation is necessary.",
                "If the request is a single-shot QA/RAG/explanation task and the current round already satisfies the user's raw input, do not invent a post-answer confirmation round; keep the current round as the deliverable.",
                "If sessionHistory is present, use it only to preserve cross-session user intent continuity and not as proof that the current round is already completed.",
                "Use planningDigest, currentRound, masterPlan, taskBoard, roundArchive, nextRoundDirective, and overallStatus as the main planning state.",
                "If toolName is baidu-search, toolArgsHint should include query=..."
        ));
        payload.put("example", example);
        payload.put("context", planningContext);
        return JSON.toJSONString(payload);
    }

    static Map<String, Object> buildPlanningDigest(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                   String currentTask,
                                                   String latestSupervision,
                                                   String latestExecution,
                                                   String planHistoryJson) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("currentTask", safe(currentTask));
        digest.put("currentRound", dynamicContext == null || dynamicContext.getCurrentRound() == null
                ? Map.of()
                : dynamicContext.getCurrentRound());
        digest.put("nextRoundDirective", dynamicContext == null || dynamicContext.getNextRoundDirective() == null
                ? Map.of()
                : dynamicContext.getNextRoundDirective());
        digest.put("overallStatus", dynamicContext == null || dynamicContext.getOverallStatus() == null
                ? Map.of()
                : dynamicContext.getOverallStatus());
        digest.put("recentPlanHistory", buildRecentPlanHistory(dynamicContext, 2));
        digest.put("taskBoardSummary", buildTaskBoardSummary(dynamicContext));
        digest.put("latestSupervision", trimForPrompt(latestSupervision, 1200));
        digest.put("latestExecution", trimForPrompt(latestExecution, 1200));
        digest.put("planHistoryDigest", trimForPrompt(planHistoryJson, 1200));
        digest.put("executionHistoryTail", tailPromptText(dynamicContext == null ? null : dynamicContext.getExecutionHistory(), 2200, 30));
        return digest;
    }

    private static List<Map<String, Object>> buildRecentPlanHistory(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                                    int maxItems) {
        if (dynamicContext == null || dynamicContext.getPlanHistory() == null || dynamicContext.getPlanHistory().isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        List<Map.Entry<Integer, StepExecutionPlanVO>> entries = new ArrayList<>(dynamicContext.getPlanHistory().entrySet());
        for (int i = Math.max(0, entries.size() - maxItems); i < entries.size(); i++) {
            StepExecutionPlanVO plan = entries.get(i).getValue();
            if (plan == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("round", plan.getRound());
            item.put("planId", plan.getPlanId());
            item.put("taskGoal", plan.getTaskGoal());
            item.put("toolRequired", plan.getToolRequired());
            item.put("toolName", plan.getToolName());
            item.put("completionHint", trimForPrompt(plan.getCompletionHint(), 300));
            recent.add(item);
        }
        return recent;
    }

    private static Map<String, Object> buildTaskBoardSummary(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (dynamicContext == null || dynamicContext.getTaskBoard() == null || dynamicContext.getTaskBoard().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        dynamicContext.getTaskBoard().forEach((stepId, item) -> {
            if (item == null) {
                return;
            }
            Map<String, Object> compact = new LinkedHashMap<>();
            compact.put("status", item.getStatus());
            compact.put("attemptCount", item.getAttemptCount());
            compact.put("lastRoundTask", trimForPrompt(item.getLastRoundTask(), 300));
            compact.put("lastFailureReason", trimForPrompt(item.getLastFailureReason(), 300));
            compact.put("acceptedOutputsSize", item.getAcceptedOutputs() == null ? 0 : item.getAcceptedOutputs().size());
            summary.put(stepId, compact);
        });
        return summary;
    }

    static String buildTodoListText(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("本轮规划清单\n");

        if (dynamicContext == null) {
            sb.append("\n- 暂无规划上下文\n");
            return sb.toString().trim();
        }

        if (dynamicContext.getMasterPlan() != null
                && dynamicContext.getMasterPlan().getMainSteps() != null
                && !dynamicContext.getMasterPlan().getMainSteps().isEmpty()) {
            int index = 1;
            for (PlanStepVO step : dynamicContext.getMasterPlan().getMainSteps()) {
                if (step == null) {
                    continue;
                }
                sb.append("\n").append(index++).append(". ");
                sb.append(StringUtils.hasText(step.getTitle()) ? trimForPrompt(step.getTitle(), 120) : safe(step.getStepId()));
                sb.append("\n");
                sb.append("   - 任务：").append(trimForPrompt(step.getGoal(), 240)).append("\n");
                sb.append("   - 完成标准：").append(trimForPrompt(step.getCompletionCriteria(), 240)).append("\n");
                sb.append("   - 状态：").append(formatStepStatus(step.getStatus())).append("\n");
            }
        } else if (dynamicContext.getCurrentRound() != null) {
            sb.append("\n1. 当前轮任务\n");
            sb.append("   - 任务：").append(trimForPrompt(dynamicContext.getCurrentRound().getRoundTask(), 300)).append('\n');
            sb.append("   - 完成标准：").append(trimForPrompt(dynamicContext.getCurrentRound().getExpectedEvidence(), 300)).append('\n');
            sb.append("   - 状态：").append(formatStepStatus(dynamicContext.getCurrentRound().getStatus())).append('\n');
        }

        if (dynamicContext.getNextRoundDirective() != null) {
            sb.append("\n下一步指令：")
                    .append(dynamicContext.getNextRoundDirective().getDirectiveType());
            if (StringUtils.hasText(dynamicContext.getNextRoundDirective().getTargetStepId())) {
                sb.append(" -> ").append(dynamicContext.getNextRoundDirective().getTargetStepId());
            }
            sb.append('\n');
        }
        if (dynamicContext.getOverallStatus() != null) {
            sb.append("总体状态：").append(dynamicContext.getOverallStatus().getState());
            if (StringUtils.hasText(dynamicContext.getOverallStatus().getFinalDecision())) {
                sb.append("（").append(dynamicContext.getOverallStatus().getFinalDecision()).append("）");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String formatStepStatus(Object status) {
        if (status == null) {
            return "待开始";
        }
        String value = String.valueOf(status).toUpperCase(Locale.ROOT);
        return switch (value) {
            case "COMPLETED" -> "已完成";
            case "IN_PROGRESS" -> "进行中";
            case "FAILED" -> "失败";
            default -> "待开始";
        };
    }

    private static String tailPromptText(StringBuilder text, int maxChars, int maxLines) {
        if (text == null || text.length() == 0 || maxChars <= 0) {
            return "";
        }
        String raw = text.toString();
        if (raw.length() > maxChars) {
            raw = raw.substring(Math.max(0, raw.length() - maxChars));
        }
        if (maxLines <= 0) {
            return raw;
        }
        String[] lines = raw.split("\\R");
        if (lines.length <= maxLines) {
            return raw;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.length - maxLines); i < lines.length; i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static String trimForPrompt(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return "";
        }
        String value = text.trim();
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(Math.max(0, value.length() - maxChars));
    }

    private StepExecutionPlanVO parsePlanOrFallback(String planningResult,
                                                    int round,
                                                    DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                    Set<String> allowedTools) {
        if (!StringUtils.hasText(planningResult)) {
            return buildFallbackPlan(round, dynamicContext, "Node1 returned empty content");
        }

        String text = sanitizeModelOutput(planningResult);
        if (isSecurityRejectedResponse(text)) {
            throw new IllegalStateException(text);
        }

        String jsonText = extractJson(text);
        try {
            StepExecutionPlanVO plan = JSON.parseObject(jsonText, StepExecutionPlanVO.class);
            if (plan == null) {
                return parseLegacyTextPlan(text, round, dynamicContext, allowedTools);
            }
            return plan;
        } catch (Exception e) {
            log.warn("Node1 JSON parse failed, fallback to legacy parser. raw={}", text);
            return parseLegacyTextPlan(text, round, dynamicContext, allowedTools);
        }
    }

    private StepExecutionPlanVO parseLegacyTextPlan(String rawText,
                                                    int round,
                                                    DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                    Set<String> allowedTools) {
        String text = rawText == null ? "" : rawText;
        String sanitizedGoal = dynamicContext.getSanitizedUserGoal();
        if (!StringUtils.hasText(sanitizedGoal)) {
            sanitizedGoal = dynamicContext.getRawUserGoal();
        }

        String taskGoal = extractByPattern(text, LEGACY_NEXT_STEP_PATTERN);
        if (!StringUtils.hasText(taskGoal)) {
            taskGoal = "answer the user directly without tools";
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        boolean needToolByText = lowerText.contains("need tool")
                || lowerText.contains("need tools")
                || lowerText.contains("toolrequired: true")
                || lowerText.contains("toolrequired=true")
                || lowerText.contains("tool_required=true")
                || text.contains("需要工具")
                || text.contains("调用工具")
                || text.contains("使用工具")
                || text.contains("需要调用")
                || text.contains("工具必需")
                || text.contains("工具必须");
        String toolName = detectToolName(text, allowedTools);
        boolean toolRequired = needToolByText || StringUtils.hasText(toolName);
        if (!toolRequired) {
            toolName = "";
        }

        String completionHint = extractByPattern(text, LEGACY_STATUS_PATTERN);
        if (!StringUtils.hasText(completionHint)) {
            completionHint = "legacy text output parsed and continued";
        }

        return StepExecutionPlanVO.builder()
                .planId("legacy-" + round + "-" + UUID.randomUUID())
                .round(round)
                .sanitizedUserGoal(sanitizedGoal)
                .taskGoal(taskGoal)
                .toolRequired(toolRequired)
                .toolName(toolName)
                .toolPurpose(toolRequired ? "use tool for current task" : "")
                .toolArgsHint("")
                .expectedOutput("provide a concise and accurate answer")
                .completionHint(completionHint)
                .build();
    }

    private StepExecutionPlanVO buildFallbackPlan(int round,
                                                  DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                  String reason) {
        String sanitizedGoal = dynamicContext.getSanitizedUserGoal();
        if (!StringUtils.hasText(sanitizedGoal)) {
            sanitizedGoal = dynamicContext.getRawUserGoal();
        }

        return StepExecutionPlanVO.builder()
                .planId("fallback-" + round + "-" + UUID.randomUUID())
                .round(round)
                .sanitizedUserGoal(sanitizedGoal)
                .taskGoal("answer the user directly without tools")
                .toolRequired(false)
                .toolName("")
                .toolPurpose("")
                .toolArgsHint("")
                .expectedOutput("provide a concise and accurate answer")
                .completionHint(reason)
                .build();
    }

    private void normalizePlan(StepExecutionPlanVO plan,
                               int round,
                               DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        if (!StringUtils.hasText(plan.getPlanId())) {
            plan.setPlanId("plan-" + round + "-" + UUID.randomUUID());
        }
        if (plan.getRound() == null) {
            plan.setRound(round);
        }

        if (!StringUtils.hasText(plan.getSanitizedUserGoal())) {
            String existing = dynamicContext.getSanitizedUserGoal();
            plan.setSanitizedUserGoal(StringUtils.hasText(existing) ? existing : dynamicContext.getRawUserGoal());
        }

        if (!StringUtils.hasText(dynamicContext.getSanitizedUserGoal())) {
            dynamicContext.setSanitizedUserGoal(plan.getSanitizedUserGoal());
        }

        if (!StringUtils.hasText(plan.getTaskGoal())) {
            plan.setTaskGoal("complete the current round task");
        }
        if (plan.getToolRequired() == null) {
            plan.setToolRequired(false);
        }
        if (!Boolean.TRUE.equals(plan.getToolRequired())) {
            plan.setToolName("");
            plan.setToolPurpose("");
            plan.setToolArgsHint("");
            return;
        }

        // 婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柟闂寸绾惧鏌ｉ幇顒佹儓闁搞劌鍊块弻娑㈩敃閿濆棛顦ョ紓浣哄Т缂嶅﹪寮诲澶婁紶闁告洦鍓欏▍锝夋⒑缁嬭儻顫﹂柛鏂跨焸濠€渚€姊虹紒妯忣亜螣婵犲洤纾块煫鍥ㄧ⊕閻撴洟鏌熺€电孝闁宠鐗撻弻锛勪沪閸撗勫垱濡ょ姷鍋涘ú顓㈠春閳╁啯濯撮柤鍙夌缚閸旀垵顫忓ú顏勪紶闁告洟娼ч崜閬嶆⒑缂佹﹩娈樺┑鐐╁亾閻庢鍠栭…宄邦嚕閹绢喗鍋勯柧蹇氼嚃閸熷酣姊绘担铏瑰笡闁告棑绠撳畷婊冾潩閼搁潧浠ч梺鍝勫€哥花閬嶅绩娴犲鐓熼柟閭﹀墮缁狙囨煕閿濆嫮鐭欓柡灞剧〒閳ь剨绲婚崝宀勫焵椤掍胶绠撴い鏇稻缁绘繂顫濋鈹炬櫊閺屾洘寰勯崼婵堜痪濡炪値鍋勭粔鎾煘閹达附鍊烽柛娆忣樈濡繝姊洪幖鐐插缂傚秴锕ら悾鐑芥晲閸涱亝鏂€闁诲函缍嗛崑鍡涘储?baidu-search 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸ゅ嫰鏌涢锝嗙闁稿被鍔庨幉鎼佸籍閸惊銉╂煕閹般劍娅嗛柛搴ｅ枛閺屾洝绠涚€ｎ亞鍔村┑鐐跺皺椤牓鍩為幋锔藉亹閻犲泧鍐х矗闂佽瀛╅崙褰掑矗閸愩劎鏆﹂柨婵嗙墢閻も偓濠电偞鍨堕悷褔宕㈤幘缁樷拺闁告稑锕︾粻鎾绘倵濮樼厧澧寸€殿喗濞婇幃娆撴倻濡厧骞堥梺璇插嚱缂嶅棝宕戦幘缁樺殌闁秆勵殕閻撴盯鎮橀悙闈涗壕缂佲偓閸愵亖鍋撻崹顐ｇ凡閻庢碍婢橀悾鐑藉础閻愬秶鍠栭幊锟犲Χ閸涱垱鍋х紓?query 闂傚倸鍊搁崐鎼佸磹閹间礁纾瑰瀣捣閻棗霉閿濆浜ら柤鏉挎健瀵爼宕煎顓熺彅闂佹悶鍔嶇换鍐Φ閸曨垰鍐€妞ゆ劦婢€缁墎绱撴担鎻掍壕婵犮垼娉涢鍕崲閸℃稒鐓忛柛顐ｇ箖閸ｆ椽鏌涢敐鍥ㄥ殌妞も晛銈稿畷銊╊敇濞戞瑦鏉告俊鐐€栧濠氭偤閺冨牊鍊垮Δ锝呭暞閻撴洟鏌嶉崫鍕偓缁樻櫠閻㈢鍋撳▓鍨灍闁绘搫绻濋妴浣肝旈崨顓狅紲濠德板€愰崑鎾趁瑰鍫㈢暫闁哄本绋栫粻娑㈠箼閸愨敩锔界箾鐎涙鐭掔紒鐘崇墪椤繐煤椤忓嫬绐涙繝鐢靛Т閸燁偊藝閳哄倻绠鹃悗娑欘焽閻矂鏌涚€ｎ剙鏋庨崡閬嶆煙闁箑澧绘繛灏栨櫊閺屻倝宕妷顔芥瘜闂?Node2 闂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸婂潡鏌ㄩ弴鐐测偓鍝ョ不閺嶎厽鐓曟い鎰剁稻缁€鈧紒鐐劤濞硷繝寮昏缁犳盯鏁愰崨顒傚嚬闂備礁鎲￠悷銉ф崲濮椻偓瀵鍩勯崘銊х獮闁诲函缍嗘禍鐐哄礉閹间焦鈷戦柟鑲╁仜閳ь剚鐗曠叅婵せ鍋撳┑锛勬暬瀹曠喖顢涘槌栧敽闂備胶鎳撻悺銊ф崲瀹ュ棛顩峰┑鍌氭啞閳锋垿鏌涘┑鍡楊伌闁稿骸娴风槐鎺楁嚋闂堟稑鎽甸悗娈垮枛椤兘寮幇鏉垮窛闁稿本绋掗ˉ鍫ユ煕閳规儳浜炬俊鐐€栫敮鎺斺偓姘煎弮瀹曟垹鈧綆鍠楅悡鏇㈡煃閳轰礁鏆熼柟鍐插暟缁辨帡鐓幓鎺嗗亾濠靛钃熸繛鎴欏灪閺呮粓鎮归崶銊ョ祷缂佺姾宕电槐鎺楁倷椤掆偓閸斻倝鏌曢崼鐔稿€愮€殿喛顕ч濂稿醇椤愶綆鈧洭姊绘担鍛婂暈闁规悂绠栧畷浼村冀椤撶姴绁﹂梺鍝勭▉閸忔瑦绂嶈ぐ鎺撶厵闁绘垶蓱鐏忣厼霉閻欌偓閸欏啫顫忛搹瑙勫枂闁告洟娼ч弲閬嶆⒑閸濄儱校鐎光偓閹间礁绠栧Δ锝呭暙缁€鍐╃箾閺夋埈鍎愰柡鍌楀亾闂傚倷鑳剁划顖炴晝閳哄懎绐楅柡宥庡幗閸婅埖銇勮箛鎾跺闁绘挻娲熼獮鏍庨鈧埀顒佹礃缁傚秷銇愰幒鎾跺幈闂佸湱鍎ら幐鍝ョ箔濮樿埖鐓忛柛顐墰缁夘喚鈧娲橀敃銏′繆濮濆矈妲绘繝娈垮櫙闂勫嫭绌辨繝鍥ㄥ€锋い蹇撳閸嬫捇寮借閸熷懎鈹戦悩瀹犲缁炬儳顭烽弻鐔煎礈瑜忕敮娑㈡煟閹捐泛校缂佺粯鐩幊鐘筹紣濠靛棙顔勯梻浣筋嚙妤犲繒绮婚幋锕€鐓橀柟杈鹃檮閺咁剟鏌涢弴銊ュ婵絽鐗嗚灃闁绘﹢娼ф禒婊勩亜閹存繍妯€妤犵偛鍟撮崺锟犲川椤撶媭妲伴柣搴ｆ嚀婢瑰﹪宕伴弴鐘愁潟?
        if ("baidu-search".equalsIgnoreCase(safe(plan.getToolName()))
                && !hasNamedArg(plan.getToolArgsHint(), "query")) {
            String seed = StringUtils.hasText(plan.getSanitizedUserGoal())
                    ? plan.getSanitizedUserGoal()
                    : plan.getTaskGoal();
            plan.setToolArgsHint("query=" + safe(seed));
        }
    }

    private void enforceToolNameWhitelist(StepExecutionPlanVO plan, Set<String> allowedTools) {
        if (!Boolean.TRUE.equals(plan.getToolRequired())) {
            return;
        }
        if (!StringUtils.hasText(plan.getToolName()) || !allowedTools.contains(plan.getToolName())) {
            plan.setToolRequired(false);
            plan.setToolName("");
            plan.setToolPurpose("tool name not in whitelist, downgrade to direct answer");
            plan.setToolArgsHint("");
        }
    }

    static void syncStructuredPlanningState(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                            StepExecutionPlanVO plan) {
        if (dynamicContext == null || plan == null) {
            return;
        }

        int round = plan.getRound() == null ? dynamicContext.getStep() : plan.getRound();
        String stepId = resolvePlanningStepId(dynamicContext, round);

        if (dynamicContext.getMasterPlan() == null) {
            dynamicContext.setMasterPlan(MasterPlanVO.builder()
                    .planVersion(1)
                    .mainSteps(new ArrayList<>())
                    .sessionGoal(dynamicContext.getSessionGoal())
                    .build());
        }

        PlanStepVO planStep = dynamicContext.getMasterPlan().getMainSteps().stream()
                .filter(item -> stepId.equals(item.getStepId()))
                .findFirst()
                .orElseGet(() -> {
                    PlanStepVO created = PlanStepVO.builder()
                            .stepId(stepId)
                            .status(StepStatusEnumVO.PENDING)
                            .dependencies(new ArrayList<>())
                            .build();
                    dynamicContext.getMasterPlan().getMainSteps().add(created);
                    return created;
                });
        planStep.setTitle("Round " + round);
        planStep.setGoal(plan.getTaskGoal());
        planStep.setCompletionCriteria(plan.getCompletionHint());
        planStep.setStatus(StepStatusEnumVO.PENDING);

        CurrentRoundTaskVO currentRound = CurrentRoundTaskVO.builder()
                .roundIndex(round)
                .currentStepId(stepId)
                .roundTask(plan.getTaskGoal())
                .suggestedTools(Boolean.TRUE.equals(plan.getToolRequired()) && StringUtils.hasText(plan.getToolName())
                        ? java.util.List.of(plan.getToolName()) : new ArrayList<>())
                .plannerNotes(plan.getToolPurpose())
                .expectedEvidence(plan.getExpectedOutput())
                .toolRequired(Boolean.TRUE.equals(plan.getToolRequired()))
                .status(StepStatusEnumVO.PENDING)
                .build();
        dynamicContext.setCurrentRound(currentRound);

        TaskBoardItemVO item = dynamicContext.getTaskBoard().computeIfAbsent(stepId, key -> TaskBoardItemVO.builder()
                .stepId(stepId)
                .attemptCount(0)
                .acceptedOutputs(new ArrayList<>())
                .status(StepStatusEnumVO.PENDING)
                .build());
        item.setLastRoundTask(plan.getTaskGoal());
        item.setStatus(StepStatusEnumVO.PENDING);

        dynamicContext.getRoundArchive().computeIfAbsent(round,
                        key -> cn.bugstack.ai.domain.agent.model.entity.RoundArchiveVO.builder().round(round).build())
                .setNode1PlanSnapshot(JSON.toJSONString(plan));
    }

    private static String resolvePlanningStepId(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                                int round) {
        if (dynamicContext.getNextRoundDirective() != null
                && dynamicContext.getNextRoundDirective().getDirectiveType() == NextRoundDirectiveTypeEnumVO.REPLAN_SAME_STEP
                && StringUtils.hasText(dynamicContext.getNextRoundDirective().getTargetStepId())) {
            return dynamicContext.getNextRoundDirective().getTargetStepId();
        }
        return "step-" + round;
    }

    private static String extractJson(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int firstBrace = text.indexOf('{');
        if (firstBrace < 0) {
            return text;
        }
        int depth = 0;
        for (int i = firstBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(firstBrace, i + 1);
                }
            }
        }
        return text;
    }

    private static String sanitizeModelOutput(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 32 || c == '\n' || c == '\r' || c == '\t') && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private static String extractByPattern(String text, Pattern pattern) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String detectToolName(String text, Set<String> allowedTools) {
        if (!StringUtils.hasText(text) || allowedTools == null || allowedTools.isEmpty()) {
            return "";
        }
        for (String toolName : allowedTools) {
            if (StringUtils.hasText(toolName) && text.contains(toolName)) {
                return toolName;
            }
        }
        return "";
    }

    private static boolean isSecurityRejectedResponse(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("security_rejected")
                || normalized.contains("rejected by security guardrail")
                || normalized.contains("input rejected by security policy")
                || normalized.contains("request rejected");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasNamedArg(String hint, String argName) {
        if (!StringUtils.hasText(hint) || !StringUtils.hasText(argName)) {
            return false;
        }
        String normalized = hint.toLowerCase(Locale.ROOT);
        String arg = argName.toLowerCase(Locale.ROOT);
        return normalized.contains(arg + "=") || normalized.contains(arg + ":");
    }

    private static Map<Integer, StepExecutionPlanVO> safePlanHistory(Map<Integer, StepExecutionPlanVO> planHistory) {
        return planHistory == null ? Map.of() : planHistory;
    }

    private static String buildNodeConversationId(String sessionId, String nodeTag) {
        if (!StringUtils.hasText(sessionId)) {
            return nodeTag;
        }
        return sessionId + ":" + nodeTag;
    }

    private void sendAnalysisSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                       String subType, String content, String sessionId) {
        if (!StringUtils.hasText(subType) || !StringUtils.hasText(content)) {
            return;
        }
        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createAnalysisSubResult(
                dynamicContext.getStep(), subType, content, sessionId);
        sendSseResult(dynamicContext, result);
    }
}

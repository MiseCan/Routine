package com.daystep.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.*

data class ConversationMessage(val role: String, val content: String, val at: String,val attachments:List<ChatAttachment> = emptyList()) {
    fun json() = JSONObject().put("role",role).put("content",content).put("at",at).put("attachments",JSONArray().apply{attachments.forEach{put(it.json())}})
    val display: String get() = if(role == "assistant") runCatching { JSONObject(content).getString("reply") }.getOrDefault(content) else content
}
data class AiQuestion(val id: String, val prompt: String, val kind: String, val options: List<Pair<String,String>>)
data class PlannedAction(val date: String, val title: String, val minutes: Int, val definitionOfDone: String)
data class PlannerResult(val planId: String?, val reply: String, val status: String, val task: JSONObject, val questions: List<AiQuestion>, val actions: List<PlannedAction>, val warnings: List<String>,val targetPlanId:String?=null,val scheduleSummaries:List<String> = emptyList(),val shortTitle:String="") {
    companion object {
        fun parse(json: JSONObject): PlannerResult {
            fun <T> array(name: String, map: (JSONObject)->T): List<T> { val a=json.getJSONArray(name); return (0 until a.length()).map { map(a.getJSONObject(it)) } }
            val warnings=json.getJSONArray("warnings")
            return PlannerResult(json.optString("planId").takeIf { it.isNotBlank() && it != "null" },json.getString("reply"),json.getString("status"),json.getJSONObject("task"),
                array("questions") {q -> val a=q.getJSONArray("options"); AiQuestion(q.getString("id"),q.getString("prompt"),q.getString("kind"),(0 until a.length()).map {i -> a.getJSONObject(i).let {it.getString("label") to it.getString("value")} }) },
                array(if(json.has("dailySteps"))"dailySteps"else "steps") {PlannedAction(it.getString("date"),it.getString("title"),it.getInt("minutes"),it.getString("definitionOfDone"))},(0 until warnings.length()).map {warnings.getString(it)},json.optString("targetPlanId").takeIf{it.isNotBlank()&&it!="null"},json.optJSONArray("schedules")?.let{a->(0 until a.length()).map{ScheduleRules.summary(a.getJSONObject(it))}}?:emptyList(),json.optString("shortTitle"))
        }
    }
}
class PlannerState(application: Application) : AndroidViewModel(application) {
    private val prefs=application.getSharedPreferences("daystep_ai",0)
    private val attachmentStore=AttachmentStore(application)
    private val chat=SingleChatStore(prefs)
    var activeChatId by mutableStateOf(java.util.UUID.randomUUID().toString()); private set
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val vault=SecretVault(application)
    private val client=DeepSeekClient()
    private val system=application.assets.open("planner-system.txt").bufferedReader().use {it.readText()}
    var hasKey by mutableStateOf(vault.exists()); private set
    var customPrompt by mutableStateOf(PromptPolicy.migrate(prefs)); private set
    var messages by mutableStateOf(readMessages()); private set
    var result by mutableStateOf(runCatching {PlannerResult.parse(JSONObject(prefs.getString("result","")!!))}.getOrNull()); private set
    var previewStale by mutableStateOf(prefs.getBoolean("preview_stale",false)); private set
    var questionRound by mutableIntStateOf(prefs.getInt("question_round",0)); private set
    private var answerDraft by mutableStateOf(prefs.getString("answer_draft","{}")!!)
    private var generationRequested=prefs.getBoolean("generation_requested",false)
    val canGeneratePlan:Boolean get()=InterviewPolicy.generationAvailable(questionRound)
    fun selectedAnswer(id:String)=runCatching{JSONObject(answerDraft).optString(id)}.getOrDefault("")
    fun selectAnswer(id:String,value:String){if(value.length>1000)return;val a=runCatching{JSONObject(answerDraft)}.getOrDefault(JSONObject());a.put(id,value);answerDraft=a.toString();prefs.edit().putString("answer_draft",answerDraft).apply()}
    fun submitAnswers(existing:List<Step>){
        val questions=result?.questions?:return
        if(questions.isEmpty()||questions.any{selectedAnswer(it.id).isBlank()}){error="请先完成本轮各项选择，也可以直接在下方输入完整说明。";return}
        val text=questions.joinToString("\n"){"${it.prompt}：${selectedAnswer(it.id)}"}+(if(input.isNotBlank())"\n补充：$input" else "")
        send(text,existing,combineAnswers=false)
    }
    var busy by mutableStateOf(false); private set
    var attaching by mutableStateOf(false); private set
    val working:Boolean get()=busy||attaching
    var draftAttachments by mutableStateOf(readDraftAttachments());private set
    var editingPlanId by mutableStateOf(prefs.getString("editing_plan_id","")!!);private set
    var checking by mutableStateOf(false); private set
    var configuring by mutableStateOf(false); private set
    var models by mutableStateOf(readModels()); private set
    var model by mutableStateOf(prefs.getString("direct_model","")!!); private set
    var modelStatus by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set
    var connectionStatus by mutableStateOf(if(hasKey)"DeepSeek · 手机直连" else "请在设置中保存 DeepSeek 密钥"); private set
    var input by mutableStateOf(prefs.getString("input","")!!)
    var confirmedId by mutableStateOf(prefs.getString("confirmedId","")); private set
    private var scopeStart=prefs.getInt("scope_start",0)
    var pendingCommands by mutableStateOf(runCatching{AppCommands.parse(JSONArray(prefs.getString("pending_commands","[]")))}.getOrDefault(emptyList()));private set
    private var commandStamp=prefs.getString("command_stamp","")?:""
    var commandStatus by mutableStateOf(prefs.getString("last_command_execution","")?:"");private set
    private var inspection:JSONObject?=null
    var attachmentRequest by mutableIntStateOf(0);private set
    var attachmentKind by mutableStateOf("files");private set
    val confirmed: Boolean get()=result?.planId!=null&&result?.planId==confirmedId
    private var fetchedThisSession=false
    init {if(chat.migrated)attachmentStore.clearAll()}
    private fun readModels():List<AvailableModel> = runCatching{val a=JSONArray(prefs.getString("direct_models","[]"));(0 until a.length()).map {a.getJSONObject(it).let {AvailableModel(it.getString("id"),it.getString("name"),it.optInt("maxOutput",12000),it.optBoolean("supportsImages",it.getString("id") in setOf("deepseek-flash","deepseek-v4-flash-vision-exp")))}}}.getOrDefault(emptyList())
    private fun readMessages():List<ConversationMessage> = runCatching{val a=JSONArray(prefs.getString("messages","[]"));(0 until a.length()).map {a.getJSONObject(it).let {ConversationMessage(it.getString("role"),it.getString("content"),it.getString("at"),ChatAttachment.decode(it.optJSONArray("attachments")))}}}.getOrDefault(emptyList())
    private fun readDraftAttachments()=runCatching{ChatAttachment.decode(JSONArray(prefs.getString("draft_attachments","[]")))}.getOrDefault(emptyList())
    private fun persistAttachments(){prefs.edit().putString("draft_attachments",JSONArray().apply{draftAttachments.forEach{put(it.json())}}.toString()).apply();persist()}
    fun addAttachments(uris:List<android.net.Uri>,expectedChatId:String=activeChatId){
        if(working)return
        if(expectedChatId!=activeChatId){error="聊天已切换，请在当前聊天重新选择附件。";return}
        if(draftAttachments.size+uris.size>6){error="每条消息最多添加6个附件。";return}
        attaching=true;error=null
        scope.launch{try{for(uri in uris){try{val attachment=withContext(Dispatchers.IO){attachmentStore.importFile(uri)};draftAttachments=draftAttachments+attachment;persistAttachments()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"附件读取失败，请重试。"}}}finally{attaching=false}}
    }
    fun removeAttachment(id:String){if(working)return;val target=draftAttachments.firstOrNull{it.id==id}?:return;draftAttachments=draftAttachments.filterNot{it.id==id};persistAttachments();scope.launch(Dispatchers.IO){attachmentStore.remove(target)}}
    fun attachmentThumbnail(path:String)=attachmentStore.thumbnail(path)
    fun selectPlanToEdit(id:String){
        if(working)return
        editingPlanId=id;scopeStart=messages.size;activeChatId=java.util.UUID.randomUUID().toString();result=null;confirmedId="";questionRound=0;answerDraft="{}";generationRequested=false;previewStale=false;inspection=null;discardCommands()
        prefs.edit().putString("editing_plan_id",id).putInt("scope_start",scopeStart).remove("result").remove("confirmedId").putInt("question_round",0).putString("answer_draft","{}").putBoolean("generation_requested",false).putBoolean("preview_stale",false).apply();persist()
    }
    private fun persist(){prefs.edit().putString("messages",JSONArray().apply{messages.forEach{put(it.json())}}.toString()).putString("input",input).putString("confirmedId",confirmedId).apply()}
    fun clearChat(){
        if(working)return
        val old=(messages.flatMap{it.attachments}+draftAttachments).distinctBy{it.id}
        chat.clear();messages=emptyList();draftAttachments=emptyList();result=null;input="";confirmedId="";editingPlanId="";scopeStart=0;questionRound=0;answerDraft="{}";generationRequested=false;previewStale=false;pendingCommands=emptyList();commandStatus="";inspection=null;error=null;activeChatId=java.util.UUID.randomUUID().toString()
        scope.launch(Dispatchers.IO){old.forEach{attachmentStore.remove(it)}}
    }
    private fun appSnapshot(existing:List<Step>):JSONObject {
        val app=getApplication<Application>();val settings=app.getSharedPreferences("daystep",0)
        return AppControlSnapshot.build(existing,editingPlanId,LocalDate.now(),settings.getString("theme","跟随系统")!!,settings.getBoolean("dynamic_color",true),TaskReminders.enabled(app),customPrompt,model,models,draftAttachments,inspection,commandStatus)
    }
    private fun stamp(existing:List<Step>):String {
        val app=getApplication<Application>();val settings=app.getSharedPreferences("daystep",0)
        return AppControlSnapshot.stamp(existing,settings.getString("theme","跟随系统")!!,settings.getBoolean("dynamic_color",true),TaskReminders.enabled(app),customPrompt,model,draftAttachments)
    }
    fun discardCommands(){pendingCommands=emptyList();commandStamp="";prefs.edit().remove("pending_commands").remove("command_stamp").apply()}
    fun commandsForExecution(existing:List<Step>):List<AppCommand>?=try{
        check(!working&&!checking&&!configuring){"正在处理，请稍后操作。"}
        check(pendingCommands.isNotEmpty()){ "没有待确认的操作。" }
        check(commandStamp==stamp(existing)){"任务或设置已变化，请重新描述要求，生成新的操作预览。"}
        AppCommands.validate(pendingCommands,existing,emptySet(),models.map{it.id}.toSet(),draftAttachments.map{it.id}.toSet())
        pendingCommands.toList()
    }catch(e:Exception){error=e.message;null}
    fun commandsFinished(message:String){discardCommands();commandStatus=message;prefs.edit().putString("last_command_execution",message).apply();messages=messages+ConversationMessage("assistant",AiReplyCodec.emptyClarification(message).toString(),OffsetDateTime.now().toString());persist()}
    fun commandFailed(message:String){error=message}
    fun requestAttachment(kind:String){attachmentKind=kind;attachmentRequest++}
    fun generatePlan(existing:List<Step>){send("请根据当前已确认的信息生成完整每日计划。无需再追问非关键细节；若关键日期或交付要求仍缺失，请指出缺项，不要编造。"+(if(input.isNotBlank())"\n补充：$input" else ""),existing,generateNow=true)}
    fun draftInput(value:String){input=value;prefs.edit().putString("input",value).apply()}
    fun savePrompt(value:String){customPrompt=PromptPolicy.save(prefs,value)}
    fun selectModel(id:String){if(busy||checking||configuring)return;if(models.none{it.id==id})return;model=id;prefs.edit().putString("direct_model",id).apply();connectionStatus="DeepSeek · $id · 手机直连";modelStatus="已选择 $id，下一条消息生效。"}
    fun checkConnection(){if(hasKey&&!fetchedThisSession&&!checking&&!configuring){fetchedThisSession=true;refreshModels()}}
    fun saveKeyAndFetch(value:String,finished:()->Unit){
        if(busy||checking||configuring)return
        val key=value.trim()
        if(key.isNotEmpty()&&(key.length>512||key.any{it.isWhitespace()})){modelStatus="密钥格式无效，请重新填写。";return}
        if(key.isBlank()&&!hasKey){modelStatus="请先填写 DeepSeek API 密钥。";return}
        configuring=true;modelStatus="正在加密保存并获取模型…"
        scope.launch{
            try{
                if(key.isNotEmpty()){
                    withContext(Dispatchers.IO){vault.save(key)};hasKey=true;models=emptyList();model="";prefs.edit().remove("direct_models").remove("direct_model").apply();finished()
                }
                applyModels(withContext(Dispatchers.IO){client.models(vault.read())});fetchedThisSession=true
            }catch(e:CancellationException){throw e}catch(e:Exception){modelStatus=message(e);connectionStatus=if(hasKey)"密钥已保存在手机，模型获取未成功" else "密钥保存失败"}finally{configuring=false}
        }
    }
    private fun applyModels(list:List<AvailableModel>){
        models=list
        if(list.none{it.id==model})model=list.first().id
        prefs.edit().putString("direct_models",JSONArray().apply{list.forEach{put(it.json())}}.toString()).putString("direct_model",model).apply()
        modelStatus="已从 DeepSeek 获取 ${list.size} 个模型，点击下方按钮切换。";connectionStatus="DeepSeek · $model · 手机直连"
    }
    fun refreshModels(){
        if(checking||configuring||busy)return
        if(!hasKey){modelStatus="请先在上方填写并保存 API 密钥。";return}
        checking=true;modelStatus="正在从 DeepSeek 获取模型…"
        scope.launch{try{applyModels(withContext(Dispatchers.IO){client.models(vault.read())});fetchedThisSession=true}catch(e:CancellationException){throw e}catch(e:Exception){modelStatus=message(e);connectionStatus="模型刷新失败，可稍后重试"}finally{checking=false}}
    }
    private fun message(e:Exception)=when(e){is IllegalStateException->e.message?:"操作未完成，请重试。";is java.io.IOException->"无法连接 DeepSeek，请检查手机网络后重试。";else->"处理 AI 回复时发生异常，请重试。"}
    fun send(value:String,existingSteps:List<Step>,combineAnswers:Boolean=true,generateNow:Boolean=false){
        if(working||configuring||checking)return
        val chosen=if(combineAnswers)result?.questions.orEmpty().filter{selectedAnswer(it.id).isNotBlank()}.joinToString("\n"){"${it.prompt}：${selectedAnswer(it.id)}"}else ""
        val text=listOf(chosen,value.trim()).filter{it.isNotBlank()}.joinToString("\n").ifBlank{if(draftAttachments.isNotEmpty())"请根据附件内容帮助我规划或修改任务。"else ""};if(text.isBlank())return
        if(text.length>6000){error="单条消息请控制在6000字以内。";return}
        if(!hasKey){error="请先在看板右上角的设置中保存 DeepSeek 密钥。";return}
        if(models.none{it.id==model}){error="请先在设置中获取并选择一个可用模型。";return}
        if(editingPlanId.isNotBlank()&&existingSteps.none{planKey(it)==editingPlanId}){error="所选计划已删除，请重新选择要修改的计划。";return}
        if(confirmed&&editingPlanId.isBlank()){
            result=null;scopeStart=messages.size;questionRound=0;answerDraft="{}";prefs.edit().remove("result").putInt("scope_start",scopeStart).putInt("question_round",0).putString("answer_draft","{}").apply()
        }
        discardCommands();inspection=null
        val next=ConversationMessage("user",text,OffsetDateTime.now().toString(),draftAttachments)
        try{AttachmentContent.validate((messages+next).takeLast(40),models.first{it.id==model})}catch(e:IllegalStateException){error=e.message;return}
        generationRequested=generateNow;prefs.edit().putBoolean("generation_requested",generationRequested).apply();messages=messages+next;draftAttachments=emptyList();input="";persistAttachments();request(existingSteps)
    }
    fun retry(existingSteps:List<Step>){if(!working&&!configuring&&!checking&&messages.lastOrNull()?.role=="user")request(existingSteps)}
    private fun request(existing:List<Step>,inspectionPass:Int=0){
        val selected=models.firstOrNull{it.id==model}?:run{error="请先在设置中获取并选择可用模型。";return}
        val now=ZonedDateTime.now()
        val draft=prefs.getString("result",null)?.let{runCatching{JSONObject(it)}.getOrNull()}
        val newGoalAllowed=draft==null||draft.optString("status")=="ready"
        val oldStale=previewStale
        val requestStamp=stamp(existing)
        busy=true;error=null;result=null;previewStale=true;prefs.edit().putBoolean("preview_stale",true).apply()
        val context=JSONObject().put("now",now.toOffsetDateTime().toString()).put("today",now.toLocalDate().toString()).put("timeZone",now.zone.id)
            .put("interview",JSONObject().put("questionRoundsAsked",questionRound).put("generationButtonAvailable",canGeneratePlan).put("generationRequested",generationRequested).put("newGoalAllowed",newGoalAllowed).put("nextPhase",when{generationRequested->"用户点击生成计划，请优先生成";questionRound==0->"基础信息推荐选项";else->"可继续了解具体任务细节，不强制结束"}))

            .put("selectedPlanId",editingPlanId.takeIf{it.isNotBlank()}?:JSONObject.NULL)
            .put("calendar",PlanningCalendar.context())
            .put("mode",if(editingPlanId.isBlank())"new_plan"else "edit_plan")
            .put("app",appSnapshot(existing))
            .put("currentDraft",draft?.let{JSONObject(it.toString()).apply{remove("dailySteps")}}?:JSONObject.NULL)
            .put("existingPlans",PlanWire.existingPlans(existing))
        val requestMessages=messages.drop(scopeStart.coerceIn(0,messages.size)).takeLast(40)
        var followupStarted=false
        scope.launch{
            try{
                val json=withContext(Dispatchers.IO){
                    val payload=DeepSeekClient.payload(system,customPrompt,context,requestMessages,selected,attachmentStore::readImage)
                    val key=vault.read()
                    AiPlannerPipeline.execute(payload,selected.maxOutput,{body->client.chat(key,body)},{raw->
                        AppReplyValidation.validate(raw,ZonedDateTime.now(),existing,editingPlanId,questionRound,newGoalAllowed,models.map{it.id}.toSet(),draftAttachments.map{it.id}.toSet())
                    },{failure,attempt->prefs.edit().putString("last_ai_diagnostic",JSONObject().put("code",failure.code).put("attempt",attempt).put("model",selected.id).put("at",OffsetDateTime.now().toString()).toString()).apply()})
                }
                val commands=AppCommands.parse(AiReplyCodec.validateCommandEnvelope(json))
                if(commands.singleOrNull()?.type=="inspect_tasks"){
                    check(inspectionPass<3){"已查询三批任务，请缩小日期或计划范围后继续。"}
                    val pages=inspection?.optJSONArray("pages")?:JSONArray()
                    pages.put(JSONObject().put("query",commands.single().values).put("result",AppCommands.inspect(existing,commands.single())))
                    inspection=JSONObject().put("pages",pages).put("queryPasses",inspectionPass+1).put("maximumPasses",3)
                    result=draft?.let{PlannerResult.parse(it)};previewStale=oldStale;prefs.edit().putBoolean("preview_stale",oldStale).apply()
                    followupStarted=true;busy=false;request(existing,inspectionPass+1);return@launch
                }
                if(commands.isNotEmpty()||(json.optString("status")=="clarify"&&json.getJSONArray("questions").length()==0&&json.getJSONObject("task").optString("title").isBlank())){
                    result=draft?.let{PlannerResult.parse(it)};previewStale=oldStale;prefs.edit().putBoolean("preview_stale",oldStale).apply()
                    pendingCommands=commands;commandStamp=requestStamp
                    prefs.edit().putString("pending_commands",JSONArray().apply{commands.forEach{put(it.json())}}.toString()).putString("command_stamp",commandStamp).apply()
                    messages=messages+ConversationMessage("assistant",json.toString(),OffsetDateTime.now().toString());persist();return@launch
                }
                val parsed=PlannerResult.parse(json);result=parsed;messages=messages+ConversationMessage("assistant",json.toString(),OffsetDateTime.now().toString())
                questionRound=InterviewPolicy.nextRound(json,questionRound,newGoalAllowed);answerDraft="{}";prefs.edit().putInt("question_round",questionRound).putString("answer_draft",answerDraft).apply()
                previewStale=false;prefs.edit().putString("result",json.toString()).putBoolean("preview_stale",false).remove("last_ai_diagnostic").apply();persist();connectionStatus="DeepSeek 已响应 · $model"
            }catch(e:CancellationException){throw e}catch(e:Exception){result=draft?.let{runCatching{PlannerResult.parse(it)}.getOrNull()};error=message(e)}finally{if(!followupStarted)busy=false}
        }
    }

    fun confirmableSteps(existing: List<Step>): List<Step>? {
        val r=result ?: return null
        if(busy || confirmed || r.status!="ready" || r.planId==null) return null
        if(previewStale){error="本次修改尚未生成成功，请先重试；原草稿已保留。";return null}
        val today=LocalDate.now().toString()
        if(r.actions.any {it.date<today}) {error="计划日期已过，请发送“按当前日期重新规划”。";return null}
        if(r.targetPlanId!=null&&existing.none{planKey(it)==r.targetPlanId}){error="原计划已删除，不能保存这份修改。";return null}
        if(editingPlanId.isNotBlank()&&r.targetPlanId!=editingPlanId){error="请先描述修改要求，生成所选计划的新预览后再确认。";return null}
        val end=r.task.getString("deadline")
        val time=r.task.optString("deadlineTime")
        return r.actions.mapIndexed {i,s -> Step("${r.planId}-$i",r.task.getString("title"),s.title,s.date,"$end${if(r.task.isNull("deadlineTime")) "" else " $time"}",s.minutes,definitionOfDone=s.definitionOfDone,planId=r.targetPlanId?:r.planId,shortProject=r.shortTitle)}
    }
    fun mergeConfirmed(existing:List<Step>,next:List<Step>)=mergeRevision(existing,next,result?.targetPlanId)
    fun markConfirmed() {confirmedId=result?.planId;previewStale=false;prefs.edit().putBoolean("preview_stale",false).apply();error=null;persist()}
    fun invalidatePlanPreview(){if(result!=null&&!confirmed){previewStale=true;prefs.edit().putBoolean("preview_stale",true).apply()}}
    fun planDeleted(id:String){
        if(editingPlanId==id)selectPlanToEdit("")
        if(result?.targetPlanId==id||result?.planId==id){result=null;confirmedId="";previewStale=false;prefs.edit().remove("result").remove("preview_stale").apply();persist()}
    }
    override fun onCleared() {super.onCleared();scope.cancel()}
}

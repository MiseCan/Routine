@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.routine

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.compose.animation.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class CompletionRecord(val note:String,val at:String)
data class Step(val id: String, val project: String, val title: String, val date: String, val ddl: String, val minutes: Int, val done: Boolean = false, val definitionOfDone: String = "",val planId:String="",val completions:List<CompletionRecord> = emptyList(),val shortProject:String="")

class MainActivity : ComponentActivity() {
    private var boardRequest by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val planner=ViewModelProvider(this)[PlannerState::class.java]
        val store=ViewModelProvider(this)[RoutineState::class.java]
        if(intent.getBooleanExtra(TaskReminders.OPEN_TODAY,false))boardRequest++
        setContent { Routine(planner,store,intent.getBooleanExtra("open_settings",false),boardRequest) }
    }
    override fun onNewIntent(intent:android.content.Intent){
        super.onNewIntent(intent);setIntent(intent)
        if(intent.getBooleanExtra(TaskReminders.OPEN_TODAY,false))boardRequest++
    }
}

internal fun encode(steps: List<Step>) = JSONArray().apply { steps.forEach { s -> put(JSONObject().put("id", s.id).put("project", s.project).put("shortProject",s.shortProject).put("title", s.title).put("date", s.date).put("ddl", s.ddl).put("minutes", s.minutes).put("done", s.done).put("definitionOfDone",s.definitionOfDone).put("planId",planKey(s)).put("completions",JSONArray().apply{s.completions.forEach{put(JSONObject().put("note",it.note).put("at",it.at))}})) } }.toString()
internal fun decode(raw: String): List<Step> = runCatching {
    val a = JSONArray(raw)
    (0 until a.length()).map { i -> a.getJSONObject(i).let {val logs=it.optJSONArray("completions")?:JSONArray(); Step(it.getString("id"), it.getString("project"), it.getString("title"), it.getString("date"), it.getString("ddl"), it.getInt("minutes"), it.getBoolean("done"),it.optString("definitionOfDone",""),it.optString("planId",""),(0 until logs.length()).map{j->logs.getJSONObject(j).let{r->CompletionRecord(r.getString("note"),r.getString("at"))}},it.optString("shortProject")) }.let{it.copy(planId=planKey(it))} }
}.getOrDefault(emptyList())

@Composable private fun Routine(planner: PlannerState,store:RoutineState,openSettings: Boolean=false,boardRequest:Int=0) {
    val context = LocalContext.current
    val saved by store.uiState.collectAsStateWithLifecycle()
    val ai by planner.uiState.collectAsStateWithLifecycle()
    val theme=saved.theme
    val dynamicColor=saved.dynamicColor
    val controller=rememberRoutineNavigator()
    LaunchedEffect(openSettings){if(openSettings)controller.navigate("settings")}
    var completionId by rememberSaveable{mutableStateOf<String?>(null)}
    var choosingChatPlan by remember{mutableStateOf(false)}
    var clearingChat by remember{mutableStateOf(false)}
    var boardDestination by rememberSaveable{mutableStateOf("today")}
    var boardNavigation by rememberSaveable{mutableIntStateOf(0)}
    val steps=saved.steps
    ReminderPermissionSetup()
    LaunchedEffect(boardRequest){if(boardRequest>0){controller.openTopLevel("board");completionId=null}}
    var today by remember { mutableStateOf(LocalDate.now()) }
    // Refresh the calendar day on resume, including after the app crosses midnight.
    DisposableEffect(context) {
        val owner = context as ComponentActivity
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {today = LocalDate.now();TaskReminders.sync(context)} }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val dark = theme == "深色" || (theme == "跟随系统" && androidx.compose.foundation.isSystemInDarkTheme())
    val colors = if (dynamicColor) {
        if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFB9CC9C), primaryContainer = Color(0xFF354D25), onPrimaryContainer = Color(0xFFDCEEC8), secondaryContainer = Color(0xFF3E4935), onSecondaryContainer = Color(0xFFE0E8D4), tertiaryContainer = Color(0xFF594431), onTertiaryContainer = Color(0xFFFFDEC2))
        else lightColorScheme(primary = Color(0xFF47652E), onPrimary = Color.White, primaryContainer = Color(0xFFC9EDA5), onPrimaryContainer = Color(0xFF142A05), secondaryContainer = Color(0xFFE0E8D4), tertiaryContainer = Color(0xFFFFDEC2), onTertiaryContainer = Color(0xFF3D2815), background = Color(0xFFF8FAF0), surface = Color(0xFFF8FAF0), surfaceContainer = Color(0xFFEEF2E5), surfaceContainerHigh = Color(0xFFE7EBDD))
    fun save(next: List<Step>) { store.saveSteps(next);planner.invalidatePlanPreview() }
    fun updateStep(updated: Step) { save(steps.map { if (it.id == updated.id) updated else it }) }
    fun applyAiCommands(){
        val commands=planner.commandsForExecution(steps)?:return
        try{
            if(commands.singleOrNull()?.type=="confirm_plan"){
                val next=planner.confirmableSteps(steps)?:throw IllegalStateException("当前没有可确认的计划预览，请先生成计划。")
                save(planner.mergeConfirmed(steps,next));planner.markConfirmed();planner.commandsFinished("已确认计划并更新看板。");return
            }
            val before=steps
            val next=AppCommands.applySteps(steps,commands,java.time.OffsetDateTime.now().toString())
            if(next!=before){save(next);(before.map{planKey(it)}.toSet()-next.map{planKey(it)}.toSet()).forEach{planner.planDeleted(it)}}
            for(command in commands){val values=command.values;when(command.type){
                "update_settings"->{
                    if(values.has("theme")){store.setTheme(values.getString("theme"))}
                    if(values.has("dynamicColor")){store.setDynamicColor(values.getBoolean("dynamicColor"))}
                    if(values.has("remindersEnabled"))TaskReminders.setEnabled(context,values.getBoolean("remindersEnabled"))
                    if(values.has("personalPrompt"))planner.savePrompt(values.getString("personalPrompt"))
                }
                "select_model"->planner.selectModel(command.targetId!!)
                "refresh_models"->planner.refreshModels()
                "select_plan"->planner.selectPlanToEdit(command.targetId?:"")
                "remove_attachment"->planner.removeAttachment(command.targetId!!)
                "clear_chat"->{planner.clearChat();return}
                "add_attachment"->planner.requestAttachment(values.getString("kind"))
                "navigate"->{when(val destination=values.getString("page")){
                    "settings","about"->controller.navigate(destination)
                    "chat"->controller.openTopLevel("chat")
                    "plan"->{controller.openPlan(command.targetId!!)}
                    else->{boardDestination=destination;boardNavigation++;controller.openTopLevel("board")}
                }}
            }}
            val note=when(commands.singleOrNull()?.type){"refresh_models"->"已发起模型刷新，结果请在设置中查看。";"add_attachment"->"已打开附件选择器，请选择要发送的内容。";"generate_plan"->"正在根据已确认的信息生成计划预览。";else->"已执行 ${commands.size} 项操作。"}+if(commands.any{it.type=="update_settings"&&it.values.optBoolean("remindersEnabled",false)}&&!TaskReminders.permitted(context))" 请在设置中允许通知后接收提醒。"else ""
            planner.commandsFinished(note)
            if(commands.singleOrNull()?.type=="generate_plan")planner.generatePlan(steps)
        }catch(e:Exception){planner.commandFailed(e.message?:"操作未执行，请重试。")}
    }
    MaterialExpressiveTheme(colorScheme = animatedColors(colors),motionScheme=MotionScheme.expressive()) {
        if(choosingChatPlan)ChatPlanPicker(steps,ai.editingPlanId,{planner.selectPlanToEdit(it)},{choosingChatPlan=false})
        if(clearingChat)MotionDialog(onDismissRequest={clearingChat=false},title={Text("清空聊天？")},text={Text("删除此会话的消息、附件和未保存草稿，保留看板中的计划。")},confirmLabel="清空",destructive=true,onConfirm={planner.clearChat();clearingChat=false})
        steps.firstOrNull{it.id==completionId}?.let{step->CompletionDialog(step,{completionId=null}){updated->save(steps.map{if(it.id==updated.id)updated else it});completionId=null}}
        RoutineNavigation(controller,Modifier.fillMaxSize()){screen,planId->
            RoutinePage(page=screen,
                title=when(screen){"chat"->chatPlanTitle(ai,steps);"settings"->"设置";"about"->"关于";"plan"->steps.firstOrNull{planKey(it)==planId}?.project?:"计划详情";else->"Routine"},
                onBack={controller.navigateUp()},
                onAction={if(screen=="chat")clearingChat=true else if(screen=="board")controller.navigate("settings")},
                onSelectPage={controller.openTopLevel(it)},
                onTitleClick=if(screen=="chat")({if(!ai.working)choosingChatPlan=true})else null,
                actionEnabled=screen!="chat"||!ai.working){
                when(screen){
                    "board" -> Board(steps,today,{completionId=it},{controller.openTopLevel("chat")},{controller.openPlan(it)},{id->save(deletePlan(steps,id));planner.planDeleted(id)},::updateStep,boardRequest,boardDestination,boardNavigation)
                    "chat" -> key(ai.activeChatId){AiChatContent(planner,steps,::applyAiCommands){next->save(planner.mergeConfirmed(steps,next));planner.markConfirmed()}}
                    "about" -> AboutPage()
                    "plan" -> PlanDetail(steps.filter{planKey(it)==planId},{completionId=it},::updateStep,{title,ddl->save(steps.map{if(planKey(it)==planId)it.copy(project=title,ddl=ddl,planId=planId,shortProject=if(title!=it.project)""else it.shortProject)else it})})
                    "settings" -> Settings(theme,store::setTheme,planner,dynamicColor,store::setDynamicColor,{controller.navigate("about")})
                }
            }
        }
    }
}

@Composable internal fun Bubble(text: String, mine: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if(mine) Arrangement.End else Arrangement.Start) {
        Surface(color = if(mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = if(mine) 24.dp else 6.dp, bottomEnd = if(mine) 6.dp else 24.dp), modifier = Modifier.widthIn(max = 330.dp)) {
            Text(text, Modifier.padding(18.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable private fun Settings(theme: String, setTheme: (String) -> Unit, planner: PlannerState,dynamicColor: Boolean,setDynamicColor: (Boolean)->Unit,about:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ReminderSettings()
        AiSettings(planner)
        SettingGroup("外观", MaterialSymbols.Settings) {
            Text("主题模式", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("跟随系统", "浅色", "深色").forEach { label -> FilterChip(selected = theme == label, onClick = { setTheme(label) }, label = { Text(label) }) } }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {Text("动态取色",style=MaterialTheme.typography.titleMedium);Text("跟随系统壁纸配色，自动适配浅色与深色。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Switch(checked=dynamicColor,onCheckedChange=setDynamicColor)
            }
        }
        OutlinedCard(onClick=about,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Info,null);Text("关于",Modifier.weight(1f).padding(start=12.dp));Text("0.16.3")}}
    }
}

@Composable internal fun SettingGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    }
}

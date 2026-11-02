@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.daystep.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Build
import androidx.compose.animation.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
        if(intent.getBooleanExtra(TaskReminders.OPEN_TODAY,false))boardRequest++
        setContent { Daystep(planner,intent.getBooleanExtra("open_settings",false),boardRequest) }
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

@Composable private fun Daystep(planner: PlannerState,openSettings: Boolean=false,boardRequest:Int=0) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("daystep", 0) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "跟随系统")!!) }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("dynamic_color", true)) }
    LaunchedEffect(Unit){prefs.edit().remove("capacity").apply()}
    var page by rememberSaveable { mutableStateOf(if(openSettings)"settings" else "board") }
    var selectedPlan by rememberSaveable{mutableStateOf("")}
    var completionId by rememberSaveable{mutableStateOf<String?>(null)}
    var choosingChatPlan by remember{mutableStateOf(false)}
    var clearingChat by remember{mutableStateOf(false)}
    var boardDestination by rememberSaveable{mutableStateOf("today")}
    var boardNavigation by rememberSaveable{mutableIntStateOf(0)}
    val pages = rememberSaveableStateHolder()
    var steps by remember { mutableStateOf(decode(prefs.getString("steps", "[]")!!)) }
    ReminderPermissionSetup()
    LaunchedEffect(boardRequest){if(boardRequest>0){page="board";completionId=null;selectedPlan=""}}
    var today by remember { mutableStateOf(LocalDate.now()) }
    // Refresh the calendar day on resume, including after the app crosses midnight.
    DisposableEffect(context) {
        val owner = context as ComponentActivity
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {today = LocalDate.now();TaskReminders.sync(context)} }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val dark = theme == "深色" || (theme == "跟随系统" && androidx.compose.foundation.isSystemInDarkTheme())
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFB9CC9C), primaryContainer = Color(0xFF354D25), secondaryContainer = Color(0xFF3E4935), tertiaryContainer = Color(0xFF594431))
        else lightColorScheme(primary = Color(0xFF47652E), onPrimary = Color.White, primaryContainer = Color(0xFFC9EDA5), onPrimaryContainer = Color(0xFF142A05), secondaryContainer = Color(0xFFE0E8D4), tertiaryContainer = Color(0xFFFFDEC2), onTertiaryContainer = Color(0xFF3D2815), background = Color(0xFFF8FAF0), surface = Color(0xFFF8FAF0), surfaceContainer = Color(0xFFEEF2E5), surfaceContainerHigh = Color(0xFFE7EBDD))
    fun save(next: List<Step>) { steps = next; prefs.edit().putString("steps", encode(next)).apply();planner.invalidatePlanPreview();TaskReminders.sync(context) }
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
                    if(values.has("theme")){theme=values.getString("theme");prefs.edit().putString("theme",theme).apply()}
                    if(values.has("dynamicColor")){dynamicColor=values.getBoolean("dynamicColor");prefs.edit().putBoolean("dynamic_color",dynamicColor).apply()}
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
                    "settings","about","chat"->page=destination
                    "plan"->{selectedPlan=command.targetId!!;page="plan"}
                    else->{boardDestination=destination;boardNavigation++;page="board"}
                }}
            }}
            val note=when(commands.singleOrNull()?.type){"refresh_models"->"已发起模型刷新，结果请在设置中查看。";"add_attachment"->"已打开附件选择器，请选择要发送的内容。";"generate_plan"->"正在根据已确认的信息生成计划预览。";else->"已执行 ${commands.size} 项操作。"}+if(commands.any{it.type=="update_settings"&&it.values.optBoolean("remindersEnabled",false)}&&!TaskReminders.permitted(context))" 请在设置中允许通知后接收提醒。"else ""
            planner.commandsFinished(note)
            if(commands.singleOrNull()?.type=="generate_plan")planner.generatePlan(steps)
        }catch(e:Exception){planner.commandFailed(e.message?:"操作未执行，请重试。")}
    }
    MaterialExpressiveTheme(colorScheme = animatedColors(colors),motionScheme=MotionScheme.expressive()) {
        if(choosingChatPlan)ChatPlanPicker(steps,planner.editingPlanId,{planner.selectPlanToEdit(it)},{choosingChatPlan=false})
        if(clearingChat)MotionDialog(onDismissRequest={clearingChat=false},title={Text("清空聊天？")},text={Text("删除此会话的消息、附件和未保存草稿，保留看板中的计划。")},confirmLabel="清空",destructive=true,onConfirm={planner.clearChat();clearingChat=false})
        steps.firstOrNull{it.id==completionId}?.let{step->CompletionDialog(step,{completionId=null}){updated->save(steps.map{if(it.id==updated.id)updated else it});completionId=null}}
        BackHandler(page != "board") {page = if(page=="about")"settings" else "board"}
        Scaffold(
            topBar = {
                DaystepTopBar(
                    title=when(page){"chat"->chatPlanTitle(planner,steps);"settings"->"设置";"about"->"关于";"plan"->steps.firstOrNull{planKey(it)==selectedPlan}?.project?:"计划详情";else->"日序"},
                    onTitleClick=if(page=="chat")({if(!planner.working)choosingChatPlan=true})else null,
                    showBack=page in listOf("settings","about","plan"),
                    onBack={page=if(page=="about")"settings"else "board"},
                    action=when(page){"chat"->TopBarAction.ClearChat;"board"->TopBarAction.Settings;else->TopBarAction.None},
                    actionEnabled=page!="chat"||!planner.working,
                    onAction={if(page=="chat")clearingChat=true else if(page=="board")page="settings"}
                )
            },
            bottomBar = { MotionVisibility(page in listOf("board","chat")){ NavigationBar {
                NavigationBarItem(selected = page == "board", onClick = { page = "board" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("看板") })
                NavigationBarItem(selected = page == "chat", onClick = {page = "chat" }, icon = { Icon(Icons.Default.Email, null) }, label = { Text("AI 聊天") })
            } } }
        ) { padding ->
            MotionPages(page,Modifier.fillMaxSize().padding(padding)) { screen ->
                pages.SaveableStateProvider(screen) { when(screen) {
                    "board" -> Board(steps, today, {completionId=it}, {page = "chat" },{selectedPlan=it;page="plan"},{id->save(deletePlan(steps,id));planner.planDeleted(id)},boardRequest,boardDestination,boardNavigation)
                    "chat" -> key(planner.activeChatId){AiChatContent(planner,steps,::applyAiCommands) { next -> save(planner.mergeConfirmed(steps,next)); planner.markConfirmed(); }}
                    "about" -> AboutPage()
                    "plan" -> PlanDetail(steps.filter{planKey(it)==selectedPlan},{completionId=it},{updated->save(steps.map{if(it.id==updated.id)updated else it})},{title,ddl->save(steps.map{if(planKey(it)==selectedPlan)it.copy(project=title,ddl=ddl,planId=selectedPlan,shortProject=if(title!=it.project)""else it.shortProject)else it})})
                    else -> Settings(theme, { theme = it; prefs.edit().putString("theme", it).apply() },planner,dynamicColor,{dynamicColor=it;prefs.edit().putBoolean("dynamic_color",it).apply()},{page="about"})
                } }
            }
        }
    }
}

@Composable private fun Board(steps: List<Step>, today: LocalDate, toggle: (String) -> Unit, chat: () -> Unit,openPlan:(String)->Unit,delete:(String)->Unit,boardRequest:Int=0,destination:String="today",navigation:Int=0) {
    val todaySteps = steps.filter { it.date == today.toString() }
    val overdue = steps.filter { it.date < today.toString() && !it.done }
    val upcoming = tomorrowSteps(steps,today)
    val done = todaySteps.count { it.done }
    var filter by rememberSaveable { mutableStateOf("今天") }
    var view by rememberSaveable{mutableStateOf("今日任务")}
    LaunchedEffect(boardRequest){if(boardRequest>0){filter="今天";view="今日任务"}}
    LaunchedEffect(navigation){if(navigation>0){view=if(destination=="plans")"所有计划"else "今日任务";filter=when(destination){"overdue"->"待补做";"tomorrow"->"接下来";else->"今天"}}}
    val shown = when(filter) { "待补做" -> overdue; "接下来" -> upcoming; else -> todaySteps }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(today.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", java.util.Locale.SIMPLIFIED_CHINESE)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("今日进度  $done / ${todaySteps.size}",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                    Text("预计 ${todaySteps.sumOf{it.minutes}} 分钟",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
                }
                MotionProgress(value=if(todaySteps.isEmpty())0f else done.toFloat()/todaySteps.size, modifier = Modifier.fillMaxWidth().height(4.dp))
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("今日任务","所有计划").forEach{label->FilterChip(selected=view==label,onClick={view=label},label={Text(label)})}}
        MotionContent(view){activeView -> Column(verticalArrangement=Arrangement.spacedBy(12.dp)){ if(activeView=="所有计划"){AllPlans(steps,openPlan,delete)}else{
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("今天", "待补做", "接下来").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(if(label == "待补做" && overdue.isNotEmpty()) "$label ${overdue.size}" else label) }) }
        }
        MotionContent(filter){activeFilter -> Column(verticalArrangement=Arrangement.spacedBy(12.dp)){ val shown=when(activeFilter){"待补做"->overdue;"接下来"->upcoming;else->todaySteps}
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if(activeFilter == "今天") "今日行动" else activeFilter, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${shown.size} 项", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (shown.isEmpty()) Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("暂无任务", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = chat, shapes = ButtonDefaults.shapes()) { Text("和 AI 规划任务") }
            }
        }
        MotionList(shown,{it.id}) { step ->
            Surface(shape = RoundedCornerShape(24.dp), color = if(step.done) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(checked = step.done, onCheckedChange = { toggle(step.id) })
                    Column(Modifier.weight(1f).padding(top = 10.dp, end = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(step.project, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(step.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        if(step.definitionOfDone.isNotBlank()) Text("完成标准：${step.definitionOfDone}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${step.minutes} 分钟 · ${step.date} · 截止 ${step.ddl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if(steps.isNotEmpty()) TextButton(shapes=ButtonDefaults.shapes(),onClick = chat, modifier = Modifier.fillMaxWidth()) { Text("＋ 规划一个新目标") }
        } } } } }
        Spacer(Modifier.height(12.dp))
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
                Column(Modifier.weight(1f)) {Text("动态取色",style=MaterialTheme.typography.titleMedium);Text(if(Build.VERSION.SDK_INT>=31)"跟随系统壁纸配色，自动适配浅色与深色。" else "需要 Android 12 或以上；当前使用默认配色。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Switch(checked=dynamicColor&&Build.VERSION.SDK_INT>=31,onCheckedChange=setDynamicColor,enabled=Build.VERSION.SDK_INT>=31)
            }
        }
        OutlinedCard(onClick=about,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Info,null);Text("关于",Modifier.weight(1f).padding(start=12.dp));Text("0.12.0")}}
    }
}

@Composable internal fun SettingGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    }
}

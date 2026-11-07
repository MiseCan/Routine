@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.routine

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

fun planKey(step:Step):String = step.planId.ifBlank {
    if(step.id.substringAfterLast('-').toIntOrNull()!=null&&step.id.contains('-'))step.id.substringBeforeLast('-') else "legacy:${step.project}:${step.ddl}"
}
fun deletePlan(steps:List<Step>,id:String)=steps.filterNot{planKey(it)==id}
fun tomorrowSteps(steps:List<Step>,today:LocalDate)=steps.filter{it.date==today.plusDays(1).toString()}
fun revisionBase(steps:List<Step>,target:String?)=if(target==null)steps else steps.filter{planKey(it)!=target||it.done||it.completions.isNotEmpty()}
fun mergeRevision(existing:List<Step>,next:List<Step>,target:String?):List<Step>{
    if(target==null)return(existing+next).distinctBy{it.id}
    require(existing.any{planKey(it)==target})
    val first=next.firstOrNull()?:return existing
    return(revisionBase(existing,target).map{if(planKey(it)==target)it.copy(project=first.project,ddl=first.ddl,shortProject=first.shortProject)else it}+next).distinctBy{it.id}
}
fun completedStep(step:Step,note:String,at:String):Step {
    require(note.isNotBlank()&&note.length<=4000)
    return step.copy(done=true,completions=step.completions+CompletionRecord(note.trim(),at))
}

@Composable internal fun AllPlans(steps:List<Step>,open:(String)->Unit,delete:(String)->Unit,header:(@Composable ()->Unit)?=null) {
    var filter by rememberSaveable{mutableStateOf("进行中")}
    var deleting by rememberSaveable{mutableStateOf<String?>(null)}
    val target=steps.filter{planKey(it)==deleting}
    if(target.isNotEmpty())MotionDialog(onDismissRequest={deleting=null},title={Text("删除计划？")},text={Text("将删除「${target.first().project}」及其全部 ${target.size} 项每日任务和完成记录，看板也会同步移除。此操作无法撤销。")},confirmLabel="删除",destructive=true,onConfirm={deleting?.let(delete);deleting=null})
    val groups=remember(steps){steps.groupBy{planKey(it)}.values.sortedWith(compareBy<List<Step>>{it.all{step->step.done}}.thenBy{it.first().ddl})}
    val shown=groups.filter{when(filter){"进行中"->it.any{step->!step.done};"已完成"->it.all{step->step.done};else->true}}
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(header!=null)item(key="plans:overview",contentType="overview"){header()}
            item(key="plans:filters",contentType="filters"){FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("进行中","已完成","全部").forEach{label->FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)})}}}
            if(shown.isEmpty())item{Text("暂无${if(filter=="全部")"" else filter}计划",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(shown,key={planKey(it.first())},contentType={"plan"}){items->
                val done=items.count{it.done}
                val interaction=remember{MutableInteractionSource()}
                Card(onClick={open(planKey(items.first()))},interactionSource=interaction,modifier=Modifier.animateItem().motionPress(interaction),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                    Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(items.first().project,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);TextButton(shapes=ButtonDefaults.shapes(),onClick={deleting=planKey(items.first())}){Text("删除",color=MaterialTheme.colorScheme.error)}}
                        Text("截止 ${items.first().ddl} · $done/${items.size} 项完成",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        MotionProgress(done.toFloat()/items.size,Modifier.fillMaxWidth().height(4.dp))
                        items.filterNot{it.done}.minByOrNull{it.date}?.let{Text("下一步：${it.title}",style=MaterialTheme.typography.bodyMedium,maxLines=2)}
                    }
                }
            }
        }
}

@Composable internal fun PlanDetail(items:List<Step>,toggle:(String)->Unit,update:(Step)->Unit,updatePlan:(String,String)->Unit) {
    var editingId by rememberSaveable{mutableStateOf<String?>(null)}
    var editingPlan by rememberSaveable{mutableStateOf(false)}
    var view by rememberSaveable{mutableStateOf("每日任务")}
    val editing=items.firstOrNull{it.id==editingId}
    if(editing!=null)StepEditor(editing,{editingId=null}){update(it);editingId=null}
    if(editingPlan&&items.isNotEmpty())PlanEditor(items,{editingPlan=false}){title,ddl->updatePlan(title,ddl);editingPlan=false}
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        if(items.isEmpty()){Text("计划不存在");return@Column}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("${items.count{it.done}} / ${items.size} 项完成",style=MaterialTheme.typography.titleMedium);Text("截止 ${items.first().ddl}",style=MaterialTheme.typography.bodySmall)}
            TextButton(shapes=ButtonDefaults.shapes(),onClick={editingPlan=true}){Text("编辑计划")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("每日任务","完成记录").forEach{label->FilterChip(selected=view==label,onClick={view=label},label={Text(label)})}}
        MotionContent(view,Modifier.weight(1f)){activeView->LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){if(activeView=="完成记录"){
            val records=items.flatMap{s->s.completions.map{s.title to it}}.sortedByDescending{it.second.at}
            if(records.isEmpty())item{Text("暂无完成记录",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            records.groupBy{it.second.at.take(10)}.forEach{(day,logs)->item(key="record-date:$day"){Text(day,fontWeight=FontWeight.Bold)};items(logs){(title,record)->Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(record.note);Text(record.at.take(16).replace('T',' '),style=MaterialTheme.typography.bodySmall)}}}}
        }else{
        items.sortedBy{it.date}.groupBy{it.date}.forEach{(date,actions)->
            item(key="date:$date"){Text("$date · ${actions.size} 项",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))}
            items(actions,key={it.id}){step->
                Surface(modifier=Modifier.animateItem(),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainer){
                    Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.Top){
                        Checkbox(checked=step.done,onCheckedChange={toggle(step.id)})
                        Column(Modifier.weight(1f).padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text(step.title,fontWeight=FontWeight.SemiBold)
                            if(step.definitionOfDone.isNotBlank())Text(step.definitionOfDone,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${step.minutes} 分钟",style=MaterialTheme.typography.bodySmall)
                            step.completions.lastOrNull()?.let{Text("${it.at.take(10)} 完成记录：${it.note}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
                        }
                        TextButton(shapes=ButtonDefaults.shapes(),onClick={editingId=step.id}){Text("编辑")}
                    }
                }
            }
        }
        } } }
    }
}

@Composable internal fun CompletionDialog(step:Step,dismiss:()->Unit,save:(Step)->Unit){
    var note by rememberSaveable(step.id){mutableStateOf("")}
    MotionDialog(onDismissRequest=dismiss,title={Text(if(step.done)"完成记录" else "本次完成了什么？")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(step.title,fontWeight=FontWeight.SemiBold)
        if(step.done){if(step.completions.isEmpty())Text("此任务尚无完成说明。");step.completions.forEach{Text("${it.at.take(16).replace('T',' ')}\n${it.note}")}}
        else OutlinedTextField(value=note,onValueChange={if(it.length<=4000)note=it},label={Text("完成内容（必填）")},minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth())
    }},onConfirm={if(step.done)save(step.copy(done=false))else save(completedStep(step,note,java.time.OffsetDateTime.now().toString()))},confirmEnabled=step.done||note.isNotBlank(),confirmLabel=if(step.done)"撤销完成" else "保存并完成",dismissLabel=if(step.done)"关闭" else "取消")
}

@Composable private fun StepEditor(step:Step,dismiss:()->Unit,save:(Step)->Unit) {
    var title by rememberSaveable(step.id){mutableStateOf(step.title)}
    var date by rememberSaveable(step.id){mutableStateOf(step.date)}
    var minutes by rememberSaveable(step.id){mutableStateOf(step.minutes.toString())}
    var standard by rememberSaveable(step.id){mutableStateOf(step.definitionOfDone)}
    val context=LocalContext.current
    val dateValid=runCatching{!LocalDate.parse(date).isAfter(LocalDate.parse(step.ddl.take(10)))}.getOrDefault(false)
    val valid=title.isNotBlank()&&title.length<=300&&dateValid&&minutes.toIntOrNull() in 5..480&&standard.length<=2000
    MotionDialog(onDismissRequest=dismiss,title={Text("编辑每日任务")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(value=title,onValueChange={title=it},label={Text("任务内容")},maxLines=5,modifier=Modifier.fillMaxWidth())
            OutlinedButton(shapes=ButtonDefaults.shapes(),onClick={val d=LocalDate.parse(date);DatePickerDialog(context,{_,y,m,day->date=LocalDate.of(y,m+1,day).toString()},d.year,d.monthValue-1,d.dayOfMonth).show()}){Text("日期：$date")}
            if(!dateValid)Text("任务日期不能晚于计划截止日期，请先编辑计划截止日期。",color=MaterialTheme.colorScheme.error)
            OutlinedTextField(value=minutes,onValueChange={minutes=it},label={Text("预计分钟数（5–480）")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=standard,onValueChange={standard=it},label={Text("完成标准")},maxLines=5,modifier=Modifier.fillMaxWidth())
        }
    },onConfirm={save(step.copy(title=title.trim(),date=date,minutes=minutes.toInt(),definitionOfDone=standard.trim()))},confirmEnabled=valid,confirmLabel="保存")
}

@Composable private fun PlanEditor(items:List<Step>,dismiss:()->Unit,save:(String,String)->Unit) {
    var title by rememberSaveable{mutableStateOf(items.first().project)}
    var ddl by rememberSaveable{mutableStateOf(items.first().ddl)}
    val valid=title.isNotBlank()&&title.length<=200&&runCatching{val end=LocalDate.parse(ddl.take(10));items.all{!LocalDate.parse(it.date).isAfter(end)}}.getOrDefault(false)
    val context=LocalContext.current
    MotionDialog(onDismissRequest=dismiss,title={Text("编辑计划")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedTextField(value=title,onValueChange={title=it},label={Text("计划名称")},modifier=Modifier.fillMaxWidth())
        OutlinedButton(shapes=ButtonDefaults.shapes(),onClick={val d=LocalDate.parse(ddl.take(10));DatePickerDialog(context,{_,y,m,day->ddl=LocalDate.of(y,m+1,day).toString()+ddl.drop(10)},d.year,d.monthValue-1,d.dayOfMonth).show()}){Text("截止：$ddl")}
        if(!valid)Text("名称不能为空，截止日期不能早于已有任务日期。",color=MaterialTheme.colorScheme.error)
    }},onConfirm={save(title.trim(),ddl)},confirmEnabled=valid,confirmLabel="保存")
}

@Composable internal fun AboutPage(){
    val context=LocalContext.current
    val builtInPrompt=remember{context.assets.open("planner-system.txt").bufferedReader().use{it.readText()}}
    var promptOpen by rememberSaveable{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        SettingGroup("Routine",androidx.compose.material.icons.Icons.Default.Info){
            Text("0.16.2",style=MaterialTheme.typography.headlineSmall)
            Text("通过 AI 对话识别任务和截止日期，补齐缺失信息，拆成每天可执行的行动。确认后的计划显示在看板。")
            Text("看板查看今日、待补做和后续任务；所有计划可查看进行中和已完成的计划，进入后可编辑每天的内容、日期、耗时和完成标准。")
            Text("“接下来”只显示明天的任务。删除计划需要确认，会同时移除其每日任务与完成记录。完成任务前需填写本次完成内容，计划详情的“完成记录”按实际完成日期展示。")
            Text("AI 聊天只有一个会话，默认创建新计划。点击顶部可选择正在修改的计划；顶部显示 AI 概括的七字以内简称，详情保留完整名称。清空聊天需确认，不删除看板计划。")
            Text("可用对话查询任务、管理计划与每日行动、填写完成记录、调整提醒和外观、切换模型和个性化要求、打开页面或附件选择器。写入操作先显示预览，确认后执行；任务变化后旧操作预览失效。密钥和系统授权由你在设置中处理。")
            Text("不强制三轮结束。第三轮询问后提供“生成计划”按钮，也可继续聊。每日计划在聊天中预览，可继续细改；修改确认后更新待办并保留完成记录。")
            Text("长期任务支持日期范围、按星期和节假日重复安排，由 App 展开为每天的独立任务。单天可编辑和记录完成；聊天预览可继续查看后续日期。")
            Text("周一至周五、周末按星期计算；工作日和节假日采用中国大陆已收录的调休日历（2026年），其他年份或自定义班表先确认具体规则。")
        }
        SettingGroup("AI 与数据",androidx.compose.material.icons.Icons.Default.Star){
            Text("手机通过 HTTPS 直接调用 DeepSeek；密钥在本机用 Android Keystore 加密保存。任务、对话和个性化提示词保存在本机。")
            Text("发送消息时，对话、个性化要求、设备时间和已有计划用于 AI 规划。模型列表来自 DeepSeek 接口。")
            Text("单项任务的预计耗时仅供参考。计划仅在你确认后保存，手动编辑不会自动重新安排其他任务。聊天预览每页展示七天，可前后翻页；看板、计划和消息采用按需渲染。页面返回到实际来源，切换看板和聊天会保留各自界面状态。")
        }
        SettingGroup("界面与提醒",MaterialSymbols.Settings){
            Text("采用 Material 3 Expressive，支持深浅主题和 Android 12 以上系统动态取色。Android 12 以上普通桌面图标也直接使用系统主色，并适配深浅模式；支持主题图标的桌面可使用单色图层自行着色。")
            Text("聊天可发送图片、PDF、DOCX 和文本文件；图片和 PDF 需要支持图片的模型。文件资料随聊天保存在本机，发送时交给所选模型。Word 仅读取正文。")
            Text("每天 8:00～23:00 每小时提醒今日尚未完成的任务，默认无声音、无振动，同一条通知更新。任务全部完成或没有任务时不发送；提醒在手机本地运行，无需网络或调用 AI。")
            Text("首次使用需允许通知。系统省电可能延迟提醒，可在设置中选择允许准点提醒。重启、修改时间或更新软件后会重新安排；系统强行停止应用后需重新打开。")
        }
        SettingGroup("内置提示词 · 只读",androidx.compose.material.icons.Icons.Default.Star){
            Text("内置规则管理提问、计划格式和确认流程，随软件版本更新，不可修改。个性化要求可在设置中单独添加；冲突时遵守内置规则，返回内容仍需通过软件校验。",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={promptOpen=!promptOpen},shapes=ButtonDefaults.shapes()){Text(if(promptOpen)"收起内置提示词"else "查看内置提示词")}
            MotionVisibility(promptOpen){androidx.compose.foundation.text.selection.SelectionContainer{Text(builtInPrompt,style=MaterialTheme.typography.bodySmall)}}
        }
    }
}

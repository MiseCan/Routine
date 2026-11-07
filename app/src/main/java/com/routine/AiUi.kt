@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.routine

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable internal fun AiChatContent(state: PlannerState, existing: List<Step>, executeCommands:()->Unit={}, confirm: (List<Step>)->Unit) {
    val ui by state.uiState.collectAsStateWithLifecycle()
    val scroll=rememberLazyListState()
    val result=ui.result
    var attachmentsMenu by remember{mutableStateOf(false)}
    var pickerChatId by rememberSaveable{mutableStateOf("")}
    val pickFiles=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty())state.addAttachments(uris,pickerChatId)}
    LaunchedEffect(ui.attachmentRequest){if(ui.attachmentRequest>0){pickerChatId=ui.activeChatId;state.consumeAttachmentRequest();pickFiles.launch(if(ui.attachmentKind=="images")arrayOf("image/*")else arrayOf("*/*"))}}
    LaunchedEffect(Unit) {state.checkConnection()}
    val followNewMessage=!scroll.canScrollForward
    LaunchedEffect(ui.messages.size){if(followNewMessage||ui.messages.lastOrNull()?.role=="user"){
        snapshotFlow{scroll.layoutInfo.totalItemsCount}.first{it>0};scroll.animateScrollToItem((scroll.layoutInfo.totalItemsCount-1).coerceAtLeast(0))
    }}
    fun send(value: String) {state.send(value,existing)}
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=scroll,contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item(key="intro"){
            if(!ui.hasKey) Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.tertiaryContainer) {Text("请先在设置中配置 DeepSeek。",Modifier.padding(16.dp))}
            if(ui.messages.isEmpty()) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("帮我规划一份课程报告", "我想准备一次项目汇报").forEach {example -> SuggestionChip(onClick={send(example)},enabled=!ui.working,label={Text(example)})}
            }
            }
            items(ui.messages,key={it.id},contentType={it.role}){message->Column(Modifier.animateItem(),verticalArrangement=Arrangement.spacedBy(6.dp)){Bubble(message.display,message.role=="user");AttachmentCards(message.attachments,state)}}
            item(key="commands"){
            MotionVisibility(ui.pendingCommands.isNotEmpty()&&!ui.working){Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.secondaryContainer){
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("待确认操作",style=MaterialTheme.typography.titleMedium)
                    AppCommands.summaries(ui.pendingCommands,existing,emptyMap()).forEach{Text(it)}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Button(onClick=executeCommands,enabled=!ui.checking&&!ui.configuring,shapes=ButtonDefaults.shapes()){Text("确认执行")}
                        TextButton(onClick={state.discardCommands()},shapes=ButtonDefaults.shapes()){Text("取消")}
                    }
                }
            }}
            }
            item(key="busy"){MotionVisibility(ui.busy) { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在理解任务并检查时间安排…",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            }
            item(key="plan-preview"){MotionContent(result){result -> if(result!=null && !ui.working) {
                if(result.questions.isNotEmpty()){
                    Text(if(result.targetPlanId!=null)"补充修改要求" else "第 ${ui.questionRound.coerceAtLeast(1)} 轮 · ${if(ui.questionRound<=1)"基础信息" else "任务细节"}",style=MaterialTheme.typography.titleMedium)
                    result.questions.forEach {q -> QuestionCard(q,ui.selectedAnswer(q.id)){state.selectAnswer(q.id,it)}}
                    val answered=result.questions.count{ui.selectedAnswer(it.id).isNotBlank()}
                    Button(onClick={state.submitAnswers(existing)},enabled=answered==result.questions.size&&!ui.working&&!ui.checking&&!ui.configuring,shapes=ButtonDefaults.shapes(),modifier=Modifier.fillMaxWidth()){Text("提交本轮信息（$answered/${result.questions.size}）")}
                }
                if(result.status=="ready" && result.actions.isNotEmpty()) {
                    var firstDay by rememberSaveable(result.planId){mutableIntStateOf(0)}
                    val visibleDays=7
                    val dailyGroups=result.actions.groupBy{it.date}
                    Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            Text("每日计划预览",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                            Text(result.task.getString("title"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            Text(result.task.getString("deliverable"))
                            Text("截止 ${result.task.getString("deadline")}${if(result.task.isNull("deadlineTime")) " · 当天结束" else " ${result.task.getString("deadlineTime")}"} · ${result.actions.size} 个行动",style=MaterialTheme.typography.bodyMedium)
                            if(result.scheduleSummaries.isNotEmpty()){
                                Text("重复安排 · 已展开为每日任务",fontWeight=FontWeight.SemiBold)
                                result.scheduleSummaries.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
                            }
                            dailyGroups.entries.drop(firstDay).take(visibleDays).forEach {(date,actions) ->
                                HorizontalDivider(color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.15f))
                                Text("$date · ${actions.size} 项",fontWeight=FontWeight.Bold)
                                actions.forEach {action ->
                                    Text("${action.title} · ${action.minutes} 分钟",fontWeight=FontWeight.SemiBold)
                                    Text("完成标准：${action.definitionOfDone}",style=MaterialTheme.typography.bodyMedium)
                                }
                            }
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                if(firstDay>0)TextButton(onClick={firstDay=(firstDay-7).coerceAtLeast(0)},shapes=ButtonDefaults.shapes()){Text("前7天")}
                                if(firstDay+7<dailyGroups.size)TextButton(onClick={firstDay+=7},shapes=ButtonDefaults.shapes()){Text("后7天")}
                            }
                            Text("第 ${firstDay+1}～${minOf(firstDay+7,dailyGroups.size)} 天 / 共 ${dailyGroups.size} 天",style=MaterialTheme.typography.labelSmall)
                            result.warnings.forEach {Text("提示：$it",style=MaterialTheme.typography.bodyMedium)}
                            Button(onClick={state.confirmableSteps(existing)?.let(confirm)},enabled=!ui.confirmed&&!ui.previewStale&&ui.result?.planId==result.planId&&!ui.working,modifier=Modifier.fillMaxWidth(),shapes=ButtonDefaults.shapes()) {Text(if(ui.confirmed)"已保存到看板" else if(ui.previewStale)"原草稿已保留，请先重试修改" else if(result.targetPlanId!=null)"确认修改，更新看板" else "确认计划，加入看板")}
                            Text("还想调整？直接在下方告诉我。新草稿会替换这份预览。",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                } else result.warnings.forEach {Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
            } }
            }
            item(key="error"){MotionContent(ui.error){message -> if(message!=null)Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(message,color=MaterialTheme.colorScheme.onErrorContainer)
                    if(ui.messages.lastOrNull()?.role=="user") TextButton(shapes=ButtonDefaults.shapes(),onClick={state.retry(existing)},enabled=!ui.working){Text("重试这条消息")}
                }
            }}
            }
            item(key="bottom-space"){Spacer(Modifier.height(4.dp))}
        }
        MotionVisibility(ui.canGeneratePlan){Button(onClick={state.generatePlan(existing)},enabled=!ui.working&&!ui.checking&&!ui.configuring&&ui.hasKey,modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp),shapes=ButtonDefaults.shapes()){Text(if(ui.busy)"生成中…" else "生成计划")}}
        if(ui.draftAttachments.isNotEmpty())Column(Modifier.heightIn(max=190.dp).verticalScroll(rememberScrollState()).padding(horizontal=12.dp)){AttachmentCards(ui.draftAttachments,state,state::removeAttachment)}
        if(ui.attaching)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=12.dp))
        if((ui.draftAttachments.any{it.images.isNotEmpty()}||ui.messages.takeLast(40).any{m->m.attachments.any{it.images.isNotEmpty()}})&&ui.models.firstOrNull{it.id==ui.model}?.supportsImages!=true){
            Column(Modifier.padding(horizontal=12.dp)){
                Text("图片和 PDF 需要支持图片的模型",style=MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){ui.models.filter{it.supportsImages}.forEach{model->SuggestionChip(onClick={state.selectModel(model.id)},enabled=!ui.working,label={Text("切换到 ${model.name}")})}}
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box{
                IconButton(onClick={attachmentsMenu=true},enabled=!ui.working,shapes=IconButtonDefaults.shapes()){Icon(Icons.Default.Add,"添加图片或文件")}
                DropdownMenu(expanded=attachmentsMenu,onDismissRequest={attachmentsMenu=false}){
                    DropdownMenuItem(text={Text("图片")},onClick={attachmentsMenu=false;pickerChatId=ui.activeChatId;pickFiles.launch(arrayOf("image/*"))})
                    DropdownMenuItem(text={Text("文件（PDF、Word、文本）")},onClick={attachmentsMenu=false;pickerChatId=ui.activeChatId;pickFiles.launch(arrayOf("*/*"))})
                }
            }
            OutlinedTextField(value=ui.input,onValueChange={state.draftInput(it)},placeholder={Text(if(result?.status=="ready") "告诉我需要怎样调整…" else "说说任务或补充信息…")},modifier=Modifier.weight(1f).animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),shape=RoundedCornerShape(28.dp),maxLines=4,enabled=!ui.working)
            FilledIconButton(onClick={send(ui.input)},shapes=IconButtonDefaults.shapes(),enabled=(ui.input.isNotBlank()||ui.draftAttachments.isNotEmpty())&&!ui.working&&!ui.checking&&!ui.configuring,modifier=Modifier.size(52.dp)){Icon(Icons.AutoMirrored.Filled.Send,"发送")}
        }
    }
}

@Composable private fun QuestionCard(question: AiQuestion,value:String,answer: (String)->Unit) {
    val context=LocalContext.current
    fun submit(value: String) {answer(value)}
    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(question.prompt,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                question.options.forEach {(label,optionValue) -> FilterChip(selected=value==optionValue,onClick={submit(optionValue)},label={Text(label)})}
            }
            when(question.kind) {
                "date" -> FilledTonalButton(onClick={val now=LocalDate.now(); DatePickerDialog(context,{_,y,m,d ->submit(LocalDate.of(y,m+1,d).toString())},now.year,now.monthValue-1,now.dayOfMonth).apply {datePicker.minDate=System.currentTimeMillis()-1000;show()}},shapes=ButtonDefaults.shapes()) {Icon(Icons.Default.DateRange,null);Spacer(Modifier.width(8.dp));Text("选择日期")}
                "time" -> FilledTonalButton(onClick={val now=LocalTime.now();TimePickerDialog(context,{_,h,m ->submit("%02d:%02d".format(h,m))},now.hour,now.minute,true).show()},shapes=ButtonDefaults.shapes()){Text("选择截止时刻")}
            }
            OutlinedTextField(value=value,onValueChange=answer,label={Text("自定义或修改答案")},maxLines=4,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
        }
    }
}

@Composable internal fun AiSettings(state: PlannerState) {
    val ui by state.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit){state.checkConnection()}
    var prompt by rememberSaveable{mutableStateOf(ui.customPrompt)}
    var promptExpanded by rememberSaveable{mutableStateOf(false)}
    var promptMessage by rememberSaveable{mutableStateOf("")}
    // Key input is never included in saved-instance state or ordinary preferences.
    var apiKey by remember{mutableStateOf("")}
    val working=ui.busy||ui.checking||ui.configuring
    SettingGroup("DeepSeek",Icons.Default.Star) {
        OutlinedTextField(value=apiKey,onValueChange={apiKey=it},label={Text(if(ui.hasKey)"更新 API 密钥" else "DeepSeek API 密钥")},placeholder={Text(if(ui.hasKey)"已加密保存，留空保留" else "填写开放平台 API 密钥")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),enabled=!working)
        Text(if(ui.hasKey)"密钥已用 Android Keystore 加密保存在本机，不回显完整内容。" else "密钥只加密保存在本机，通过 HTTPS 发送给 DeepSeek。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick={state.saveKeyAndFetch(apiKey){apiKey=""}},enabled=!working&&(apiKey.isNotBlank()||ui.hasKey),shapes=ButtonDefaults.shapes()){Text(if(ui.configuring)"保存并获取中…" else "保存密钥并获取模型")}
    }
    SettingGroup("选择模型",MaterialSymbols.Settings) {
        Text(if(ui.model.isBlank())"尚未选择模型" else "当前：${ui.model}",style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            ui.models.forEach{model->FilterChip(selected=ui.model==model.id,onClick={state.selectModel(model.id)},enabled=!working,label={Column{Text(model.name+if(model.supportsImages)" · 支持图片"else "");if(model.name!=model.id)Text(model.id,style=MaterialTheme.typography.labelSmall)}})}
        }
        if(ui.models.isEmpty())Text("保存密钥后自动获取可用模型。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick={state.refreshModels()},enabled=ui.hasKey&&!working,shapes=ButtonDefaults.shapes()){Text(if(ui.checking)"获取中…" else "刷新模型列表")}
        if(ui.modelStatus.isNotBlank())Text(ui.modelStatus,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
    }

    SettingGroup("个性化提示词",Icons.Default.Edit) {
        Text("补充任务内容和表达偏好。内置规则固定生效，可在关于中查看。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        MotionContent(promptExpanded){expanded -> Column(verticalArrangement=Arrangement.spacedBy(10.dp)){if(expanded) {
            OutlinedTextField(value=prompt,onValueChange={if(it.length<=PromptPolicy.MAX_LENGTH)prompt=it},label={Text("我的偏好（可留空）")},placeholder={Text("例如：学习任务优先安排练习，回复简洁直接。")},minLines=4,maxLines=10,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),supportingText={Text("${prompt.length}/${PromptPolicy.MAX_LENGTH} · 保存后下一条消息生效")})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={state.savePrompt(prompt);prompt=prompt.trim();promptMessage="已保存，下一条消息生效"},shapes=ButtonDefaults.shapes()){Text("保存")}
                TextButton(shapes=ButtonDefaults.shapes(),onClick={state.savePrompt("");prompt="";promptMessage="已清空个性化提示词"}){Text("清空并保存")}
            }
            if(promptMessage.isNotBlank()) Text(promptMessage,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            TextButton(shapes=ButtonDefaults.shapes(),onClick={promptExpanded=false}){Text("收起")}
        } else {
            Text(ui.customPrompt.ifBlank{"尚未添加个性化提示词"},maxLines=4,style=MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick={prompt=ui.customPrompt;promptExpanded=true;promptMessage=""},shapes=ButtonDefaults.shapes()){Text(if(ui.customPrompt.isBlank())"添加个性化提示词"else "编辑个性化提示词")}
        }
        } }
    }
}

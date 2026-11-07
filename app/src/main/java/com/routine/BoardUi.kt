@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable internal fun Board(steps:List<Step>,today:LocalDate,toggle:(String)->Unit,chat:()->Unit,openPlan:(String)->Unit,delete:(String)->Unit,boardRequest:Int=0,destination:String="today",navigation:Int=0){
    val todayTasks=remember(steps,today){steps.filter{it.date==today.toString()}}
    val overdue=remember(steps,today){steps.filter{it.date<today.toString()&&!it.done}}
    val tomorrow=remember(steps,today){tomorrowSteps(steps,today)}
    var filter by rememberSaveable{mutableStateOf("今天")}
    var view by rememberSaveable{mutableStateOf("今日任务")}
    LaunchedEffect(boardRequest){if(boardRequest>0){filter="今天";view="今日任务"}}
    LaunchedEffect(navigation){if(navigation>0){view=if(destination=="plans")"所有计划"else "今日任务";filter=when(destination){"overdue"->"待补做";"tomorrow"->"接下来";else->"今天"}}}
    val done=todayTasks.count{it.done}
    val shown=when(filter){"待补做"->overdue;"接下来"->tomorrow;else->todayTasks}
    MotionContent(view,Modifier.fillMaxSize().padding(horizontal=20.dp)){selectedView->
        val overview:@Composable ()->Unit={BoardOverview(today,done,todayTasks.size,todayTasks.sumOf{it.minutes},selectedView){view=it}}
        if(selectedView=="所有计划")AllPlans(steps,openPlan,delete,header=overview)
        else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item(key="board:overview",contentType="overview"){overview()}
            item(key="board:filters",contentType="filters"){
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf("今天","待补做","接下来").forEach{label->FilterChip(selected=filter==label,onClick={filter=label},label={Text(if(label=="待补做"&&overdue.isNotEmpty())"$label ${overdue.size}"else label)})}
                }
            }
            item(key="board:count",contentType="heading"){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(if(filter=="今天")"今日行动"else filter,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("${shown.size} 项",color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(shown.isEmpty())item(key="empty"){Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainer){
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("暂无任务",style=MaterialTheme.typography.titleMedium)
                    FilledTonalButton(onClick=chat,shapes=ButtonDefaults.shapes()){Text("和 AI 规划任务")}
                }
            }}
            items(shown,key={it.id},contentType={"task"}){step->TaskRow(step,toggle,Modifier.animateItem())}
            if(steps.isNotEmpty())item(key="new-goal"){TextButton(onClick=chat,shapes=ButtonDefaults.shapes(),modifier=Modifier.fillMaxWidth()){Text("＋ 规划一个新目标")}}
        }
    }
}

/** The overview is a normal list item, so no board controls reserve space while scrolling. */
@Composable private fun BoardOverview(today:LocalDate,done:Int,count:Int,minutes:Int,view:String,selectView:(String)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(today.format(DateTimeFormatter.ofPattern("M月d日 · EEEE",java.util.Locale.SIMPLIFIED_CHINESE)),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(20.dp)){
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("今日进度  $done / $count",style=MaterialTheme.typography.titleSmall)
                    Text("预计 $minutes 分钟",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
                }
                MotionProgress(if(count==0)0f else done.toFloat()/count,Modifier.fillMaxWidth().height(4.dp))
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("今日任务","所有计划").forEach{label->FilterChip(selected=view==label,onClick={selectView(label)},label={Text(label)})}
        }
    }
}

@Composable private fun TaskRow(step:Step,toggle:(String)->Unit,modifier:Modifier=Modifier){
    Surface(modifier=modifier,shape=RoundedCornerShape(24.dp),color=if(step.done)MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh){
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.Top){
            Checkbox(checked=step.done,onCheckedChange={toggle(step.id)})
            Column(Modifier.weight(1f).padding(top=10.dp,end=8.dp,bottom=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(step.project,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Text(step.title,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
                if(step.definitionOfDone.isNotBlank())Text("完成标准：${step.definitionOfDone}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${step.minutes} 分钟 · ${step.date} · 截止 ${step.ddl}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

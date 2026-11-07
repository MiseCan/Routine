@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object PlanNames {
    fun valid(value:String)=value.isNotBlank()&&value.codePointCount(0,value.length)<=7&&!value.contains('…')&&!value.contains("...")
    // Old plans without a generated short name get a complete topic phrase, never a text slice.
    fun fallback(full:String):String {
        if(valid(full))return full
        val topics=listOf("日语" to "日语学习","英语" to "英语学习","数学" to "数学复习","论文" to "论文写作","报告" to "报告撰写","健身" to "健身训练","阅读" to "阅读学习","考试" to "备考计划","项目" to "项目推进")
        return topics.firstOrNull{full.contains(it.first)}?.second?:"编辑计划"
    }
}

internal fun chatPlanTitle(state:PlannerUiState,steps:List<Step>):String {
    if(state.editingPlanId.isNotBlank()){
        val selected=steps.firstOrNull{planKey(it)==state.editingPlanId}
        val draft=state.result?.takeIf{it.targetPlanId==state.editingPlanId}?.shortTitle
        return draft?.takeIf{PlanNames.valid(it)}?:selected?.shortProject?.takeIf{PlanNames.valid(it)}?:selected?.let{PlanNames.fallback(it.project)}?:"选择计划"
    }
    return state.result?.takeUnless{state.confirmed}?.shortTitle?.takeIf{PlanNames.valid(it)}?:"AI 聊天"
}

@Composable internal fun ChatPlanPicker(steps:List<Step>,current:String,select:(String)->Unit,dismiss:()->Unit){
    var chosen by remember{mutableStateOf(current)}
    MotionDialog(onDismissRequest=dismiss,title={Text("对话计划")},text={
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("默认创建新计划；选择已有计划后，对话用于修改该计划。切换会重置未保存的规划草稿，聊天记录保留。",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={chosen=""},shapes=ButtonDefaults.shapes(),modifier=Modifier.fillMaxWidth()){Text(if(chosen.isEmpty())"✓ 创建新计划"else "创建新计划")}
            steps.groupBy{planKey(it)}.forEach{(id,items)->
                OutlinedButton(onClick={chosen=id},shapes=ButtonDefaults.shapes(),modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.fillMaxWidth()){
                        Text((if(chosen==id)"✓ "else "")+items.first().project)
                        Text("截止 ${items.first().ddl} · ${items.count{it.done}}/${items.size} 项完成",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    },confirmLabel="切换",onConfirm={select(chosen);dismiss()})
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.daystep.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun AttachmentCards(files:List<ChatAttachment>,state:PlannerState,onRemove:((String)->Unit)?=null){
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        files.forEach{file->
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh){
                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    if(file.images.isNotEmpty()){
                        val bitmap by produceState<ImageBitmap?>(null,file.id){value=withContext(Dispatchers.IO){state.attachmentThumbnail(file.images.first())?.asImageBitmap()}}
                        bitmap?.let{Image(it,file.name,Modifier.size(52.dp))}
                    }
                    Column(Modifier.weight(1f)){
                        Text(file.name,style=MaterialTheme.typography.labelLarge,maxLines=2)
                        Text(if(file.images.isNotEmpty())"${file.images.size} 张图片/页面" else "${file.text.length} 字 · 已读取",style=MaterialTheme.typography.bodySmall)
                    }
                    if(onRemove!=null)IconButton(onClick={onRemove(file.id)},enabled=!state.working,shapes=IconButtonDefaults.shapes()){Icon(Icons.Default.Close,"移除附件：${file.name}")}
                }
            }
        }
    }
}

@Composable internal fun PlanPicker(existing:List<Step>,select:(String)->Unit,dismiss:()->Unit){
    var chosen by remember{mutableStateOf("")}
    MotionDialog(onDismissRequest=dismiss,title={Text("选择要修改的计划")},text={
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            existing.groupBy{planKey(it)}.forEach{(id,steps)->
                OutlinedButton(onClick={chosen=id},shapes=ButtonDefaults.shapes(),modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.fillMaxWidth()){
                        Text((if(chosen==id)"✓ "else "")+steps.first().project)
                        Text("截止 ${steps.first().ddl} · ${steps.count{it.done}}/${steps.size} 项完成",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    },confirmLabel="修改所选计划",confirmEnabled=chosen.isNotBlank(),onConfirm={select(chosen);dismiss()})
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.routine

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Android's required notification consent is requested once; no repeated startup prompts. */
@Composable internal fun ReminderPermissionSetup(){
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){TaskReminders.sync(context)}
    LaunchedEffect(Unit){
        TaskReminders.createChannel(context)
        if(TaskReminders.enabled(context)&&Build.VERSION.SDK_INT>=33&&!TaskReminders.permitted(context)&&!TaskReminders.asked(context)){
            TaskReminders.markAsked(context);permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }else TaskReminders.sync(context)
    }
}

@Composable internal fun ReminderSettings(){
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var enabled by remember{mutableStateOf(TaskReminders.enabled(context))}
    var allowed by remember{mutableStateOf(TaskReminders.permitted(context))}
    var precise by remember{mutableStateOf(TaskReminders.precise(context))}
    fun refresh(){enabled=TaskReminders.enabled(context);allowed=TaskReminders.permitted(context);precise=TaskReminders.precise(context);TaskReminders.sync(context)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){refresh()}
    fun requestPermission(){
        if(Build.VERSION.SDK_INT>=33&&!TaskReminders.asked(context)){
            TaskReminders.markAsked(context);permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
    }
    DisposableEffect(owner){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refresh()}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    SettingGroup("任务提醒",Icons.Default.Notifications){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Column(Modifier.weight(1f)){
                Text("今日任务轻提醒",style=MaterialTheme.typography.titleMedium)
                Text("每天 8:00～23:00，每小时提醒尚未完成的今日任务。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked=enabled,onCheckedChange={value->enabled=value;TaskReminders.setEnabled(context,value);if(value&&!allowed)requestPermission()})
        }
        Text("默认无声音、无振动，不弹窗。同一条通知持续更新；没有待办或全部完成时不发送。点击通知打开今日任务。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(enabled&&!allowed){
            Text("通知权限未开启，暂时无法提醒。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            FilledTonalButton(onClick={requestPermission()},shapes=ButtonDefaults.shapes()){Text("允许通知")}
        }
        if(enabled&&allowed&&!precise){
            Text("系统省电时可能延迟；如需整点发送，可允许准点提醒。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:${context.packageName}")))},shapes=ButtonDefaults.shapes()){Text("允许准点提醒（可选）")}
        }
    }
}

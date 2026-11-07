package com.routine

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDate
import java.time.ZonedDateTime

/** One upcoming alarm only. The receiver always reloads today's tasks before notifying. */
object TaskReminders {
    const val CHANNEL="today_tasks_quiet"
    const val NOTIFICATION_ID=8100
    const val ACTION="com.routine.REMIND_TODAY"
    const val OPEN_TODAY="open_today"
    private const val REQUEST=8101
    private fun prefs(context:Context)=context.getSharedPreferences("routine_reminders",Context.MODE_PRIVATE)
    fun enabled(context:Context)=prefs(context).getBoolean("enabled",true)
    fun asked(context:Context)=prefs(context).getBoolean("permission_asked",false)
    fun markAsked(context:Context){prefs(context).edit().putBoolean("permission_asked",true).apply()}
    fun setEnabled(context:Context,value:Boolean){prefs(context).edit().putBoolean("enabled",value).apply();sync(context)}
    private fun manager(context:Context)=context.getSystemService(NotificationManager::class.java)
    fun createChannel(context:Context){
        manager(context).createNotificationChannel(NotificationChannel(CHANNEL,"今日任务轻提醒",NotificationManager.IMPORTANCE_LOW).apply{
            description="每天 8:00～23:00 每小时提醒今日未完成任务；默认无声音、无振动。"
            setSound(null,null);enableVibration(false);setShowBadge(false)
        })
    }
    fun permitted(context:Context):Boolean {
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false
        return manager(context).areNotificationsEnabled()&&manager(context).getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    fun precise(context:Context)=context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun alarmIntent(context:Context)=PendingIntent.getBroadcast(context,REQUEST,Intent(context,TaskReminderReceiver::class.java).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun storedSteps(context:Context)=decode(context.getSharedPreferences("routine",Context.MODE_PRIVATE).getString("steps","[]")?:"[]")

    fun sync(context:Context,now:ZonedDateTime=ZonedDateTime.now()) {
        createChannel(context)
        val alarms=context.getSystemService(AlarmManager::class.java)
        val operation=alarmIntent(context)
        alarms.cancel(operation)
        val steps=storedSteps(context)
        val pending=ReminderPolicy.pending(steps,now.toLocalDate())
        if(!enabled(context)||!permitted(context)){
            manager(context).cancel(NOTIFICATION_ID);return
        }
        // Hide an already delivered snapshot when tasks are edited/completed/deleted.
        val fingerprint=pending.map{it.id+":"+it.project+":"+it.title}.joinToString("\n")
        if(pending.isEmpty()||prefs(context).getString("shown_tasks",null)!=fingerprint)manager(context).cancel(NOTIFICATION_ID)
        var next=ReminderPolicy.next(now)
        val date=steps.asSequence().filter{!it.done}.mapNotNull{runCatching{LocalDate.parse(it.date)}.getOrNull()}.filter{!it.isBefore(next.toLocalDate())}.minOrNull()
        if(date==null)return
        if(date.isAfter(next.toLocalDate()))next=date.atTime(8,0).atZone(now.zone)
        try {
            if(precise(context))alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),operation)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),operation)
        }catch(_:SecurityException){
            // The user can revoke exact-alarm access between the check and scheduling.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),operation)
        }
    }

    fun deliver(context:Context,now:ZonedDateTime=ZonedDateTime.now()):Boolean {
        createChannel(context)
        try {
            if(!enabled(context)||!permitted(context))return false
            val steps=storedSteps(context)
            if(!ReminderPolicy.shouldSend(now,prefs(context).getString("last_slot",null),steps))return false
            val pending=ReminderPolicy.pending(steps,now.toLocalDate())
            manager(context).notify(NOTIFICATION_ID,buildNotification(context,pending,now))
            prefs(context).edit().putString("last_slot",ReminderPolicy.slot(now))
                .putString("shown_tasks",pending.map{it.id+":"+it.project+":"+it.title}.joinToString("\n")).commit()
            return true
        }catch(_:SecurityException){return false}
        finally { sync(context,now) }
    }

    internal fun buildNotification(context:Context,pending:List<Step>,now:ZonedDateTime):Notification {
        val open=Intent(context,MainActivity::class.java).putExtra(OPEN_TODAY,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap=PendingIntent.getActivity(context,REQUEST,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val midnight=now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("今日还有 ${pending.size} 项任务未完成")
                .setContentText(pending.take(3).joinToString("、"){it.title.take(60)})
                .setStyle(Notification.BigTextStyle().bigText(ReminderPolicy.body(pending)))
                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setShowWhen(false).setTimeoutAfter(java.time.Duration.between(now,midnight).toMillis())
                .build()
    }
}

class TaskReminderReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action==TaskReminders.ACTION)TaskReminders.deliver(context)
        else if(intent.action in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))TaskReminders.sync(context)
    }
}

package com.daystep.app

import android.app.Notification
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.ZonedDateTime

/** Builds synthetic notifications without posting them, scheduling alarms or touching preferences. */
@RunWith(AndroidJUnit4::class)
class TaskReminderDeviceTest {
    private val realContext=InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext=object:ContextWrapper(realContext){
        override fun getSharedPreferences(name:String,mode:Int):SharedPreferences =
            error("Notification construction must not access user preferences")
    }
    private val now=ZonedDateTime.parse("2026-10-31T10:00:00+08:00[Asia/Shanghai]")
    private val first=Step("test-reminder-a","测试计划甲","阅读测试资料",now.toLocalDate().toString(),now.toLocalDate().toString(),20,planId="test-a")
    private val second=first.copy(id="test-reminder-b",project="测试计划乙",title="整理测试笔记",planId="test-b")

    @Test fun notificationContainsTheCurrentPendingTasksAndOpensTheApp(){
        val notification=TaskReminders.buildNotification(testContext,listOf(first,second),now)
        assertEquals(TaskReminders.CHANNEL,notification.channelId)
        assertEquals("今日还有 2 项任务未完成",notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val text=notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text.contains(first.title));assertTrue(text.contains(second.title))
        val expanded=notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(expanded.contains(first.project));assertTrue(expanded.contains(second.project))
        assertNotNull(notification.contentIntent)
        assertNotEquals(0,notification.smallIcon.resId)
    }

    @Test fun notificationHasNoSoundOrVibrationAndIsPrivateOnTheLockScreen(){
        val notification=TaskReminders.buildNotification(testContext,listOf(first),now)
        @Suppress("DEPRECATION")
        assertNull(notification.sound)
        @Suppress("DEPRECATION")
        assertNull(notification.vibrate)
        assertEquals(Notification.VISIBILITY_PRIVATE,notification.visibility)
        assertEquals(Notification.CATEGORY_REMINDER,notification.category)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL!=0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE!=0)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT==0)
    }

    @Test fun notificationExpiresAtTheNextLocalMidnight(){
        val late=now.withHour(23).withMinute(45)
        val notification=TaskReminders.buildNotification(testContext,listOf(first),late)
        val midnight=late.toLocalDate().plusDays(1).atStartOfDay(late.zone)
        assertEquals(Duration.between(late,midnight).toMillis(),notification.timeoutAfter)
        assertEquals(15*60*1000L,notification.timeoutAfter)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN,true))
    }

    @Test fun largePlanHasBoundedExpandedTextAndAnAccuratePendingCount(){
        val many=(1..100).map{first.copy(id="synthetic-$it",title="任务$it："+"详细内容".repeat(60))}
        val notification=TaskReminders.buildNotification(testContext,many,now)
        assertEquals("今日还有 100 项任务未完成",notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)!!.length<=3500)
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("90"))
    }
}

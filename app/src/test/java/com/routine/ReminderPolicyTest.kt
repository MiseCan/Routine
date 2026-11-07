package com.routine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPolicyTest {
    private val today=LocalDate.of(2026,10,31)
    private fun time(value:String)=ZonedDateTime.parse("2026-10-31T$value+08:00[Asia/Shanghai]")
    private fun task(id:String="a",date:LocalDate=today,done:Boolean=false)=
        Step(id,"计划$id","任务$id",date.toString(),"2027-01-01",20,done,planId="plan-$id")

    @Test fun onlyUnfinishedTasksForTheCurrentLocalDayAreIncluded(){
        val pending=task()
        val other=task("b")
        val items=listOf(task("old",today.minusDays(1)),pending,task("done",done=true),task("next",today.plusDays(1)),other)
        assertEquals(listOf(pending,other),ReminderPolicy.pending(items,today))
    }

    @Test fun pendingUsesTheSuppliedDayAtTheYearBoundary(){
        val end=LocalDate.of(2026,12,31)
        val next=task("next",end.plusDays(1))
        assertEquals(listOf(next),ReminderPolicy.pending(listOf(task("end",end),next),end.plusDays(1)))
    }

    @Test fun sendingWindowIncludesEightAndTheLastHourButNotNight(){
        assertFalse(ReminderPolicy.inWindow(time("07:59:59")))
        assertTrue(ReminderPolicy.inWindow(time("08:00:00")))
        assertTrue(ReminderPolicy.inWindow(time("23:00:00")))
        assertTrue(ReminderPolicy.inWindow(time("23:59:59")))
        assertFalse(ReminderPolicy.inWindow(time("00:00:00")))
    }

    @Test fun nextAlarmIsStrictlyFutureAndAtAnHourBoundary(){
        assertEquals(time("08:00:00"),ReminderPolicy.next(time("07:59:59")))
        assertEquals(time("09:00:00"),ReminderPolicy.next(time("08:00:00")))
        assertEquals(time("09:00:00"),ReminderPolicy.next(time("08:32:12")))
        assertEquals(time("23:00:00"),ReminderPolicy.next(time("22:59:59")))
    }

    @Test fun lastHourSchedulesTheNextMorningAcrossMonthAndYear(){
        assertEquals(ZonedDateTime.parse("2026-11-01T08:00:00+08:00[Asia/Shanghai]"),ReminderPolicy.next(time("23:00:00")))
        assertEquals(ZonedDateTime.parse("2026-11-01T08:00:00+08:00[Asia/Shanghai]"),ReminderPolicy.next(time("23:59:59")))
        assertEquals(ZonedDateTime.parse("2027-01-01T08:00:00+08:00[Asia/Shanghai]"),ReminderPolicy.next(ZonedDateTime.parse("2026-12-31T23:37:00+08:00[Asia/Shanghai]")))
    }

    @Test fun nextAlarmKeepsLocalEightOClockWhenDaylightSavingOffsetChanges(){
        val zone=ZoneId.of("America/New_York")
        val beforeSpring=ZonedDateTime.of(2027,3,13,23,0,0,0,zone)
        val afterSpring=ReminderPolicy.next(beforeSpring)
        assertEquals(ZonedDateTime.of(2027,3,14,8,0,0,0,zone),afterSpring)
        assertEquals(8,afterSpring.hour)
        assertTrue(afterSpring.toInstant().isAfter(beforeSpring.toInstant()))
        val beforeAutumn=ZonedDateTime.of(2026,10,31,23,0,0,0,zone)
        assertEquals(ZonedDateTime.of(2026,11,1,8,0,0,0,zone),ReminderPolicy.next(beforeAutumn))
    }

    @Test fun oneSlotIsSharedWithinAnHourAndChangesByHourAndDate(){
        assertEquals(ReminderPolicy.slot(time("08:00:00")),ReminderPolicy.slot(time("08:59:59")))
        assertNotEquals(ReminderPolicy.slot(time("08:00:00")),ReminderPolicy.slot(time("09:00:00")))
        assertNotEquals(ReminderPolicy.slot(time("08:00:00")),ReminderPolicy.slot(time("08:00:00").plusDays(1)))
    }

    @Test fun repeatedDeliveryWithinTheSameHourDoesNotSendAgain(){
        val steps=listOf(task())
        val last=ReminderPolicy.slot(time("08:01:00"))
        assertTrue(ReminderPolicy.shouldSend(time("08:00:00"),null,steps))
        assertFalse(ReminderPolicy.shouldSend(time("08:59:59"),last,steps))
        assertTrue(ReminderPolicy.shouldSend(time("09:00:00"),last,steps))
    }

    @Test fun emptyAndFullyCompletedDaysNeverSend(){
        assertFalse(ReminderPolicy.shouldSend(time("09:00:00"),null,emptyList()))
        assertFalse(ReminderPolicy.shouldSend(time("09:00:00"),null,listOf(task(done=true))))
        assertFalse(ReminderPolicy.shouldSend(time("09:00:00"),null,listOf(task(date=today.minusDays(1)),task("next",today.plusDays(1)))))
        assertFalse(ReminderPolicy.shouldSend(time("07:59:59"),null,listOf(task())))
        assertFalse(ReminderPolicy.shouldSend(time("00:00:00"),null,listOf(task())))
    }

    @Test fun completedOrDeletedTasksDisappearFromTheNextReminder(){
        val first=task("first")
        val second=task("second")
        assertEquals(listOf(second),ReminderPolicy.pending(listOf(first.copy(done=true),second),today))
        assertFalse(ReminderPolicy.shouldSend(time("10:00:00"),ReminderPolicy.slot(time("09:00:00")),listOf(first.copy(done=true))))
    }

    @Test fun smallNotificationListsNameEveryTaskAndItsPlan(){
        val steps=(1..10).map{task(it.toString())}
        val body=ReminderPolicy.body(steps)
        steps.forEach{assertTrue("Missing task ${it.id}",body.contains(it.title));assertTrue("Missing project ${it.project}",body.contains(it.project))}
        assertTrue(body.length<=3500)
    }

    @Test fun largeAndLongTaskListsStayWithinNotificationTextLimit(){
        val many=(1..100).map{task(it.toString()).copy(project="很长的计划名称".repeat(60),title="很长的任务内容".repeat(100))}
        val body=ReminderPolicy.body(many)
        assertTrue(body.isNotBlank())
        assertTrue("Notification too long: ${body.length}",body.length<=3500)
        assertTrue("A large list needs a count/remaining summary",Regex("[0-9]+").containsMatchIn(body))
    }
}

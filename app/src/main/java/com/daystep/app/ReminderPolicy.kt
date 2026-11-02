package com.daystep.app

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Local calendar rules shared by the scheduler and delivery check. No model/network needed. */
object ReminderPolicy {
    fun pending(steps:List<Step>,today:LocalDate)=steps.filter{!it.done&&it.date==today.toString()}
    fun inWindow(now:ZonedDateTime)=now.hour in 8..23
    fun slot(now:ZonedDateTime)="${now.toLocalDate()}T${now.hour}"
    fun next(now:ZonedDateTime):ZonedDateTime {
        val hour=now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        return when {
            hour.hour<8->hour.withHour(8)
            else->hour
        }
    }
    fun shouldSend(now:ZonedDateTime,lastSlot:String?,steps:List<Step>)=
        inWindow(now)&&slot(now)!=lastSlot&&pending(steps,now.toLocalDate()).isNotEmpty()

    fun body(pending:List<Step>):String {
        val lines=pending.take(10).map{"• ${it.project.take(80)}：${it.title.take(200)}"}
        return (lines+if(pending.size>10)listOf("另有 ${pending.size-10} 项，点击查看今日任务。")else emptyList()).joinToString("\n").take(3500)
    }
}

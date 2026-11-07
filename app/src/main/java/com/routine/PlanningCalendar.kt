package com.routine

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Mainland China published calendar; never infer future statutory holidays from weekdays. */
object PlanningCalendar {
    const val SOURCE="https://www.wuhan.gov.cn/zwgk/tzgg/202511/t20251105_2672471.shtml"
    val knownYears=setOf(2026)
    private val holidays=buildSet<LocalDate>{
        listOf("2026-01-01" to "2026-01-03","2026-02-15" to "2026-02-23","2026-04-04" to "2026-04-06","2026-05-01" to "2026-05-05","2026-06-19" to "2026-06-21","2026-09-25" to "2026-09-27","2026-10-01" to "2026-10-07").forEach{(a,b)->var day=LocalDate.parse(a);while(day<=LocalDate.parse(b)){add(day);day=day.plusDays(1)}}
    }
    private val makeupWorkdays=setOf("2026-01-04","2026-02-14","2026-02-28","2026-05-09","2026-09-20","2026-10-10").map{LocalDate.parse(it)}.toSet()
    fun known(date:LocalDate){if(date.year !in knownYears)throw AiFormatException("CALENDAR_UNAVAILABLE","${date.year}年的中国大陆调休日历未收录。请先让用户选择按周一至周五/周末安排，或提供确切假期日期；不能把周末当作法定节假日，也不能自行编造。")}
    fun holiday(date:LocalDate):Boolean{known(date);return date in holidays}
    fun workday(date:LocalDate):Boolean{known(date);return date in makeupWorkdays||(date.dayOfWeek.value<=5&&date !in holidays)}
    fun context()=JSONObject().put("region","中国大陆").put("knownYears",JSONArray(knownYears.toList())).put("holidays",JSONArray(holidays.sorted().map{it.toString()})).put("makeupWorkdays",JSONArray(makeupWorkdays.sorted().map{it.toString()})).put("source",SOURCE).put("unknownYearPolicy","必须先确认改用星期规则或由用户提供确切日期，禁止猜测。")
}

package com.routine

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Compact only identical, editable tasks; recorded work remains explicit and unchanged. */
object PlanWire {
    fun existingPlans(steps:List<Step>)=JSONArray().apply{steps.groupBy{planKey(it)}.forEach{(id,items)->
        val singles=JSONArray();val rules=JSONArray()
        fun single(s:Step)=JSONObject().put("title",s.title).put("date",s.date).put("minutes",s.minutes).put("definitionOfDone",s.definitionOfDone).put("done",s.done).put("locked",s.done||s.completions.isNotEmpty()).put("completionRecords",JSONArray().apply{s.completions.forEach{put(JSONObject().put("note",it.note).put("at",it.at))}})
        items.filter{it.done||it.completions.isNotEmpty()}.forEach{singles.put(single(it))}
        items.filterNot{it.done||it.completions.isNotEmpty()}.groupBy{Triple(it.title,it.minutes,it.definitionOfDone)}.forEach{(_,same)->
            val dates=same.map{LocalDate.parse(it.date)}.toSortedSet();val start=dates.first();val end=dates.last()
            if(same.size<3||dates.size!=same.size||ChronoUnit.DAYS.between(start,end)>ScheduleRules.MAX_DAYS)same.forEach{singles.put(single(it))}
            else{
                val weekdays=dates.map{it.dayOfWeek.value}.toSortedSet()
                val excluded=JSONArray();var d=start
                while(d<=end){if(d.dayOfWeek.value in weekdays&&d !in dates)excluded.put(d.toString());d=d.plusDays(1)}
                val daily=dates.size.toLong()==ChronoUnit.DAYS.between(start,end)+1
                val first=same.first()
                rules.put(JSONObject().put("startDate",start.toString()).put("endDate",end.toString()).put("repeat",if(daily)"daily"else "weekly").put("daysOfWeek",if(daily)JSONArray()else JSONArray(weekdays.toList())).put("excludedDates",excluded).put("title",first.title).put("minutes",first.minutes).put("definitionOfDone",first.definitionOfDone))
            }
        }
        put(JSONObject().put("id",id).put("title",items.first().project).put("deadline",items.first().ddl).put("steps",singles).put("schedules",rules))
    }}
}

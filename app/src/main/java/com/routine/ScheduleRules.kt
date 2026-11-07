package com.routine

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Compact wire rules expand once into ordinary per-date actions before preview or saving. */
object ScheduleRules {
    const val MAX_DAYS=1096L
    const val MAX_ACTIONS=5000
    private fun fail(message:String):Nothing=throw AiFormatException("SCHEDULE_VALIDATION",message)
    private fun date(value:Any?):LocalDate=try{if(value !is String)fail("日期字段必须是YYYY-MM-DD字符串。");LocalDate.parse(value)}catch(e:Exception){fail("重复规则的日期无效，请使用YYYY-MM-DD。")}
    private fun dates(rule:JSONObject,key:String):Set<LocalDate>{val a=rule.optJSONArray(key)?:fail("$key 必须是日期数组。");if(a.length()>1097)fail("重复规则的例外日期过多。");return(0 until a.length()).map{date(a.get(it))}.toSet()}
    private fun task(rule:JSONObject):JSONObject {
        for((key,max) in listOf("title" to 300,"definitionOfDone" to 2000)){val text=rule.opt(key) as? String?:fail("重复任务缺少 $key。");if(text.isBlank()||text.length>max)fail("重复任务的 $key 内容无效。")}
        val minutes=rule.opt("minutes") as? Number?:fail("重复任务的预计耗时必须为整数。")
        if(minutes.toDouble()!=minutes.toInt().toDouble()||minutes.toInt() !in 5..240)fail("每项预计耗时应在5–240分钟内。")
        return JSONObject().put("title",rule.getString("title")).put("minutes",minutes.toInt()).put("definitionOfDone",rule.getString("definitionOfDone"))
    }
    fun expand(steps:JSONArray,rules:JSONArray,today:LocalDate,deadline:LocalDate,locked:List<Step> = emptyList()):JSONArray {
        if(steps.length()+rules.length()>120)fail("单次任务与重复规则合计最多120条，请合并重复安排而非遗漏任务。")
        val out=mutableListOf<JSONObject>()
        for(i in 0 until steps.length())out+=JSONObject(steps.getJSONObject(i).toString())
        for(i in 0 until rules.length()){
            val rule=rules.getJSONObject(i);val prototype=task(rule)
            val start=date(rule.opt("startDate"));val end=date(rule.opt("endDate"))
            if(start<today||end<start||end>deadline||ChronoUnit.DAYS.between(start,end)>MAX_DAYS)fail("重复规则必须在今天至截止日期内，起止日期不能颠倒；最多安排三年。")
            val repeat=rule.optString("repeat");if(repeat !in setOf("daily","weekdays","weekends","weekly","workdays","rest_days","holidays"))fail("repeat 必须为 daily/weekdays/weekends/weekly/workdays/rest_days/holidays。")
            val week=rule.optJSONArray("daysOfWeek")?:fail("daysOfWeek 必须为数组，周一为1，周日为7。")
            val days=(0 until week.length()).map{val n=week.opt(it) as? Number?:fail("星期必须是1–7的整数。");if(n.toDouble()!=n.toInt().toDouble()||n.toInt() !in 1..7)fail("星期必须是1–7的整数。");n.toInt()}.toSet()
            if(week.length()>7||(repeat=="weekly"&&days.isEmpty())||(repeat!="weekly"&&days.isNotEmpty()))fail("只有weekly可填写daysOfWeek，且必须选择至少一天；其他类型使用空数组。")
            val excluded=dates(rule,"excludedDates")
            if(excluded.any{it<start||it>end})fail("排除日期必须位于该重复规则的起止范围内。")
            if(repeat in setOf("workdays","rest_days","holidays"))for(year in start.year..end.year)PlanningCalendar.known(LocalDate.of(year,1,1))
            var d=start;var count=0
            while(d<=end){
                val matches=when(repeat){"daily"->true;"weekdays"->d.dayOfWeek.value<=5;"weekends"->d.dayOfWeek.value>=6;"weekly"->d.dayOfWeek.value in days;"workdays"->PlanningCalendar.workday(d);"rest_days"->!PlanningCalendar.workday(d);else->PlanningCalendar.holiday(d)}
                if(matches&&d !in excluded){out+=JSONObject(prototype.toString()).put("date",d.toString());count++;if(out.size>MAX_ACTIONS)fail("展开后超过5000项每日任务，请分阶段生成；不能截断或遗漏。")}
                d=d.plusDays(1)
            }
            if(count==0)fail("该重复规则没有匹配到任何日期，请调整日期或规则。")
        }
        // Duplicated days from overlapping identical rules are one action, not two completions.
        val lockedKeys=locked.map{it.date to it.title.trim()}.toSet()
        val unique=out.sortedBy{it.optString("date")}.distinctBy{listOf(it.optString("date"),it.optString("title").trim(),it.optInt("minutes"),it.optString("definitionOfDone").trim())}.filterNot{(it.optString("date") to it.optString("title").trim()) in lockedKeys}
        if(unique.size>MAX_ACTIONS)fail("展开后超过5000项每日任务，请分阶段生成。")
        return JSONArray(unique)
    }
    fun summary(rule:JSONObject):String {
        val weekNames=listOf("一","二","三","四","五","六","日")
        val selected=rule.optJSONArray("daysOfWeek")?.let{a->(0 until a.length()).joinToString("、"){weekNames.getOrElse(a.optInt(it)-1){"?"}}}.orEmpty()
        val repeat=when(rule.optString("repeat")){"daily"->"每天";"weekdays"->"周一至周五";"weekends"->"周末";"workdays"->"工作日（含调休）";"rest_days"->"休息日（含节假日）";"holidays"->"法定节假日";"weekly"->"每周 $selected";else->""}
        val excluded=rule.optJSONArray("excludedDates")?.length()?:0
        return "${rule.optString("startDate")}—${rule.optString("endDate")} · $repeat${if(excluded>0)" · 排除${excluded}天"else ""}\n${rule.optString("title")} · ${rule.optInt("minutes")}分钟"
    }
}

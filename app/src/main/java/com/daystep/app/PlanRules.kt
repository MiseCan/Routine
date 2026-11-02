package com.daystep.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

object PlanRules {
    private fun fail(message: String): Nothing=throw AiFormatException("PLAN_VALIDATION",message)
    private fun text(o:JSONObject,key:String,max:Int,required:Boolean=false):String {
        val s=o.opt(key) as? String?:fail("AI 返回的 $key 格式无效，请重试。")
        if(s.length>max||(required&&s.isBlank()))fail("AI 返回的 $key 内容无效，请重试。")
        return s
    }
    private fun list(o:JSONObject,key:String,max:Int):JSONArray {
        val a=o.optJSONArray(key)?:fail("AI 返回的 $key 格式无效，请重试。")
        if(a.length()>max)fail("AI 返回内容过长，请缩小任务范围。")
        return a
    }
    fun validate(raw:JSONObject,now:ZonedDateTime,existing:List<Step> = emptyList()):JSONObject {
        val json=JSONObject(raw.toString())
        json.remove("dailySteps")
        val status=text(json,"status",20)
        if(status !in listOf("clarify","ready","infeasible"))fail("AI 规划状态无效。")
        text(json,"reply",4000,true)
        val t=json.optJSONObject("task")?:fail("AI 返回的任务信息缺失。")
        val title=text(t,"title",200);val deliverable=text(t,"deliverable",2000);val progress=text(t,"progress",2000)
        val end=if(t.has("deadline")&&t.isNull("deadline"))null else try{LocalDate.parse(t.getString("deadline"))}catch(e:Exception){fail("AI 返回的截止日期无效。")}
        if(!t.has("deadlineTime")||!t.isNull("deadlineTime"))try{val s=t.getString("deadlineTime");if(!Regex("\\d{2}:\\d{2}").matches(s))fail("AI 截止时刻无效。");LocalTime.parse(s)}catch(e:Exception){fail("AI 返回的截止时刻无效。")}
        for(key in listOf("constraints")) {val a=list(t,key,20);for(i in 0 until a.length())if(a.opt(i) !is String||a.getString(i).length>1000)fail("任务限制格式无效。")}
        val warnings=list(json,"warnings",20);for(i in 0 until warnings.length())if(warnings.opt(i) !is String||warnings.getString(i).length>1000)fail("计划提示格式无效。")
        val questions=list(json,"questions",6);val ids=mutableSetOf<String>()
        for(i in 0 until questions.length()) {
            val q=questions.getJSONObject(i)
            if(!ids.add(text(q,"id",60,true)))fail("补充问题重复。")
            text(q,"prompt",1000,true)
            if(text(q,"kind",20) !in listOf("choice","date","time","text"))fail("补充问题类型无效。")
            val options=list(q,"options",6);for(j in 0 until options.length()){val o=options.getJSONObject(j);text(o,"label",100,true);text(o,"value",1000,true)}
        }
        val actions=list(json,"steps",120)
        val schedules=if(json.has("schedules"))list(json,"schedules",120)else JSONArray().also{json.put("schedules",it)}
        json.put("planId",JSONObject.NULL)
        if(status!="ready"){if(actions.length()>0||schedules.length()>0)fail("信息尚未齐全，不能生成可确认计划。");return json}
        if(title.isBlank()||deliverable.isBlank()||progress.isBlank()||end==null||(actions.length()==0&&schedules.length()==0)||questions.length()>0)fail("任务信息尚未齐全，请让 AI 继续补全。")
        val today=now.toLocalDate()
        if(end<today||ChronoUnit.DAYS.between(today,end)>ScheduleRules.MAX_DAYS)fail("截止日期已过或超出三年，请分阶段安排。")
        fun validateActions(actions:JSONArray){
        var previous=today
        for(i in 0 until actions.length()) {
            val a=actions.getJSONObject(i)
            val date=try{LocalDate.parse(a.getString("date"))}catch(e:Exception){fail("每日任务日期无效。")}
            if(date<previous||date>end)fail("每日任务超出日期范围或顺序错误，请重新规划。")
            text(a,"title",300,true);text(a,"definitionOfDone",2000,true)
            val n=a.opt("minutes") as? Number?:fail("任务工时无效。")
            if(n.toDouble()!=n.toInt().toDouble()||n.toInt() !in 5..240)fail("每步工时应在5–240分钟内。")
            previous=date
        }
        }
        validateActions(actions)
        val target=json.optString("targetPlanId")
        val expanded=ScheduleRules.expand(actions,schedules,today,end,existing.filter{planKey(it)==target&&(it.done||it.completions.isNotEmpty())})
        if(expanded.length()==0)fail("没有可保存的待办步骤，已完成任务无需重复生成。")
        validateActions(expanded)
        json.put("dailySteps",expanded)
        return json.put("planId",UUID.randomUUID().toString())
    }
}

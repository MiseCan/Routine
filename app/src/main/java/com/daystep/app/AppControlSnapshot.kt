package com.daystep.app

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate

/** Read-only, bounded application context. Credentials are deliberately absent from the API. */
object AppControlSnapshot {
    fun build(
        steps:List<Step>,selectedPlanId:String,today:LocalDate,theme:String,dynamicColor:Boolean,
        remindersEnabled:Boolean,personalPrompt:String,model:String,models:List<AvailableModel>,
        attachments:List<ChatAttachment>,inspection:JSONObject?=null,execution:String=""
    ):JSONObject {
        val todayTasks=steps.filter{it.date==today.toString()}
        val selected=if(selectedPlanId.isBlank())emptyList()else steps.filter{planKey(it)==selectedPlanId}.sortedWith(compareBy({it.date},{it.id}))
        // Each scope receives its own allowance, so a busy day cannot hide the selected plan.
        val visible=(todayTasks.take(100)+selected.take(100)).distinctBy{it.id}
        val hasMore=visible.size<steps.size
        val plans=JSONArray().apply{steps.groupBy{planKey(it)}.forEach{(id,items)->
            put(JSONObject().put("id",id).put("title",items.first().project).put("deadline",items.first().ddl)
                .put("taskCount",items.size).put("doneCount",items.count{it.done}))
        }}
        return JSONObject()
            .put("settings",settings(theme,dynamicColor,remindersEnabled,personalPrompt,model))
            .put("models",JSONArray().apply{models.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("supportsImages",it.supportsImages))}})
            .put("modelIds",JSONArray(models.map{it.id}))
            .put("plans",plans)
            .put("tasks",JSONArray().apply{visible.forEach{put(task(it))}})
            .put("today",today.toString())
            .put("selectedPlanId",selectedPlanId.takeIf{it.isNotBlank()}?:JSONObject.NULL)
            .put("totalTaskCount",steps.size).put("visibleTaskCount",visible.size)
            .put("hasMore",hasMore).put("truncated",hasMore)
            .put("taskScope",JSONObject().put("todayTaskCount",todayTasks.size).put("selectedPlanTaskCount",selected.size)
                .put("todayHasMore",todayTasks.size>100).put("selectedPlanHasMore",selected.size>100)
                .put("perScopeLimit",100).put("maxVisibleTasks",200))
            .put("readMore","tasks只含今日和所选计划的部分任务，不代表其他日期没有任务。查询完整任务、其他日期或全部完成记录时，使用inspect_tasks并依据hasMore继续分页；不要猜测ID。")
            .put("attachments",JSONArray().apply{attachments.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("mime",it.mime).put("imageCount",it.images.size).put("hasText",it.text.isNotBlank()))}})
            .put("inspection",inspection?.let{JSONObject(it.toString())}?:JSONObject.NULL)
            .put("execution",execution)
    }

    private fun settings(theme:String,dynamicColor:Boolean,remindersEnabled:Boolean,personalPrompt:String,model:String)=JSONObject()
        .put("theme",theme).put("dynamicColor",dynamicColor).put("remindersEnabled",remindersEnabled)
        .put("personalPrompt",personalPrompt).put("model",model)

    private fun task(step:Step)=JSONObject()
        .put("id",step.id).put("planId",planKey(step)).put("project",step.project).put("title",step.title)
        .put("date",step.date).put("ddl",step.ddl).put("minutes",step.minutes).put("definitionOfDone",step.definitionOfDone)
        .put("done",step.done).put("completionRecordCount",step.completions.size)
        .put("completionRecords",JSONArray().apply{step.completions.takeLast(3).forEach{put(JSONObject().put("note",it.note).put("at",it.at))}})

    /** Detects changes to saved work, settings, model or draft attachments before a proposal executes. */
    fun stamp(
        steps:List<Step>,theme:String,dynamicColor:Boolean,remindersEnabled:Boolean,
        personalPrompt:String,model:String,attachments:List<ChatAttachment>
    ):String {
        val snapshot=JSONObject().put("steps",JSONArray(encode(steps)))
            .put("settings",settings(theme,dynamicColor,remindersEnabled,personalPrompt,model))
            .put("attachments",JSONArray().apply{attachments.forEach{put(it.json())}})
        return MessageDigest.getInstance("SHA-256").digest(canonical(snapshot).toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it)}
    }

    /** JSONObject iteration order is not part of its contract; sort object keys before hashing. */
    private fun canonical(value:Any?):String=when(value){
        null,JSONObject.NULL->"null"
        is JSONObject->value.keys().asSequence().toList().sorted().joinToString(",","{","}"){JSONObject.quote(it)+":"+canonical(value.get(it))}
        is JSONArray->(0 until value.length()).joinToString(",","[","]"){canonical(value.get(it))}
        is String->JSONObject.quote(value)
        else->value.toString()
    }
}

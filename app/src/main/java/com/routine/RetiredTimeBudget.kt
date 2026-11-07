package com.routine

import org.json.JSONArray
import org.json.JSONObject

/** One-way cleanup for retired fields in old local data; no planning logic reads these values. */
object RetiredTimeBudget {
    const val FIELD="dailyMinutes"
    private val retiredPromptHashes=setOf("d87f522971107d1a427121b01b1c296a74c17d6ea39d47d3d6150570e84d20e2","93f5131a8b3e3d9c0ae46021d1f6113126e67037af668653d1e2496f671f1e5a","24b3377d2760d95a3589a9b85a50b5d8b5bae6f1132c7184eb63163503fd13bf","f29e8f481691deeeca8abf2a5ac644b66395d5cf538c1e185d9786599573916a")
    fun cleanPrompt(value:String,fallback:String):String {
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(value.replace("\r\n","\n").toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        return if(hash in retiredPromptHashes)fallback else value
    }
    private fun budgetText(text:String)=Regex("(每日|每天).{0,12}(分钟|小时|可用|投入|预算|上限)|剩余.{0,8}(分钟|时间)|时间.{0,5}预算|工时超出预算").containsMatchIn(text)
    private fun obsoleteError(text:String)=text.contains("超过每天")||text.contains("每日工时超出预算")||text.contains("截止前剩余时间不足")||text.contains("当前时刻到截止")
    private fun retiredQuestion(q:JSONObject):Boolean = q.optString("id") in setOf(FIELD,"daily_minutes","dailyTime","availableTime","capacity_conflict")||q.optString("kind")=="minutes"||budgetText(q.optString("prompt"))||run{val options=q.optJSONArray("options")?:JSONArray();(0 until options.length()).any{budgetText(options.optJSONObject(it)?.optString("label")?:"")}}
    fun cleanReply(raw:JSONObject):JSONObject {
        val result=JSONObject(raw.toString())
        result.optJSONObject("task")?.let{task->task.remove(FIELD);task.optJSONArray("constraints")?.let{a->task.put("constraints",JSONArray().apply{for(i in 0 until a.length())if(!budgetText(a.optString(i)))put(a.get(i))})}}
        result.optJSONArray("questions")?.let{a->result.put("questions",JSONArray().apply{for(i in 0 until a.length()){val q=a.optJSONObject(i);if(q==null||!retiredQuestion(q))put(a.get(i))}})}
        result.optJSONArray("warnings")?.let{a->result.put("warnings",JSONArray().apply{for(i in 0 until a.length())if(!budgetText(a.optString(i)))put(a.get(i))})}
        if(obsoleteError(result.optString("reply"))){result.put("reply","旧的时间限制已移除，可以继续生成或修改计划。").put("status","clarify").put("questions",JSONArray()).put("steps",JSONArray()).put("schedules",JSONArray()).put("dailySteps",JSONArray()).put("planId",JSONObject.NULL)}
        else if(budgetText(result.optString("reply")))result.put("reply",if(result.optString("status")=="ready")"请预览每日计划，可以继续调整。" else "请继续补充任务要求，或让我生成计划。")
        return result
    }
    fun cleanState(raw:JSONObject):JSONObject {
        val state=JSONObject(raw.toString())
        state.optString("result").takeIf{it.isNotBlank()}?.let{body->runCatching{cleanReply(JSONObject(body))}.getOrNull()?.let{state.put("result",it.toString())}}
        state.optString("messages").takeIf{it.isNotBlank()}?.let{body->runCatching{JSONArray(body)}.getOrNull()?.let{a->
            for(i in 0 until a.length())a.optJSONObject(i)?.let{message->if(message.optString("role")=="assistant")runCatching{cleanReply(JSONObject(message.getString("content")))}.getOrNull()?.let{message.put("content",it.toString())}}
            state.put("messages",a.toString())
        }}
        state.optString("answer_draft").takeIf{it.isNotBlank()}?.let{body->runCatching{JSONObject(body)}.getOrNull()?.let{answers->answers.remove(FIELD);state.put("answer_draft",answers.toString())}}
        return state
    }
}

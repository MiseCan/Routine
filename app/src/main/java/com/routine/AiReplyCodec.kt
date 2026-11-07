package com.routine

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class AiFormatException(val code:String,message:String,val candidate:String?=null,val fieldPath:String?=null):IllegalStateException(message)
class AiResponseException(message:String):IllegalStateException(message)

/** Format-only normalization. Never invent dates, task requirements, or plan IDs. */
object AiReplyCodec {
    val wireKeys=listOf("reply","status","task","questions","steps","schedules","warnings","targetPlanId","startsNewGoal","commands","shortTitle")
    fun emptyClarification(reply:String="请告诉我需要完成的任务。"):JSONObject=JSONObject()
        .put("reply",reply).put("status","clarify").put("targetPlanId",JSONObject.NULL).put("startsNewGoal",false).put("shortTitle","")
        .put("task",JSONObject().put("title","").put("deliverable","").put("deadline",JSONObject.NULL).put("deadlineTime",JSONObject.NULL).put("progress","").put("constraints",JSONArray()))
        .put("questions",JSONArray()).put("steps",JSONArray()).put("schedules",JSONArray()).put("warnings",JSONArray()).put("commands",JSONArray())

    fun normalize(raw:JSONObject):JSONObject {
        if(raw.has("properties")&&raw.optString("type")=="object"&&!raw.has("reply"))throw AiFormatException("SCHEMA_ECHO","AI 返回了格式说明，没有返回实际任务。",raw.toString())
        val result=RetiredTimeBudget.cleanReply(raw)
        result.remove("planId")
        result.remove("dailySteps")
        if(!result.has("shortTitle")||result.isNull("shortTitle"))result.put("shortTitle","")
        val shortTitle=result.opt("shortTitle") as? String?:throw AiFormatException("PLAN_SHORT_TITLE","shortTitle 必须是精简计划名称。")
        if(shortTitle.codePointCount(0,shortTitle.length)>7||shortTitle.contains('…')||shortTitle.contains("..."))
            throw AiFormatException("PLAN_SHORT_TITLE","请用不超过7字的完整短语概括计划名称，不能截断或使用省略号。")
        if(!result.has("targetPlanId"))result.put("targetPlanId",JSONObject.NULL)
        if(!result.has("startsNewGoal"))result.put("startsNewGoal",false)
        for(k in listOf("questions","steps","schedules","warnings","commands"))if(!result.has(k))result.put(k,JSONArray())
        result.optJSONObject("task")?.let {t->
            for(k in listOf("title","deliverable","progress"))if(!t.has(k)||t.isNull(k))t.put(k,"")
            for(k in listOf("deadline","deadlineTime"))if(!t.has(k))t.put(k,JSONObject.NULL)
            if(!t.has("constraints"))t.put("constraints",JSONArray())
        }
        result.optJSONArray("steps")?.let {steps->for(i in 0 until steps.length())steps.optJSONObject(i)?.let {s->val n=s.opt("minutes");if(n is String&&Regex("[0-9]{1,3}").matches(n))s.put("minutes",n.toInt())}}
        result.optJSONArray("schedules")?.let{a->for(i in 0 until a.length())a.optJSONObject(i)?.let{s->for(key in listOf("daysOfWeek","excludedDates"))if(!s.has(key))s.put(key,JSONArray());val n=s.opt("minutes");if(n is String&&Regex("[0-9]{1,3}").matches(n))s.put("minutes",n.toInt())}}
        result.optJSONArray("questions")?.let {questions->for(i in 0 until questions.length())questions.optJSONObject(i)?.let {q->
            if(!q.has("options"))q.put("options",JSONArray())
            normalizeOptions(q)
        }}
        return result
    }

    private fun normalizeOptions(question:JSONObject){
        val options=question.optJSONArray("options")?:return
        val normalized=JSONArray()
        for(i in 0 until options.length()){
            val item=options.opt(i)
            val option=if(item is String)JSONObject().put("label",item.trim()).put("value",item.trim())else item
            if(option is JSONObject){
                var label=option.opt("label") as? String
                var value=option.opt("value") as? String
                if(label!=null){label=label.trim();option.put("label",label)}
                if(value!=null){value=value.trim();option.put("value",value)}
                val missingValue=!option.has("value")||option.isNull("value")||value?.isBlank()==true
                if(missingValue&&!label.isNullOrBlank()){
                    // The screen already has a free-text answer field. An empty "custom" chip
                    // must not become an invented answer such as a date or a task requirement.
                    if(Regex("^(其他|其它|自定义|自行填写|手动填写|自填|稍后填写|待补充)([（(：:].*)?$").matches(label))continue
                    option.put("value",label) // Preserve the model's explicit choice verbatim.
                }
                if((!option.has("label")||option.isNull("label")||label?.isBlank()==true)&&!value.isNullOrBlank()&&value.length<=100)option.put("label",value)
            }
            normalized.put(option)
        }
        question.put("options",normalized)
    }

    /** A command proposal never doubles as a plan save or an interview response. */
    fun validateCommandEnvelope(raw:JSONObject):JSONArray {
        val commands=if(raw.has("commands"))raw.optJSONArray("commands")
            ?:throw AiFormatException("APP_COMMAND","commands 必须是操作数组。") else JSONArray()
        if(commands.length()==0)return commands
        fun fail():Nothing=throw AiFormatException("APP_COMMAND","操作预览必须使用 clarify，不能同时返回计划、问题或计划归属。")
        if(raw.optString("status")!="clarify"||raw.opt("startsNewGoal")!=false)fail()
        if(raw.has("targetPlanId")&&!raw.isNull("targetPlanId"))fail()
        if(raw.optString("shortTitle").isNotBlank())fail()
        for(key in listOf("questions","steps","schedules"))if(raw.optJSONArray(key)?.length()!=0)fail()
        val task=raw.optJSONObject("task")?:fail()
        for(key in listOf("title","deliverable","progress"))if(task.optString(key).isNotBlank())fail()
        for(key in listOf("deadline","deadlineTime"))if(!task.isNull(key))fail()
        if(task.optJSONArray("constraints")?.length()!=0)fail()
        return commands
    }

    fun content(value:String):JSONObject {
        var body=value.trim().trimStart('\uFEFF').trim()
        if(body.isEmpty())throw AiFormatException("EMPTY_CONTENT","AI 返回了空内容。")
        if(body.startsWith("```")){
            val lines=body.lines()
            if(lines.size>=3&&lines.last().trim()=="```"&&lines.first().trim().lowercase() in listOf("```json","```"))body=lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        try {
            var tokener=JSONTokener(body)
            var parsed=tokener.nextValue()
            if(tokener.nextClean()!='\u0000')throw IllegalArgumentException("Trailing text")
            if(parsed is String){tokener=JSONTokener(parsed);parsed=tokener.nextValue();if(tokener.nextClean()!='\u0000')throw IllegalArgumentException("Trailing text")}
            if(parsed !is JSONObject)throw IllegalArgumentException("Not an object")
            return normalize(parsed)
        } catch(e:AiFormatException){throw e}
        catch(e:Exception){throw AiFormatException("INVALID_JSON","AI 回复不是完整的任务 JSON。",value.take(100_000))}
    }

    fun envelope(response:JSONObject):JSONObject {
        val choice=response.optJSONArray("choices")?.optJSONObject(0)?:throw AiFormatException("MISSING_CHOICE","AI 没有返回回复。")
        when(choice.optString("finish_reason")){
            "length"->throw AiFormatException("TRUNCATED","AI 回复超过长度上限，未生成完整计划。")
            "content_filter"->throw AiResponseException("AI 服务未能处理这条请求，请调整任务描述。")
            "stop"->Unit
            else->throw AiResponseException("AI 响应未正常结束，请重试。")
        }
        val value=choice.optJSONObject("message")?.opt("content")
        if(value==null||value===JSONObject.NULL)throw AiFormatException("EMPTY_CONTENT","AI 返回了空内容。")
        if(value !is String)throw AiFormatException("INVALID_CONTENT_TYPE","AI 回复的内容类型无效。")
        return content(value)
    }

    fun assistantWire(body:String):String {
        val raw=try{content(body)}catch(e:Exception){emptyClarification(body)}
        return JSONObject().apply{wireKeys.forEach{k->if(raw.has(k))put(k,raw.get(k))}}.toString()
    }

    fun repairedPayload(original:JSONObject,error:AiFormatException,invalid:JSONObject?,maximum:Int):JSONObject {
        val copy=JSONObject(original.toString())
        if(error.code=="TRUNCATED")copy.put("max_tokens",minOf(maximum.coerceAtLeast(1),maxOf(original.optInt("max_tokens",12000),24000)))
        val messages=copy.getJSONArray("messages")
        val raw=invalid?.toString()?:error.candidate
        if(!raw.isNullOrBlank())messages.put(JSONObject().put("role","assistant").put("content",raw.take(100_000)))
        val field=error.fieldPath?.let{" 错误位置：$it。"}.orEmpty()
        val correction="上一条输出未通过App校验：${error.code}，${error.message}。$field 请修正并重新生成完整JSON。保留用户事实；task的未知文字用空字符串，未知日期用null，不得编造日期或要求。questions.options的label/value必须是非空字符串，value是用户选择后直接提交的答案；自定义答案由App输入框填写，不生成空value占位选项。不要输出JSON Schema、Markdown、时间前缀或额外说明。操作提案放commands，使用clarify且questions/steps/schedules为空、task为空、targetPlanId=null、startsNewGoal=false；不能与每日计划混发。只引用设备上下文中已知的ID，不声称已经执行。重复任务放schedules，单次任务放steps，合计最多120条规则，由App展开每日任务，不逐日重复输出。不得省略后续日期；无法确定法定节假日时返回clarify，请用户选择星期规则或提供确切假期日期。"
        messages.put(JSONObject().put("role","user").put("content",correction))
        return copy
    }
}

/** At most one corrective model call. Transport/account errors are never disguised as JSON errors. */
object AiPlannerPipeline {
    fun execute(payload:JSONObject,maximum:Int,call:(JSONObject)->JSONObject,validate:(JSONObject)->JSONObject,onFailure:(AiFormatException,Int)->Unit={_,_->}):JSONObject {
        var request=JSONObject(payload.toString())
        for(attempt in 0..1){
            var invalid:JSONObject?=null
            try{
                invalid=call(request)
                try{return validate(invalid)}catch(e:org.json.JSONException){throw AiFormatException("PLAN_SHAPE","AI 回复包含错误的字段类型。",invalid.toString())}
            }catch(e:AiFormatException){
                onFailure(e,attempt+1)
                if(attempt==1)throw AiFormatException(e.code,"${e.message} 已自动修正一次但仍未通过，请点重试。",fieldPath=e.fieldPath)
                request=AiReplyCodec.repairedPayload(payload,e,invalid,maximum)
            }
        }
        error("Unreachable")
    }
}

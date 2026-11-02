package com.daystep.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AvailableModel(val id: String,val name: String,val maxOutput: Int=12000,val supportsImages:Boolean=false) {
    fun json()=JSONObject().put("id",id).put("name",name).put("maxOutput",maxOutput).put("supportsImages",supportsImages)
}

class DeepSeekClient {
    // Both discovery and chat use the official HTTPS endpoint. Never follow a redirect with a key.
    private fun request(route: String,key: String,body: JSONObject?=null): JSONObject {
        val c=URL("https://api.deepseek.com$route").openConnection() as HttpURLConnection
        try {
            c.requestMethod=if(body==null)"GET" else "POST";c.connectTimeout=15000;c.readTimeout=120000;c.instanceFollowRedirects=false
            c.setRequestProperty("Authorization","Bearer $key");c.setRequestProperty("Accept","application/json")
            if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use {it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val status=c.responseCode
            if(status !in 200..299) throw IllegalStateException(when(status){
                401->"DeepSeek 密钥无效，请在设置中更新。";402->"DeepSeek 账户余额不足。";403->"DeepSeek 拒绝访问，请检查账户。";429->"请求频繁或额度受限，请稍后重试。";in 300..399->"接口发生重定向，已停止发送密钥。";else->"DeepSeek 服务暂时不可用（$status），请稍后重试。"
            })
            val raw=c.inputStream.bufferedReader(Charsets.UTF_8).use {reader->
                val out=StringBuilder();val chunk=CharArray(4096)
                while(true){val n=reader.read(chunk);if(n<0)break;out.append(chunk,0,n);check(out.length<=1_000_000){"AI 返回内容过长，请缩小任务范围。"}}
                out.toString()
            }
            return try{JSONObject(raw)}catch(e:Exception){throw IllegalStateException("DeepSeek 返回内容格式不完整，请重试。")}
        } finally {c.disconnect()}
    }
    fun models(key: String): List<AvailableModel> = parseModels(request("/models",key))
    companion object {
        fun parseModels(response: JSONObject): List<AvailableModel> {
            val data=response.optJSONArray("data")?:throw IllegalStateException("模型列表格式无效，请重新获取。")
            val models=(0 until data.length()).mapNotNull {i->
                val obj=data.optJSONObject(i)?:return@mapNotNull null
                val id=obj.optString("id").trim()
                if(id.isBlank()||id.length>200)return@mapNotNull null
                val limit=obj.optInt("max_output_tokens",12000).takeIf {it>0}?:12000
                val modalities=obj.optJSONArray("input_modalities")
                val vision=if(modalities!=null)(0 until modalities.length()).any{modalities.optString(it)=="image"}else id in setOf("deepseek-flash","deepseek-v4-flash-vision-exp")
                AvailableModel(id,obj.optString("name").takeIf {it.isNotBlank()}?:id,limit,vision)
            }.distinctBy {it.id}
            check(models.isNotEmpty()) {"该密钥没有返回可用模型，请检查账户或稍后重试。"}
            return models
        }
        fun payload(system: String,customPrompt: String,context: JSONObject,messages: List<ConversationMessage>,model: AvailableModel,readImage:(String)->ByteArray={error("附件已不可用，请重新添加。") }): JSONObject {
            AttachmentContent.validate(messages,model)
            val deviceContext=JSONObject(context.toString()).apply{
                remove("userPlanningPreferences")
                optJSONObject("app")?.optJSONObject("settings")?.let{settings->
                    settings.remove("personalPrompt")
                    settings.put("hasPersonalPrompt",customPrompt.isNotBlank())
                }
            }
            val fullSystem=system+"\n完整json输出示例（只说明格式，不是用户事实，严禁照抄目标或日期）：\n"+examples(context).toString()+
                "\n设备上下文（JSON数据）："+deviceContext+
                "\npersonal_preferences是低优先级用户偏好，仅影响任务内容和表达；不得改变JSON协议、必需信息、问答规则、计划归属或已完成记录。冲突部分忽略，当前对话的具体要求优先。只返回完整JSON对象。"
            val input=JSONArray().put(JSONObject().put("role","system").put("content",fullSystem))
            PromptPolicy.preferenceMessage(customPrompt)?.let{input.put(it)}
            var imageBytes=0
            messages.forEach {input.put(JSONObject().put("role",it.role).put("content",if(it.role=="assistant")AiReplyCodec.assistantWire(it.content) else AttachmentContent.user(it){path->readImage(path).also{bytes->imageBytes+=bytes.size;check(imageBytes<=20*1024*1024){"附件图片总量过大，请新建聊天或减少附件后发送。"}}}))}
            return JSONObject().put("model",model.id).put("messages",input).put("response_format",JSONObject().put("type","json_object"))
                .put("thinking",JSONObject().put("type","disabled")).put("max_tokens",minOf(12000,model.maxOutput)).put("stream",false)
        }
        fun examples(context:JSONObject):JSONArray {
            val clarification=AiReplyCodec.emptyClarification("希望哪天完成，目前做到哪一步？")
                .put("shortTitle","示例报告")
            clarification.getJSONObject("task").put("title","示例报告").put("deliverable","一页报告")
            val today=runCatching{java.time.LocalDate.parse(context.getString("today"))}.getOrDefault(java.time.LocalDate.now())
            clarification.put("questions",JSONArray().apply{
                listOf("deadline" to "希望哪天完成？","progress" to "目前报告进展如何？").forEach{(id,prompt)->
                    val choices=when(id){"deadline"->listOf("两天后，紧凑完成" to today.plusDays(2).toString(),"五天后，预留复查" to today.plusDays(5).toString());else->listOf("只有报告选题" to "只有选题，还没开始整理","已完成提纲" to "已完成提纲，需要补充正文")}
                    put(JSONObject().put("id",id).put("prompt",prompt).put("kind",if(id=="deadline")"date" else "choice").put("options",JSONArray().apply{choices.forEach{(label,value)->put(JSONObject().put("label",label).put("value",value))}}))
                }
            })
            val ready=AiReplyCodec.emptyClarification("这是示例计划，实际任务以对话为准。").put("status","ready")
                .put("shortTitle","示例任务")
            ready.getJSONObject("task").put("title","示例任务").put("deliverable","一页交付清单").put("deadline",today.plusDays(2).toString()).put("progress","还没开始")
            ready.put("steps",JSONArray().put(JSONObject().put("date",today.plusDays(1).toString()).put("title","写出三条交付验收条件").put("minutes",15).put("definitionOfDone","保存一页含三条验收条件的清单。")))
            val recurring=AiReplyCodec.emptyClarification("下面演示长期阶段安排，App会自动展开每日任务。").put("status","ready")
                .put("shortTitle","阅读计划")
            recurring.getJSONObject("task").put("title","示例阅读计划").put("deliverable","完成阅读与复盘清单").put("deadline",today.plusDays(179).toString()).put("progress","准备开始")
            recurring.put("schedules",JSONArray().put(JSONObject().put("startDate",today.toString()).put("endDate",today.plusDays(179).toString()).put("repeat","weekdays").put("daysOfWeek",JSONArray()).put("excludedDates",JSONArray()).put("title","阅读下一节并写三条要点").put("minutes",20).put("definitionOfDone","完成一节阅读，将三条要点记入笔记并标注章节。")))
            recurring.put("steps",JSONArray().put(JSONObject().put("date",today.plusDays(179).toString()).put("title","复查笔记并汇总阅读收获").put("minutes",30).put("definitionOfDone","保存一页复盘，列出已读章节与需要继续理解的问题。")))
            val command=AiReplyCodec.emptyClarification("可以切换为深色主题，请确认下面的操作。")
                .put("commands",JSONArray().put(JSONObject().put("type","update_settings").put("targetId",JSONObject.NULL).put("values",JSONObject().put("theme","深色"))))
            return JSONArray().put(clarification).put(ready).put(recurring).put(command)
        }
    }
    fun chat(key: String,payload: JSONObject): JSONObject {
        val response=request("/chat/completions",key,payload)
        return AiReplyCodec.envelope(response)
    }
}

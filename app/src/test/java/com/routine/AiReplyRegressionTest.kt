package com.routine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class AiReplyRegressionTest {
    private val now=ZonedDateTime.parse("2026-10-29T18:00:00+08:00[Asia/Shanghai]")
    private fun context()=JSONObject().put("today",now.toLocalDate().toString())
    private fun ready()=DeepSeekClient.examples(context()).getJSONObject(1)
    private fun response(content:Any=JSONObject.NULL,reason:String="stop")=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",reason).put("message",JSONObject().put("content",content))))
    private fun payload()=DeepSeekClient.payload("只输出json","回复简洁",context(),listOf(ConversationMessage("user","明天交报告，已有资料","2026-10-29T18:00:00+08:00")),AvailableModel("test-model","测试",30000))
    private fun fails(code:String,call:()->Unit){try{call();fail("Expected $code")}catch(e:AiFormatException){assertEquals(code,e.code)}}

    @Test fun allPromptExamplesPassTheActualPlanValidatorAndUiParser(){
        val examples=DeepSeekClient.examples(context())
        for(i in 0 until examples.length()){val r=PlanRules.validate(AiReplyCodec.content(examples.getJSONObject(i).toString()),now);PlannerResult.parse(r)}
    }
    @Test fun historyDoesNotDemonstrateTimestampPrefixesOrClientOnlyPlanIds(){
        val raw=ready().put("planId","client-generated-id")
        val request=DeepSeekClient.payload("输出json","简洁",context(),listOf(ConversationMessage("assistant",raw.toString(),"2026-10-29T17:00:00+08:00"),ConversationMessage("user","把第一天细分一下","2026-10-29T18:00:00+08:00")),AvailableModel("x","x"))
        val messages=request.getJSONArray("messages")
        val assistant=JSONObject(messages.getJSONObject(2).getString("content"))
        assertFalse(assistant.has("planId"));assertEquals("ready",assistant.getString("status"));assertTrue(messages.getJSONObject(3).getString("content").startsWith("[消息时间"))
        assertTrue(messages.getJSONObject(0).getString("content").contains("完整json输出示例"))
    }
    @Test fun stripsOnlyAWholeJsonCodeFenceAndByteOrderMark(){
        val r=AiReplyCodec.content("\uFEFF  ```json\n${ready()}\n``` ")
        assertEquals("ready",r.getString("status"));PlanRules.validate(r,now)
    }
    @Test fun acceptsADoubleEncodedJsonObject(){
        assertEquals("ready",AiReplyCodec.content(JSONObject.quote(ready().toString())).getString("status"))
    }
    @Test fun rejectsArbitraryProseAndDoesNotExtractAnUntrustedEmbeddedObject(){fails("INVALID_JSON"){AiReplyCodec.content("这里有个示例：${ready()}，请参考。")}}
    @Test fun nullAndWhitespaceAreDiagnosedAsEmptyReplies(){for(value in listOf(JSONObject.NULL,"   ",""))fails("EMPTY_CONTENT"){AiReplyCodec.envelope(response(value))}}
    @Test fun truncationAndRefusalAreNotReportedAsJsonSyntaxErrors(){
        fails("TRUNCATED"){AiReplyCodec.envelope(response("{\"reply\":","length"))}
        try{AiReplyCodec.envelope(response("","content_filter"));fail("Expected refusal")}catch(e:AiResponseException){assertTrue(e.message!!.contains("未能处理"))}
    }
    @Test fun rejectsSchemaEchoes(){fails("SCHEMA_ECHO"){AiReplyCodec.content("""{"type":"object","properties":{"reply":{"type":"string"}}}""")}}
    @Test fun omittedUnknownFieldsRemainUnknownInsteadOfInventingTaskFacts(){
        val r=AiReplyCodec.content("""{"reply":"什么时候截止？","status":"clarify","task":{"title":"报告"},"questions":[{"id":"ddl","prompt":"截止日期？","kind":"date"}]}""")
        assertTrue(r.getJSONObject("task").isNull("deadline"));assertFalse(r.getJSONObject("task").has(RetiredTimeBudget.FIELD));assertEquals(0,r.getJSONArray("steps").length());PlannerResult.parse(PlanRules.validate(r,now))
    }
    @Test fun emptyDefaultsNeverTurnAnIncompleteReadyIntoAConfirmablePlan(){
        val r=AiReplyCodec.content("""{"reply":"好了","status":"ready","task":{"title":"报告"}}""")
        fails("PLAN_VALIDATION"){PlanRules.validate(r,now)}
    }
    @Test fun preservesNumericInformationAndQuestionOptionsDuringFormatNormalization(){
        val r=ready();r.getJSONArray("steps").getJSONObject(0).put("minutes","15")
        val parsed=AiReplyCodec.content(r.toString());assertEquals(15,parsed.getJSONArray("steps").getJSONObject(0).getInt("minutes"));PlanRules.validate(parsed,now)
        val q=AiReplyCodec.emptyClarification().put("questions",JSONArray("""[{"id":"scope","prompt":"覆盖哪些内容？","kind":"choice","options":["第一章","第二章"]}]"""))
        assertEquals("第二章",AiReplyCodec.content(q.toString()).getJSONArray("questions").getJSONObject(0).getJSONArray("options").getJSONObject(1).getString("value"))
    }
    @Test fun formatFailuresTriggerOneCorrectionWithJsonModePreserved(){
        var calls=0;var second:JSONObject?=null
        val original=payload()
        val result=AiPlannerPipeline.execute(original,30000,{body->calls++;if(calls==1)throw AiFormatException("EMPTY_CONTENT","AI 返回空内容") else{second=body;ready()}},{PlanRules.validate(it,now)})
        assertEquals(2,calls);assertEquals("ready",result.getString("status"));assertEquals("json_object",second!!.getJSONObject("response_format").getString("type"));assertEquals(3,original.getJSONArray("messages").length())
    }
    @Test fun semanticValidationFailuresAreAlsoCorrectedBeforeTheUiSeesThem(){
        var calls=0
        val result=AiPlannerPipeline.execute(payload(),30000,{calls++;if(calls==1)ready().apply{getJSONObject("task").remove("deadline")}else ready()},{PlanRules.validate(AiReplyCodec.normalize(it),now)})
        assertEquals(2,calls);assertEquals("ready",result.getString("status"))
    }
    @Test fun repairIsBoundedAndAccountErrorsAreNotRetried(){
        var calls=0
        fails("INVALID_JSON"){AiPlannerPipeline.execute(payload(),30000,{calls++;throw AiFormatException("INVALID_JSON","无效格式")},{it})};assertEquals(2,calls)
        calls=0;try{AiPlannerPipeline.execute(payload(),30000,{calls++;throw IllegalStateException("密钥无效")},{it});fail("Expected account error")}catch(e:IllegalStateException){assertEquals("密钥无效",e.message)};assertEquals(1,calls)
    }
    @Test fun truncatedReplyRetriesRespectTheModelsDeclaredOutputLimit(){
        val original=payload()
        assertEquals(24000,AiReplyCodec.repairedPayload(original,AiFormatException("TRUNCATED","输出截断"),null,30000).getInt("max_tokens"))
        assertEquals(6000,AiReplyCodec.repairedPayload(original,AiFormatException("TRUNCATED","输出截断"),null,6000).getInt("max_tokens"))
    }
}

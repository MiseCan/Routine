package com.routine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class QuestionOptionsRegressionTest {
    private val now=ZonedDateTime.parse("2026-11-06T21:57:00+08:00[Asia/Shanghai]")
    private fun clarification()=DeepSeekClient.examples(JSONObject().put("today",now.toLocalDate().toString())).getJSONObject(0).apply{
        put("startsNewGoal",true);put("shortTitle","日常习惯")
        getJSONObject("task").put("title","每日习惯提醒").put("deliverable","每天完成后记录")
    }
    private fun validate(raw:JSONObject)=AppReplyValidation.validate(raw,now,emptyList(),"",2,true,emptySet(),emptySet())

    @Test fun dailyReminderClarificationWithEmptyValuesUsesExplicitLabelsWithoutInventingFacts(){
        val raw=clarification()
        val options=raw.getJSONArray("questions").getJSONObject(0).getJSONArray("options")
        options.getJSONObject(0).put("value","")
        options.getJSONObject(1).put("value",JSONObject.NULL)
        raw.getJSONArray("questions").getJSONObject(1).getJSONArray("options").getJSONObject(0).remove("value")
        var calls=0
        val result=AiPlannerPipeline.execute(JSONObject().put("messages",JSONArray()),12000,{calls++;raw},::validate)
        assertEquals(1,calls)
        val checked=result.getJSONArray("questions").getJSONObject(0).getJSONArray("options")
        assertEquals(options.getJSONObject(0).getString("label"),checked.getJSONObject(0).getString("value"))
        assertTrue(result.getJSONObject("task").isNull("deadline"))
        assertEquals("",result.getJSONObject("task").getString("progress"))
        assertEquals("clarify",result.getString("status"))
        assertEquals(0,result.getJSONArray("steps").length())
        assertEquals("",options.getJSONObject(0).getString("value"))
        assertEquals(1,InterviewPolicy.nextRound(result,2,true))
        assertTrue(PlannerResult.parse(result).questions.isNotEmpty())
    }

    @Test fun emptyCustomPlaceholdersAreRemovedWhileRealChoicesRemainSelectable(){
        val raw=clarification()
        val options=raw.getJSONArray("questions").getJSONObject(0).getJSONArray("options")
        options.put(JSONObject().put("label","自定义（手动填写）").put("value",""))
        options.put(JSONObject().put("label","其他").put("value",JSONObject.NULL))
        val checked=validate(raw).getJSONArray("questions").getJSONObject(0).getJSONArray("options")
        assertEquals(2,checked.length())
        for(i in 0 until checked.length())assertTrue(checked.getJSONObject(i).getString("value").isNotBlank())
    }

    @Test fun normalizationIsIdempotentAndRetainsAnExplicitCustomAnswer(){
        val raw=clarification()
        val options=raw.getJSONArray("questions").getJSONObject(1).getJSONArray("options")
        options.put(JSONObject().put("label","其他").put("value","已养成习惯，只需要记录"))
        val once=AiReplyCodec.normalize(raw)
        assertEquals(once.toString(),AiReplyCodec.normalize(once).toString())
        assertEquals("已养成习惯，只需要记录",once.getJSONArray("questions").getJSONObject(1).getJSONArray("options").getJSONObject(2).getString("value"))
    }

    @Test fun nullUnknownTextBecomesEmptyTextButNeverMakesAReadyPlanValid(){
        val raw=AiReplyCodec.emptyClarification("请补充任务内容。").put("shortTitle",JSONObject.NULL)
        val task=raw.getJSONObject("task")
        for(key in listOf("title","deliverable","progress"))task.put(key,JSONObject.NULL)
        val normalized=AiReplyCodec.normalize(raw)
        for(key in listOf("title","deliverable","progress"))assertEquals("",normalized.getJSONObject("task").getString(key))
        assertTrue(normalized.getJSONObject("task").isNull("deadline"))
        assertEquals("",normalized.getString("shortTitle"))
        PlanRules.validate(normalized,now)
        normalized.put("status","ready")
        try{PlanRules.validate(normalized,now);fail("Unknown task facts must not become a confirmable plan")}catch(error:AiFormatException){assertEquals("PLAN_VALIDATION",error.code)}
    }

    @Test fun structuredInvalidValuesAreNotSilentlyStringifiedAndRepairNamesTheExactField(){
        val raw=clarification()
        raw.getJSONArray("questions").getJSONObject(0).getJSONArray("options").getJSONObject(0).put("value",JSONObject().put("date","2026-11-08"))
        var calls=0
        var retry:JSONObject?=null
        val result=AiPlannerPipeline.execute(JSONObject().put("messages",JSONArray()),12000,{body->
            calls++
            if(calls==1)raw else {retry=body;clarification()}
        },::validate)
        assertEquals(2,calls)
        assertEquals("clarify",result.getString("status"))
        val correction=retry!!.getJSONArray("messages").let{it.getJSONObject(it.length()-1).getString("content")}
        assertTrue(correction.contains("questions[0].options[0].value"))
        assertTrue(correction.contains("非空字符串"))
    }

    @Test fun missingRecommendationsAndOversizedValuesStillFailTheExistingBoundaries(){
        val customOnly=clarification()
        customOnly.getJSONArray("questions").getJSONObject(0).put("options",JSONArray().put(JSONObject().put("label","自定义").put("value","")))
        try{validate(customOnly);fail("Expected missing recommendations")}catch(error:AiFormatException){assertEquals("TASK_RECOMMENDATIONS",error.code)}
        val tooLong=clarification()
        tooLong.getJSONArray("questions").getJSONObject(1).getJSONArray("options").getJSONObject(0).put("value","x".repeat(1001))
        try{validate(tooLong);fail("Expected length rejection")}catch(error:AiFormatException){assertEquals("QUESTION_OPTION",error.code);assertEquals("questions[1].options[0].value",error.fieldPath)}
    }
}

package com.daystep.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.ZonedDateTime

class AppCommandProtocolTest {
    private val context=JSONObject().put("today","2026-11-01")
    private fun command()=JSONObject().put("type","update_settings").put("targetId",JSONObject.NULL).put("values",JSONObject().put("theme","深色"))
    private fun proposal()=AiReplyCodec.emptyClarification("请确认切换为深色主题。").put("commands",JSONArray().put(command()))
    private fun fails(block:()->Unit){
        try{block();fail("Expected command protocol rejection")}
        catch(expected:AiFormatException){assertEquals("APP_COMMAND",expected.code)}
    }

    @Test fun oldPlanRepliesGainAnEmptyCommandListWithoutChangingTheirPlan(){
        val old=DeepSeekClient.examples(context).getJSONObject(1).apply{remove("commands")}
        val clean=AiReplyCodec.content(old.toString())
        assertEquals(0,clean.getJSONArray("commands").length())
        assertEquals(old.getJSONArray("steps").toString(),clean.getJSONArray("steps").toString())
        assertEquals("ready",clean.getString("status"))
        assertEquals(0,AiReplyCodec.validateCommandEnvelope(clean).length())
    }

    @Test fun commandProposalAndItsArgumentsSurviveAssistantHistoryExactly(){
        val checked=proposal().put("planId","client-only").put("dailySteps",JSONArray())
        val wire=JSONObject(AiReplyCodec.assistantWire(checked.toString()))
        assertEquals(checked.getJSONArray("commands").toString(),wire.getJSONArray("commands").toString())
        assertEquals(1,AiReplyCodec.validateCommandEnvelope(wire).length())
        assertFalse(wire.has("planId"));assertFalse(wire.has("dailySteps"))
    }

    @Test fun wrongCommandContainersAreRejectedRatherThanSilentlyDropped(){
        for(value in listOf<Any>("update_settings",JSONObject(),JSONObject.NULL))fails{
            AiReplyCodec.validateCommandEnvelope(AiReplyCodec.normalize(proposal().put("commands",value)))
        }
    }

    @Test fun commandsCannotBeMixedWithAReadyPlanOrInterviewOrImplicitPlanTarget(){
        for(raw in listOf(
            proposal().put("status","ready"),
            proposal().put("questions",JSONArray().put(JSONObject())),
            proposal().put("steps",JSONArray().put(JSONObject())),
            proposal().put("schedules",JSONArray().put(JSONObject())),
            proposal().put("targetPlanId","old-plan"),
            proposal().put("startsNewGoal",true),
            proposal().apply{getJSONObject("task").put("title","同时生成的新计划")}
        ))fails{AiReplyCodec.validateCommandEnvelope(raw)}
    }

    @Test fun readOnlyResponsesNeedNoTaskIntakeOrActionProposal(){
        val response=AiReplyCodec.emptyClarification("今天没有未完成任务。")
        assertEquals(0,AiReplyCodec.validateCommandEnvelope(response).length())
        val checked=PlanRules.validate(response,ZonedDateTime.parse("2026-11-01T10:00:00+08:00[Asia/Shanghai]"))
        assertEquals("clarify",checked.getString("status"))
        assertEquals(0,checked.getJSONArray("questions").length())
    }

    @Test fun commandFormatErrorsUseTheExistingSingleRepairPath(){
        var calls=0
        val payload=DeepSeekClient.payload("内置协议","",context,listOf(ConversationMessage("user","切换深色","2026-11-01T10:00:00+08:00")),AvailableModel("test","test"))
        val checked=AiPlannerPipeline.execute(payload,12000,{
            calls++
            if(calls==1)proposal().put("status","ready")else proposal()
        },{raw->AiReplyCodec.validateCommandEnvelope(raw);raw})
        assertEquals(2,calls)
        assertEquals("update_settings",checked.getJSONArray("commands").getJSONObject(0).getString("type"))
        assertTrue(AiReplyCodec.repairedPayload(payload,AiFormatException("APP_COMMAND","Invalid"),null,12000)
            .getJSONArray("messages").toString().contains("操作提案放commands"))
    }

    @Test fun schemaRequiresCommandShapeAndOnlySupportedSingleConversationOperations(){
        val system=listOf(File("src/main/assets/planner-system.txt"),File("app/src/main/assets/planner-system.txt")).first{it.exists()}.readText()
        val schema=JSONObject(system.substringAfter("JSON schema："))
        val commands=schema.getJSONObject("properties").getJSONObject("commands")
        assertEquals("array",commands.getString("type"));assertEquals(20,commands.getInt("maxItems"))
        val item=commands.getJSONObject("items")
        val required=item.getJSONArray("required")
        assertEquals(setOf("type","targetId","values"),(0 until required.length()).map{required.getString(it)}.toSet())
        assertFalse(item.getBoolean("additionalProperties"))
        val kinds=item.getJSONObject("properties").getJSONObject("type").getJSONArray("enum")
        val types=(0 until kinds.length()).map{kinds.getString(it)}.toSet()
        assertTrue(types.containsAll(listOf("complete_task","delete_plan","update_settings","select_plan","clear_chat","confirm_plan","add_attachment","inspect_tasks")))
        assertTrue(types.intersect(setOf("new_chat","open_chat","rename_chat","delete_chat","set_key","set_system_prompt")).isEmpty())
        assertEquals(7,schema.getJSONObject("properties").getJSONObject("shortTitle").getInt("maxLength"))
    }

    @Test fun meaningfulShortNamesArePreservedWhileOversizeAndEllipsisAreRejected(){
        val ready=DeepSeekClient.examples(context).getJSONObject(1).put("shortTitle","考研英语复习")
        assertEquals("考研英语复习",AiReplyCodec.normalize(ready).getString("shortTitle"))
        for(title in listOf("这是超过七字的计划名称","考研英语…","英语...")){
            try{AiReplyCodec.normalize(JSONObject(ready.toString()).put("shortTitle",title));fail("Expected short title rejection")}
            catch(expected:AiFormatException){assertEquals("PLAN_SHORT_TITLE",expected.code)}
        }
        val legacy=JSONObject(ready.toString()).apply{remove("shortTitle")}
        assertEquals("",AiReplyCodec.normalize(legacy).getString("shortTitle"))
    }
}

package com.routine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

/** Exercises replies through the same validation/repair boundary used by the real client. */
class AppControlIntegrationTest {
    private val now=ZonedDateTime.parse("2026-11-01T10:00:00+08:00[Asia/Shanghai]")
    private val context=JSONObject().put("today",now.toLocalDate().toString())
    private val model=AvailableModel("test-model","测试模型")
    private val task=Step("task-1","英语复习计划","复习第一单元",now.toLocalDate().toString(),"2027-12-31",30,definitionOfDone="完成本单元自测",planId="plan-1",shortProject="英语复习")
    private fun command(type:String,id:String?=null,values:JSONObject=JSONObject())=
        JSONObject().put("type",type).put("targetId",id?:JSONObject.NULL).put("values",values)
    private fun proposal(c:JSONObject)=AiReplyCodec.emptyClarification("请确认下面的操作。").put("commands",JSONArray().put(c))
    private fun validate(raw:JSONObject,steps:List<Step> = listOf(task),selected:String="",round:Int=0)=
        AppReplyValidation.validate(raw,now,steps,selected,round,true,setOf(model.id),emptySet())
    private fun payload(text:String="关闭今日任务提醒",preference:String="",ctx:JSONObject=context)=
        DeepSeekClient.payload("内置规则",preference,ctx,listOf(ConversationMessage("user",text,now.toOffsetDateTime().toString())),model)
    private fun pipeline(raw:JSONObject,steps:List<Step> = listOf(task),selected:String=""):JSONObject =
        AiPlannerPipeline.execute(payload(),12000,{JSONObject(raw.toString())},{validate(it,steps,selected)})
    private fun fails(code:String,block:()->Unit){
        try{block();fail("Expected $code")}
        catch(expected:AiFormatException){assertEquals(code,expected.code)}
    }

    @Test fun readOnlyQuestionDoesNotStartTaskIntakeOrRequireSelectedPlanTarget(){
        val response=AiReplyCodec.emptyClarification("今天有一项未完成任务：复习第一单元。")
        val checked=pipeline(response,selected="plan-1")
        assertEquals("clarify",checked.getString("status"))
        assertEquals(0,checked.getJSONArray("questions").length())
        assertEquals(0,checked.getJSONArray("commands").length())
        assertTrue(checked.getJSONObject("task").getString("title").isBlank())
        assertFalse(task.done)
    }

    @Test fun completingTaskNeedsUsersNoteAndDoesNotWriteBeforeExplicitApply(){
        val userNote="完成第一单元自测，整理了三道错题。"
        val raw=proposal(command("complete_task",task.id,JSONObject().put("note",userNote)))
        val before=listOf(task)
        val checked=AiPlannerPipeline.execute(payload("我已完成第一单元自测，整理了三道错题，请记录完成。"),12000,{raw},{validate(it,before)})
        val commands=AppCommands.parse(checked.getJSONArray("commands"))
        assertFalse(before.single().done);assertTrue(before.single().completions.isEmpty())
        val after=AppCommands.applySteps(before,commands,now.toOffsetDateTime().toString())
        assertTrue(after.single().done);assertEquals(userNote,after.single().completions.single().note)
        assertEquals(task.id,after.single().id);assertEquals("英语复习",after.single().shortProject)
        fails("APP_COMMAND"){validate(proposal(command("complete_task",task.id)))}
        fails("APP_COMMAND"){validate(proposal(command("complete_task",task.id,JSONObject().put("note","  "))))}
    }

    @Test fun aSettingsRequestWorksWithoutInventingTaskFactsOrChangingTheSelectedPlan(){
        val raw=proposal(command("update_settings",values=JSONObject().put("theme","深色").put("remindersEnabled",false)))
        val checked=pipeline(raw,selected="plan-1")
        assertEquals("clarify",checked.getString("status"))
        assertTrue(checked.isNull("targetPlanId"))
        assertEquals("深色",checked.getJSONArray("commands").getJSONObject(0).getJSONObject("values").getString("theme"))
        assertTrue(checked.getJSONObject("task").isNull("deadline"))
        assertEquals(listOf(task),AppCommands.applySteps(listOf(task),AppCommands.parse(checked.getJSONArray("commands")),now.toString()))
    }

    @Test fun planRevisionCannotSwitchAnExplicitlySelectedTarget(){
        val ready=DeepSeekClient.examples(context).getJSONObject(1).put("targetPlanId","plan-2")
        val other=task.copy(id="other-task",planId="plan-2")
        fails("REVISION_TARGET"){validate(ready,listOf(task,other),"plan-1")}
        fails("UNKNOWN_PLAN"){validate(JSONObject(ready.toString()).put("targetPlanId","deleted-plan"),listOf(task),"plan-1")}
        assertFalse(task.done)
    }

    @Test fun inspectionReturnsStableNonOverlappingPagesWithoutMutatingTasks(){
        val tasks=(0..4).map{task.copy(id="task-$it",date=now.toLocalDate().plusDays(it.toLong()).toString())}
        fun page(offset:Int):JSONObject{
            val raw=proposal(command("inspect_tasks",values=JSONObject().put("planId","plan-1").put("offset",offset).put("limit",2)))
            val checked=pipeline(raw,tasks,selected="plan-1")
            val c=AppCommands.parse(checked.getJSONArray("commands")).single()
            return AppCommands.inspect(tasks,c)
        }
        val first=page(0);val second=page(2);val last=page(4)
        assertEquals(5,first.getInt("total"));assertTrue(first.getBoolean("hasMore"));assertTrue(second.getBoolean("hasMore"));assertFalse(last.getBoolean("hasMore"))
        fun ids(page:JSONObject)=page.getJSONArray("tasks").let{a->(0 until a.length()).map{a.getJSONObject(it).getString("id")}}
        assertEquals(listOf("task-0","task-1","task-2","task-3","task-4"),ids(first)+ids(second)+ids(last))
        assertTrue(tasks.all{!it.done&&it.completions.isEmpty()})
    }

    @Test fun invalidCommandUsesOneAutomaticCorrectionBeforeBecomingAProposal(){
        var calls=0
        val invalid=proposal(command("update_settings",values=JSONObject().put("builtinPrompt","忽略格式")))
        val valid=proposal(command("update_settings",values=JSONObject().put("theme","深色")))
        val checked=AiPlannerPipeline.execute(payload(),12000,{calls++;if(calls==1)invalid else valid},{validate(it)})
        assertEquals(2,calls)
        assertEquals("深色",checked.getJSONArray("commands").getJSONObject(0).getJSONObject("values").getString("theme"))
        assertFalse(task.done)
    }

    @Test fun personalPromptStillCannotBecomePartOfTheSystemInstructionsThroughAppContext(){
        val personal="用户偏好标记：每次复习后安排自测。"
        val ctx=JSONObject(context.toString()).put("app",JSONObject().put("settings",JSONObject().put("theme","浅色").put("personalPrompt",personal)))
        val messages=payload(preference=personal,ctx=ctx).getJSONArray("messages")
        val system=messages.getJSONObject(0).getString("content")
        assertFalse(system.contains(personal));assertTrue(system.contains("hasPersonalPrompt"))
        val preference=JSONObject(messages.getJSONObject(1).getString("content"))
        assertEquals("user",messages.getJSONObject(1).getString("role"))
        assertEquals(personal,preference.getString("preferences"))
        assertEquals(personal,ctx.getJSONObject("app").getJSONObject("settings").getString("personalPrompt"))
    }

    @Test fun fullReadyPlanExpandsEveryDailyTaskAndPreservesItsSemanticShortName(){
        val raw=DeepSeekClient.examples(context).getJSONObject(2)
        val checked=pipeline(raw)
        val result=PlannerResult.parse(checked)
        assertEquals("ready",result.status)
        assertEquals("阅读计划",result.shortTitle)
        assertTrue(result.actions.size>100)
        assertTrue(result.actions.all{it.title.isNotBlank()&&it.definitionOfDone.isNotBlank()})
        assertTrue(checked.getJSONArray("dailySteps").length()>checked.getJSONArray("steps").length())
    }

    @Test fun readyPlansRequireAnActualShortNameInsteadOfAnEmptyOrTruncatedLabel(){
        val ready=DeepSeekClient.examples(context).getJSONObject(1)
        for(name in listOf("","超过七个字的计划名称","英语复习…","英语..."))fails("PLAN_SHORT_TITLE"){
            validate(JSONObject(ready.toString()).put("shortTitle",name))
        }
        assertEquals("考研英语复习",validate(JSONObject(ready.toString()).put("shortTitle","考研英语复习")).getString("shortTitle"))
    }
}

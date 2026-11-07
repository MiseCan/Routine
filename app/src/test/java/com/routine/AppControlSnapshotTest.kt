package com.routine

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AppControlSnapshotTest {
    private val today=LocalDate.of(2026,11,1)
    private fun step(id:String="task-a",plan:String="plan-a",date:LocalDate=today)=Step(id,"计划完整名称$plan","行动$id",date.toString(),"2027-12-31",20,definitionOfDone="保存本次练习结果",planId=plan)
    private val attachment=ChatAttachment("file-a","参考资料.txt","text/plain","正文不应重复放入上下文")
    private fun build(steps:List<Step>,selected:String="",inspection:JSONObject?=null)=AppControlSnapshot.build(steps,selected,today,"深色",true,true,"练习优先","real-model",listOf(AvailableModel("real-model","官方返回名称")),listOf(attachment),inspection,"上次执行成功")
    private fun stamp(steps:List<Step>,theme:String="深色",dynamicColor:Boolean=true,remindersEnabled:Boolean=true,personalPrompt:String="练习优先",model:String="real-model",attachments:List<ChatAttachment> = listOf(attachment))=
        AppControlSnapshot.stamp(steps,theme,dynamicColor,remindersEnabled,personalPrompt,model,attachments)

    @Test fun allPlanSummariesRemainAvailableWhileTaskScopesAreBounded(){
        val busyToday=(1..140).map{step("today-$it")}
        val selected=(1..150).map{step("selected-$it","plan-b",today.plusDays(it.toLong()))}
        val other=step("other","plan-c",today.plusYears(1))
        val snapshot=build(busyToday+selected+other,"plan-b")
        assertEquals(3,snapshot.getJSONArray("plans").length())
        assertEquals(291,snapshot.getInt("totalTaskCount"));assertEquals(200,snapshot.getJSONArray("tasks").length())
        assertTrue(snapshot.getBoolean("hasMore"));assertTrue(snapshot.getBoolean("truncated"))
        assertTrue(snapshot.getJSONObject("taskScope").getBoolean("todayHasMore"));assertTrue(snapshot.getJSONObject("taskScope").getBoolean("selectedPlanHasMore"))
        val plans=snapshot.getJSONArray("plans")
        assertEquals("计划完整名称plan-b",plans.getJSONObject(1).getString("title"));assertEquals(150,plans.getJSONObject(1).getInt("taskCount"))
        assertTrue(snapshot.getString("readMore").contains("inspect_tasks"))
    }

    @Test fun overlappingTodayAndSelectedPlanTasksAreNotDuplicated(){
        val items=listOf(step(),step("future","plan-a",today.plusDays(1)),step("other","plan-b",today.plusDays(2)))
        val snapshot=build(items,"plan-a")
        assertEquals(2,snapshot.getJSONArray("tasks").length())
        assertEquals(3,snapshot.getInt("totalTaskCount"));assertTrue(snapshot.getBoolean("hasMore"))
        assertEquals(1,snapshot.getJSONObject("taskScope").getInt("todayTaskCount"))
        assertEquals(2,snapshot.getJSONObject("taskScope").getInt("selectedPlanTaskCount"))
    }

    @Test fun exactIdsCompletionStateAndLastThreeRecordsAreVisible(){
        val records=(1..5).map{CompletionRecord("实际完成$it","2026-11-01T10:0$it:00+08:00")}
        val snapshot=build(listOf(step().copy(done=true,completions=records)))
        val task=snapshot.getJSONArray("tasks").getJSONObject(0)
        assertEquals("task-a",task.getString("id"));assertEquals("plan-a",task.getString("planId"));assertTrue(task.getBoolean("done"))
        assertEquals("保存本次练习结果",task.getString("definitionOfDone"));assertEquals(5,task.getInt("completionRecordCount"))
        assertEquals(3,task.getJSONArray("completionRecords").length());assertEquals("实际完成3",task.getJSONArray("completionRecords").getJSONObject(0).getString("note"))
        assertEquals(1,snapshot.getJSONArray("plans").getJSONObject(0).getInt("doneCount"))
    }

    @Test fun globalsAndAttachmentReferencesAreIncludedWithoutDuplicatingBodiesOrCredentials(){
        val snapshot=build(listOf(step()))
        assertEquals("real-model",snapshot.getJSONObject("settings").getString("model"));assertEquals("深色",snapshot.getJSONObject("settings").getString("theme"))
        assertEquals("real-model",snapshot.getJSONArray("modelIds").getString(0));assertEquals("file-a",snapshot.getJSONArray("attachments").getJSONObject(0).getString("id"))
        assertFalse(snapshot.toString().contains(attachment.text));assertFalse(snapshot.toString().contains("apiKey"));assertFalse(snapshot.has("chats"))
        assertEquals("上次执行成功",snapshot.getString("execution"))
    }

    @Test fun inspectionIsCopiedAndEmptySnapshotDoesNotClaimTruncation(){
        val inspection=JSONObject().put("total",4).put("hasMore",true)
        val snapshot=build(emptyList(),inspection=inspection)
        inspection.put("total",999)
        assertEquals(4,snapshot.getJSONObject("inspection").getInt("total"));assertFalse(snapshot.getBoolean("hasMore"));assertFalse(snapshot.getBoolean("truncated"))
        assertEquals(0,snapshot.getJSONArray("plans").length());assertTrue(snapshot.isNull("selectedPlanId"))
    }

    @Test fun stampIsStableForIdenticalContentIncludingEscapesAndUnicode(){
        val items=listOf(step().copy(title="记录\"带引号\"的内容\n第二行"))
        assertEquals(stamp(items),stamp(decode(encode(items))))
        assertTrue(Regex("[a-f0-9]{64}").matches(stamp(items)))
    }

    @Test fun changesToFutureTasksLogsSettingsModelAndDraftFilesInvalidateAProposal(){
        val future=step(date=today.plusYears(1));val original=stamp(listOf(future))
        assertNotEquals(original,stamp(listOf(future.copy(title="已手动修改"))))
        assertNotEquals(original,stamp(listOf(future.copy(completions=listOf(CompletionRecord("完成内容","2026-11-01T10:00:00+08:00"))))))
        assertNotEquals(original,stamp(listOf(future),theme="浅色"));assertNotEquals(original,stamp(listOf(future),dynamicColor=false))
        assertNotEquals(original,stamp(listOf(future),remindersEnabled=false));assertNotEquals(original,stamp(listOf(future),personalPrompt="改成复习优先"))
        assertNotEquals(original,stamp(listOf(future),model="other-model"));assertNotEquals(original,stamp(listOf(future),attachments=emptyList()))
        assertNotEquals(original,stamp(listOf(future),attachments=listOf(attachment.copy(text="修改后的资料"))))
    }
}

package com.routine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppCommandsTest {
    private val at="2026-11-01T10:00:00+08:00"
    private fun step(id:String="a",plan:String="p")=Step(id,"学习计划","阅读第1章","2026-11-01","2026-11-30",30,definitionOfDone="记录3个要点",planId=plan)
    private fun command(type:String,target:String?=null,vararg values:Pair<String,Any>)=AppCommand(type,target,JSONObject().apply{values.forEach{put(it.first,it.second)}})
    private fun parse(vararg commands:AppCommand)=AppCommands.parse(JSONArray().apply{commands.forEach{put(it.json())}})
    private fun rejected(block:()->Unit){try{block();fail("Expected APP_COMMAND rejection")}catch(e:AiFormatException){assertEquals("APP_COMMAND",e.code)}}
    private fun validate(commands:List<AppCommand>,steps:List<Step> = listOf(step()),models:Set<String> = setOf("model-a"),attachments:Set<String> = setOf("file-a"))=AppCommands.validate(commands,steps,emptySet(),models,attachments)

    @Test fun onlyDocumentedCommandsAndFieldsAreAccepted(){
        rejected{parse(command("execute_code",null,"code" to "anything"))}
        rejected{parse(command("update_settings",null,"apiKey" to "secret"))}
        rejected{parse(command("update_settings",null,"builtinPrompt" to "replace rules"))}
        rejected{parse(command("update_task","a","done" to true))}
        val raw=command("delete_task","a").json().put("runImmediately",true)
        rejected{AppCommands.parse(JSONArray().put(raw))}
    }

    @Test fun taskTargetsMustBeAccurateNonblankIds(){
        rejected{parse(command("delete_task"))}
        rejected{parse(command("delete_task","  "))}
        rejected{validate(parse(command("update_task","missing","title" to "新任务")))}
        rejected{parse(command("update_settings","unexpected","theme" to "浅色"))}
    }

    @Test fun fieldTypesRangesAndCalendarDatesAreStrict(){
        rejected{parse(command("update_task","a","minutes" to "30"))}
        rejected{parse(command("update_task","a","minutes" to 30.5))}
        rejected{parse(command("update_task","a","minutes" to 0))}
        rejected{parse(command("update_task","a","minutes" to 481))}
        rejected{parse(command("update_task","a","date" to "2026-02-30"))}
        rejected{parse(command("update_task","a","date" to "2026-1-1"))}
        rejected{parse(command("update_plan","p","deadline" to "2026-11-30 25:00"))}
        rejected{parse(command("update_task","a"))}
    }

    @Test fun completingRequiresActualNonblankNoteAndRetainsEarlierLogs(){
        rejected{parse(command("complete_task","a","note" to "  "))}
        val original=step().copy(completions=listOf(CompletionRecord("先前的记录","2026-10-31T10:00:00+08:00")))
        val result=AppCommands.applySteps(listOf(original),parse(command("complete_task","a","note" to " 阅读并整理了3个要点 ")),at).single()
        assertTrue(result.done);assertEquals(2,result.completions.size)
        assertEquals(original.completions.first(),result.completions.first())
        assertEquals(CompletionRecord("阅读并整理了3个要点",at),result.completions.last())
    }

    @Test fun duplicateCompletionCannotAppendAnUnintendedSecondLog(){
        val original=completedStep(step(),"已经完成",at)
        rejected{AppCommands.applySteps(listOf(original),parse(command("complete_task","a","note" to "再次完成")),at)}
        assertEquals(1,original.completions.size)
    }

    @Test fun reopeningPreservesRecordsAndDoesNotTouchOtherTasks(){
        val original=completedStep(step(),"已完成阅读",at)
        val other=step("b","q")
        val result=AppCommands.applySteps(listOf(original,other),parse(command("reopen_task","a")),at)
        assertFalse(result.first().done);assertEquals(original.completions,result.first().completions)
        assertEquals(other,result.last())
    }

    @Test fun manualTaskEditsPreserveCompletionHistoryAndPlanIdentity(){
        val original=completedStep(step(),"已记录的内容",at)
        val result=AppCommands.applySteps(listOf(original),parse(command("update_task","a","title" to "阅读第2章","minutes" to 45,"definitionOfDone" to "保存笔记")),at).single()
        assertEquals("阅读第2章",result.title);assertEquals(45,result.minutes)
        assertEquals("p",result.planId);assertEquals(original.completions,result.completions);assertTrue(result.done)
    }

    @Test fun deletingAPlanRemovesItsTasksAndRecordsOnly(){
        val first=completedStep(step(),"完成记录",at)
        val second=step("b")
        val other=step("c","other")
        val result=AppCommands.applySteps(listOf(first,second,other),parse(command("delete_plan","p")),at)
        assertEquals(listOf(other),result)
        val summary=AppCommands.summaries(parse(command("delete_plan","p")),listOf(first,second,other),emptyMap()).single()
        assertTrue(summary.contains("2 项每日任务"));assertTrue(summary.contains("1 条完成记录"))
    }

    @Test fun addingATaskUsesExistingPlanMetadataAndRequiresValidDates(){
        val cmd=command("create_task",null,"planId" to "p","title" to "复盘","date" to "2026-11-02","minutes" to 20,"definitionOfDone" to "记录3条收获")
        val original=step()
        val result=AppCommands.applySteps(listOf(original),parse(cmd),at)
        assertEquals(original,result.first());assertEquals(2,result.size)
        assertNotEquals(original.id,result.last().id);assertEquals("p",result.last().planId)
        assertEquals(original.project,result.last().project);assertEquals(original.ddl,result.last().ddl)
        rejected{validate(parse(cmd.copy(values=JSONObject(cmd.values.toString()).put("date","2026-12-01"))))}
        rejected{validate(parse(cmd.copy(values=JSONObject(cmd.values.toString()).put("planId","missing"))))}
    }

    @Test fun planMetadataChangesApplyToAllStepsWhilePreservingRecords(){
        val done=completedStep(step(),"保留",at)
        val later=step("b").copy(date="2026-11-15")
        val result=AppCommands.applySteps(listOf(done,later),parse(command("update_plan","p","title" to "新计划名","deadline" to "2026-12-01 20:30")),at)
        assertTrue(result.all{it.project=="新计划名"&&it.ddl=="2026-12-01 20:30"})
        assertEquals(done.completions,result.first().completions)
        rejected{validate(parse(command("update_plan","p","deadline" to "2026-11-01")),listOf(done,later))}
    }

    @Test fun batchValidationSimulatesOrderingWithoutMutatingOriginalData(){
        val original=step().copy(date="2026-11-15")
        val move=command("update_task","a","date" to "2026-11-05")
        val shorten=command("update_plan","p","deadline" to "2026-11-10")
        validate(parse(move,shorten),listOf(original))
        rejected{validate(parse(shorten,move),listOf(original))}
        rejected{AppCommands.applySteps(listOf(original),parse(move,command("delete_task","nonexistent")),at)}
        assertEquals("2026-11-15",original.date);assertEquals("2026-11-30",original.ddl)
    }

    @Test fun operationsOnObjectsDeletedEarlierInBatchFailBeforeExecution(){
        rejected{validate(parse(command("delete_plan","p"),command("complete_task","a","note" to "完成")))}
        rejected{validate(parse(command("delete_task","a"),command("select_plan","p")))}
    }

    @Test fun modelPlanAndAttachmentReferencesMustStillExist(){
        validate(parse(command("select_model","model-a")))
        rejected{validate(parse(command("select_model","missing")))}
        validate(parse(command("remove_attachment","file-a")))
        rejected{validate(parse(command("remove_attachment","sent-file")))}
        validate(parse(command("select_plan","p")))
        validate(parse(command("select_plan")))
        rejected{validate(parse(command("navigate","missing","page" to "plan")))}
    }

    @Test fun settingsAcceptOnlySupportedTypedPreferences(){
        val c=command("update_settings",null,"theme" to "深色","dynamicColor" to false,"remindersEnabled" to true,"personalPrompt" to "练习优先")
        validate(parse(c))
        rejected{parse(command("update_settings",null,"theme" to "custom"))}
        rejected{parse(command("update_settings",null,"dynamicColor" to "true"))}
        rejected{parse(command("update_settings",null,"personalPrompt" to "a".repeat(8001)))}
        validate(parse(command("update_settings",null,"personalPrompt" to "")))
        val summary=AppCommands.summaries(parse(c),listOf(step()),emptyMap()).single()
        assertTrue(summary.contains("深色"));assertTrue(summary.contains("练习优先"));assertTrue(summary.contains("关闭动态取色"))
    }

    @Test fun disruptiveOperationsAreStandaloneAndLargeBatchesAreRejected(){
        for(type in listOf("clear_chat","refresh_models","generate_plan","confirm_plan")){
            parse(command(type))
            rejected{parse(command("delete_task","a"),command(type))}
        }
        rejected{parse(command("navigate",null,"page" to "today"),command("delete_task","a"))}
        rejected{AppCommands.parse(JSONArray().apply{repeat(21){put(command("update_settings",null,"theme" to "浅色").json())}})}
    }

    @Test fun singleChatProtocolHasNoArchivedChatOperations(){
        listOf("new_chat","open_chat","rename_chat","delete_chat").forEach{type->rejected{parse(command(type,"old-id"))}}
        rejected{parse(command("navigate",null,"page" to "chat_list"))}
        parse(command("navigate",null,"page" to "chat"))
        val summary=AppCommands.summaries(parse(command("clear_chat")),listOf(step()),emptyMap()).single()
        assertTrue(summary.contains("消息、草稿和附件"));assertTrue(summary.contains("保留看板"))
    }

    @Test fun reviewShowsTheExactCompletionNoteAndTaskContext(){
        val note="阅读了第一章，并保存了3条笔记。"
        val text=AppCommands.summaries(parse(command("complete_task","a","note" to note)),listOf(step()),emptyMap()).single()
        assertTrue(text.contains(note));assertTrue(text.contains("阅读第1章"));assertTrue(text.contains("2026-11-01"))
    }

    @Test fun wireRoundTripDoesNotShareTheMutableValuesObject(){
        val original=command("update_settings",null,"theme" to "浅色")
        val result=parse(original).single()
        original.values.put("theme","深色")
        assertEquals("浅色",result.values.getString("theme"))
    }

    @Test fun inspectionPaginatesAFullYearWithoutLosingIdsOrRepeatingTasks(){
        val start=java.time.LocalDate.of(2026,11,1)
        val steps=(0..364).map{i->step("day-$i").copy(date=start.plusDays(i.toLong()).toString(),ddl="2027-10-31")}.reversed()
        val first=AppCommands.inspect(steps,command("inspect_tasks",null,"planId" to "p","limit" to 200))
        assertEquals(365,first.getInt("total"));assertEquals(0,first.getInt("offset"));assertTrue(first.getBoolean("hasMore"))
        assertEquals(200,first.getJSONArray("tasks").length());assertEquals("day-0",first.getJSONArray("tasks").getJSONObject(0).getString("id"))
        val second=AppCommands.inspect(steps,command("inspect_tasks",null,"planId" to "p","limit" to 200,"offset" to 200))
        assertEquals(165,second.getJSONArray("tasks").length());assertFalse(second.getBoolean("hasMore"))
        assertEquals("day-200",second.getJSONArray("tasks").getJSONObject(0).getString("id"))
        val beyond=AppCommands.inspect(steps,command("inspect_tasks",null,"offset" to Int.MAX_VALUE))
        assertEquals(0,beyond.getJSONArray("tasks").length());assertFalse(beyond.getBoolean("hasMore"))
    }

    @Test fun inspectionFiltersPlanAndInclusiveDatesAndReturnsFullCompletionRecords(){
        val logged=completedStep(step().copy(date="2026-11-02"),"已阅读并保存完整笔记",at)
        val items=listOf(step().copy(id="old"),logged,step("later").copy(date="2026-11-03"),step("other","q").copy(date="2026-11-02"))
        val cmd=command("inspect_tasks",null,"planId" to "p","from" to "2026-11-02","to" to "2026-11-02")
        validate(parse(cmd),items)
        val result=AppCommands.inspect(items,cmd)
        assertEquals(1,result.getInt("total"))
        val task=result.getJSONArray("tasks").getJSONObject(0)
        assertEquals("a",task.getString("id"));assertEquals("p",task.getString("planId"));assertTrue(task.getBoolean("done"))
        assertEquals(logged.definitionOfDone,task.getString("definitionOfDone"));assertEquals(logged.ddl,task.getString("ddl"))
        assertEquals(logged.completions.single().note,task.getJSONArray("completionRecords").getJSONObject(0).getString("note"))
        assertEquals(2,AppCommands.inspect(items,command("inspect_tasks",null,"date" to "2026-11-02")).getInt("total"))
        assertEquals(4,AppCommands.inspect(items,command("inspect_tasks")).getJSONArray("tasks").length())
    }

    @Test fun inspectionRejectsUnknownPlansInvalidFiltersAndMixedMutationBatches(){
        rejected{validate(parse(command("inspect_tasks",null,"planId" to "missing")))}
        rejected{AppCommands.inspect(listOf(step()),command("inspect_tasks",null,"planId" to "missing"))}
        rejected{parse(command("inspect_tasks",null,"limit" to 201))}
        rejected{parse(command("inspect_tasks",null,"limit" to 0))}
        rejected{parse(command("inspect_tasks",null,"offset" to -1))}
        rejected{parse(command("inspect_tasks",null,"offset" to 1.2))}
        rejected{parse(command("inspect_tasks",null,"date" to "2026-02-30"))}
        rejected{parse(command("inspect_tasks",null,"from" to "2026-11-05","to" to "2026-11-01"))}
        rejected{parse(command("inspect_tasks"),command("delete_plan","p"))}
    }

    @Test fun newTasksCopyThePlanShortTitleAndRenamingClearsAnObsoleteShortTitle(){
        val original=step().copy(shortProject="阅读入门")
        val add=command("create_task",null,"planId" to "p","title" to "复盘","date" to "2026-11-02","minutes" to 20,"definitionOfDone" to "保存复盘")
        val withNewTask=AppCommands.applySteps(listOf(original),parse(add),at)
        assertEquals("阅读入门",withNewTask.last().shortProject)
        val unchanged=AppCommands.applySteps(withNewTask,parse(command("update_plan","p","deadline" to "2026-12-01")),at)
        assertTrue(unchanged.all{it.shortProject=="阅读入门"})
        val renamed=AppCommands.applySteps(withNewTask,parse(command("update_plan","p","title" to "新的完整计划名称")),at)
        assertTrue(renamed.all{it.shortProject.isEmpty()})
    }

    @Test fun planShortTitleIsValidatedAsSevenCharactersAndIncludedInReview(){
        val c=command("update_plan","p","title" to "学习基础知识并完成进阶练习","shortTitle" to "基础进阶")
        val result=AppCommands.applySteps(listOf(step()),parse(c),at).single()
        assertEquals("学习基础知识并完成进阶练习",result.project);assertEquals("基础进阶",result.shortProject)
        assertTrue(AppCommands.summaries(parse(c),listOf(step()),emptyMap()).single().contains("简称改为「基础进阶」"))
        parse(command("update_plan","p","shortTitle" to "😀".repeat(7)))
        rejected{parse(command("update_plan","p","shortTitle" to "一二三四五六七八"))}
        rejected{parse(command("update_plan","p","shortTitle" to "阅读…"))}
        rejected{parse(command("update_plan","p","shortTitle" to "阅读..."))}
    }
}

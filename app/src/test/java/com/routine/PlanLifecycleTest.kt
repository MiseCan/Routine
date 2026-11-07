package com.routine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlanLifecycleTest {
    private fun step(id:String,plan:String="a",done:Boolean=false)=Step(id,"同名计划","学习词汇","2026-10-30","2026-11-05",30,done,planId=plan)
    @Test fun deletingOnePlanRemovesItsPendingAndCompletedDailyTasksOnly(){
        val log=CompletionRecord("背诵了20个单词","2026-10-29T20:00:00+08:00")
        val other=step("other","b")
        val result=deletePlan(listOf(step("a-0"),step("a-1",done=true).copy(completions=listOf(log)),other),"a")
        assertEquals(listOf(other),result)
    }
    @Test fun upcomingIsExactlyTomorrowAcrossYearBoundary(){
        val today=LocalDate.of(2026,12,31)
        val next=step("tomorrow").copy(date="2027-01-01")
        assertEquals(listOf(next),tomorrowSteps(listOf(next,step("later").copy(date="2027-01-02"),step("today").copy(date="2026-12-31")),today))
    }
    @Test fun completionRequiresANonblankNote(){try{completedStep(step("a"),"   ","2026-10-29T20:00:00+08:00");fail("Blank note accepted")}catch(expected:IllegalArgumentException){}}
    @Test fun actualCompletionDatesAndNotesSurvivePersistenceAndRepeatCompletion(){
        val first=completedStep(step("a")," 学会あ行 ","2026-10-29T20:00:00+08:00")
        val again=completedStep(first.copy(done=false),"复习并默写あ行","2026-10-30T09:00:00+08:00")
        val loaded=decode(encode(listOf(again))).single()
        assertTrue(loaded.done);assertEquals(2,loaded.completions.size);assertEquals("学会あ行",loaded.completions[0].note);assertEquals("2026-10-30",loaded.completions[1].at.take(10))
    }
    @Test fun revisionReplacesEditableTasksAndKeepsOtherPlansAndCompletionRecords(){
        val done=completedStep(step("finished"),"已完成内容","2026-10-29T20:00:00+08:00")
        val unDoneWithHistory=done.copy(id="undone",done=false)
        val old=step("old")
        val other=step("other","b")
        val replacement=step("new").copy(title="修改后的任务")
        val result=mergeRevision(listOf(old,done,unDoneWithHistory,other),listOf(replacement),"a")
        assertFalse(result.any{it.id=="old"});assertEquals(4,result.size);assertEquals(done.completions,result.first{it.id=="finished"}.completions);assertTrue(result.contains(other));assertTrue(result.contains(unDoneWithHistory))
    }
    @Test fun deletedPlanCannotBeResurrectedByAnOldRevision(){try{mergeRevision(emptyList(),listOf(step("new")),"a");fail("Deleted plan recreated")}catch(expected:IllegalArgumentException){}}
    @Test fun revisionBudgetExcludesOnlyTheEditablePartOfTheTarget(){
        val target=step("old")
        val locked=completedStep(step("logged"),"原记录","2026-10-29T20:00:00+08:00").copy(done=false)
        val other=step("other","b")
        assertEquals(listOf(locked,other),revisionBase(listOf(target,locked,other),"a"))
    }
}

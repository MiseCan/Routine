package com.daystep.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class ScheduleRulesTest {
    private val now=ZonedDateTime.parse("2026-10-29T18:00:00+08:00[Asia/Shanghai]")
    private fun rule(start:String="2026-10-29",end:String="2027-04-26",repeat:String="daily")=JSONObject().put("startDate",start).put("endDate",end).put("repeat",repeat).put("daysOfWeek",JSONArray()).put("excludedDates",JSONArray()).put("title","背下一组单词并复习昨日错词").put("minutes",20).put("definitionOfDone","记入20个新词，完成昨日错词自测并保存结果。")
    private fun ready(rule:JSONObject=rule()):JSONObject=AiReplyCodec.emptyClarification("已按阶段安排。").put("status","ready").apply{getJSONObject("task").put("title","长期单词计划").put("deliverable","完成单词学习与自测记录").put("progress","准备开始").put("deadline",rule.getString("endDate"));put("schedules",JSONArray().put(rule))}
    private fun dates(a:JSONArray)=(0 until a.length()).map{a.getJSONObject(it).getString("date")}
    private fun fails(code:String,block:()->Unit){try{block();fail("Expected $code")}catch(e:AiFormatException){assertEquals(code,e.code)}}
    @Test fun oneRuleCreates180RealDailyActionsIncludingBothEndpoints(){
        val response=PlanRules.validate(ready(),now);val parsed=PlannerResult.parse(response)
        assertEquals(180,parsed.actions.size);assertEquals("2026-10-29",parsed.actions.first().date);assertEquals("2027-04-26",parsed.actions.last().date)
        assertEquals(0,response.getJSONArray("steps").length());assertEquals(1,response.getJSONArray("schedules").length());assertEquals(180,response.getJSONArray("dailySteps").length())
        assertTrue(parsed.actions.all{it.title==rule().getString("title")&&it.minutes==20&&it.definitionOfDone.isNotBlank()})
    }
    @Test fun oneYearPlanNoLongerRequiresHundredsOfWireSteps(){val r=rule(end="2027-10-28");assertEquals(365,PlannerResult.parse(PlanRules.validate(ready(r),now)).actions.size)}
    @Test fun weekdayAndWeekendRulesCoverExactlyTheSelectedDays(){
        for(repeat in listOf("weekdays","weekends")){val raw=rule(end="2026-11-11",repeat=repeat);val actions=PlannerResult.parse(PlanRules.validate(ready(raw),now)).actions;assertEquals(if(repeat=="weekdays")10 else 4,actions.size);assertTrue(actions.all{(LocalDate.parse(it.date).dayOfWeek.value<=5)==(repeat=="weekdays")})}
    }
    @Test fun weeklyRuleAndAnExcludedDayAreAppliedLocally(){
        val r=rule(end="2026-11-11",repeat="weekly").put("daysOfWeek",JSONArray().put(1).put(3)).put("excludedDates",JSONArray().put("2026-11-04"))
        assertEquals(listOf("2026-11-02","2026-11-09","2026-11-11"),PlannerResult.parse(PlanRules.validate(ready(r),now)).actions.map{it.date})
    }
    @Test fun statutoryHolidaysAndMakeupDaysDoNotMeanOrdinaryWeekends(){
        val start=LocalDate.parse("2026-09-19");val end=LocalDate.parse("2026-10-11")
        fun expanded(type:String)=dates(ScheduleRules.expand(JSONArray(),JSONArray().put(rule(start.toString(),end.toString(),type)),start,end))
        assertTrue("2026-09-20" in expanded("workdays"));assertTrue("2026-10-10" in expanded("workdays"))
        assertFalse("2026-10-01" in expanded("workdays"));assertTrue("2026-10-01" in expanded("holidays"))
        assertFalse("2026-10-11" in expanded("holidays"));assertTrue("2026-10-11" in expanded("rest_days"))
    }
    @Test fun unknownHolidayYearsAreNotGuessedOrTreatedAsWeekends(){fails("CALENDAR_UNAVAILABLE"){PlanRules.validate(ready(rule(repeat="workdays")),now)}}
    @Test fun aSingleDayExceptionCanReplaceAnOccurrenceWithoutDuplicatingIt(){
        val r=rule(end="2026-11-04").put("excludedDates",JSONArray().put("2026-11-01"))
        val raw=ready(r).put("steps",JSONArray().put(JSONObject().put("date","2026-11-01").put("title","做一次阶段自测").put("minutes",40).put("definitionOfDone","保存自测分数和错题。")))
        val actions=PlannerResult.parse(PlanRules.validate(raw,now)).actions;assertEquals(7,actions.size);assertEquals("做一次阶段自测",actions.single{it.date=="2026-11-01"}.title)
    }
    @Test fun identicalOverlapsAreDeduplicatedAndDistinctActionsOnTheSameDayRemain(){
        val a=rule(end="2026-11-04");val b=JSONObject(a.toString()).put("title","练习听力")
        val raw=ready(a).put("schedules",JSONArray().put(a).put(a).put(b))
        assertEquals(14,PlannerResult.parse(PlanRules.validate(raw,now)).actions.size)
    }
    @Test fun invalidRangesAndRepeatValuesAreRejectedInsteadOfDroppingDays(){
        for(r in listOf(rule(start="2026-11-05",end="2026-11-01"),rule(start="2026-10-28"),rule(repeat="somedays"),rule().put("excludedDates",JSONArray().put("2028-01-01")),rule(repeat="weekly").put("daysOfWeek",JSONArray().put(8))))fails("SCHEDULE_VALIDATION"){PlanRules.validate(ready(r),now)}
    }
    @Test fun nonReadyRepliesCannotCarryExecutableRules(){fails("PLAN_VALIDATION"){PlanRules.validate(ready().put("status","clarify"),now)}}
    @Test fun networkDailyStepsCannotBypassExpansionOrValidation(){
        val raw=ready().put("dailySteps",JSONArray().put(JSONObject().put("date","2099-01-01")))
        val clean=AiReplyCodec.normalize(raw);assertFalse(clean.has("dailySteps"));assertEquals(180,PlanRules.validate(clean,now).getJSONArray("dailySteps").length())
    }
    @Test fun assistantHistorySendsTheRuleRatherThan180ExpandedSteps(){
        val checked=PlanRules.validate(ready(),now);val wire=JSONObject(AiReplyCodec.assistantWire(checked.toString()))
        assertFalse(wire.has("dailySteps"));assertFalse(wire.has("planId"));assertEquals(1,wire.getJSONArray("schedules").length());assertEquals(0,wire.getJSONArray("steps").length());assertTrue(wire.toString().length<checked.toString().length/10)
    }
    @Test fun aStoredExpandedResultSurvivesReloadWithoutRegeneratingDates(){val json=PlanRules.validate(ready(),now);assertEquals(PlannerResult.parse(json).actions,PlannerResult.parse(JSONObject(json.toString())).actions)}
    @Test fun lockedCompletionDatesArePreservedDuringLongPlanRevisions(){
        val old=Step("old-0","单词","背下一组单词并复习昨日错词","2026-10-29","2027-04-26",20,true,planId="old",completions=listOf(CompletionRecord("今天完成20词","2026-10-29T18:00:00+08:00")))
        val raw=ready().put("targetPlanId","old");val result=PlannerResult.parse(PlanRules.validate(raw,now,listOf(old)))
        assertEquals(179,result.actions.size)
        val next=result.actions.mapIndexed{i,a->Step("new-$i","单词",a.title,a.date,"2027-04-26",a.minutes,definitionOfDone=a.definitionOfDone,planId="old")}
        val saved=mergeRevision(listOf(old),next,"old");assertEquals(180,saved.size);assertTrue(saved.contains(old));assertEquals(saved,decode(encode(saved)))
        val one=completedStep(saved.first{!it.done},"完成了当天词汇","2026-10-30T18:00:00+08:00");assertTrue(one.done);assertEquals(1,saved.count{it.done})
    }
    @Test fun compactExistingPlansRoundTripExactDatesEvenAfterManualExceptions(){
        val steps=(0L..179L).filter{it!=7L}.map{i->Step("old-$i","单词","背20词",now.toLocalDate().plusDays(i).toString(),"2027-04-26",20,definitionOfDone="保存自测结果",planId="old")}
        val packed=PlanWire.existingPlans(steps).getJSONObject(0);assertEquals(1,packed.getJSONArray("schedules").length())
        val expanded=ScheduleRules.expand(packed.getJSONArray("steps"),packed.getJSONArray("schedules"),now.toLocalDate(),LocalDate.parse("2027-04-26"))
        assertEquals(steps.map{it.date},dates(expanded));assertFalse(dates(expanded).contains("2026-11-05"))
    }
    @Test fun oversizedExpansionFailsRatherThanSavingOnlyTheFirstPart(){val raw=ready(rule(end="2029-10-28"));val rules=JSONArray();repeat(5){rules.put(rule(end="2029-10-28").put("title","行动$it"))};raw.put("schedules",rules);fails("SCHEDULE_VALIDATION"){PlanRules.validate(raw,now)}}
    @Test fun emptyWeeklyMatchesAndFractionalEstimatesAreRejected(){fails("SCHEDULE_VALIDATION"){PlanRules.validate(ready(rule(start="2026-10-31",end="2026-11-01",repeat="weekdays")),now)};fails("SCHEDULE_VALIDATION"){PlanRules.validate(ready(rule().put("minutes",20.5)),now)}}
}

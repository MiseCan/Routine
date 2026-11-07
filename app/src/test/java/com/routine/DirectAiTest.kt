package com.routine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class DirectAiTest {
    @Test fun distinctPlansWithTheSameTitleStaySeparate(){
        val a=Step("plan-a-0","报告","任务","2026-11-01","2026-11-05",30)
        val b=a.copy(id="plan-b-0")
        assertNotEquals(planKey(a),planKey(b))
    }
    @Test fun planIdentitySurvivesRenamingAndDailyEdits(){
        val a=Step("original-0","报告","任务","2026-11-01","2026-11-05",30,planId="stable-plan")
        assertEquals(planKey(a),planKey(a.copy(project="新名称",title="修改内容",date="2026-11-02")))
    }
    @Test fun legacyDailyStepsMigrateToTheirOriginalGroup(){
        val a=Step("1760000000000-0","旧计划","任务1","2026-11-01","2026-11-05",30)
        assertEquals(planKey(a),planKey(a.copy(id="1760000000000-1",title="任务2")))
    }
    private val now=ZonedDateTime.parse("2026-10-29T18:00:00+08:00[Asia/Shanghai]")
    private fun ready()=JSONObject("""{"reply":"已生成计划","status":"ready","task":{"title":"课程报告","deliverable":"三页PDF报告","deadline":"2026-11-06","deadlineTime":"18:00","progress":"已有资料","constraints":[]},"questions":[],"steps":[{"date":"2026-10-29","title":"从资料提取三个结论","minutes":20,"definitionOfDone":"每个结论标注来源"},{"date":"2026-11-05","title":"检查格式并导出报告","minutes":30,"definitionOfDone":"导出三页PDF并检查引用"}],"warnings":[]}""")
    private fun rejected(block:()->Unit){try{block();fail("Expected validation failure")}catch(expected:IllegalStateException){}}
    @Test fun modelDiscoveryUsesReturnedIdsAndDisplayNames(){
        val models=DeepSeekClient.parseModels(JSONObject("""{"data":[{"id":"model-a","name":"模型 A","max_output_tokens":8000},{"id":"model-b"},{"id":"model-a"}]}"""))
        assertEquals(listOf("model-a","model-b"),models.map{it.id});assertEquals("模型 A",models[0].name);assertEquals(8000,models[0].maxOutput);assertEquals("model-b",models[1].name)
    }
    @Test fun emptyModelListsAreNotReplacedWithFakeNames(){rejected{DeepSeekClient.parseModels(JSONObject("""{"data":[]}"""))}}
    @Test fun requestsContainPromptContextAndOriginalMessageTimes(){
        val result=DeepSeekClient.payload("输出json","周日不排任务",JSONObject().put("today","2026-10-30"),listOf(ConversationMessage("user","明天交报告","2026-10-29T18:00:00+08:00")),AvailableModel("returned-model","模型",6000))
        assertEquals("returned-model",result.getString("model"));assertEquals(6000,result.getInt("max_tokens"));assertEquals("json_object",result.getJSONObject("response_format").getString("type"));assertTrue(result.toString().contains("周日不排任务"));assertTrue(result.getJSONArray("messages").getJSONObject(2).getString("content").contains("2026-10-29"))
    }
    @Test fun readyGetsADeviceGeneratedIdAndKeepsCompletionCriteria(){val a=PlanRules.validate(ready(),now);val b=PlanRules.validate(ready(),now);assertNotEquals(a.getString("planId"),b.getString("planId"));assertEquals(2,PlannerResult.parse(a).actions.size)}
    @Test fun missingInformationProducesChoiceCardsWithoutConfirmation(){
        val raw=ready().put("status","clarify").put("steps",JSONArray()).put("questions",JSONArray("""[{"id":"ddl","prompt":"何时交付？","kind":"date","options":[{"label":"明天","value":"2026-10-30"}]}]"""))
        raw.getJSONObject("task").put("deadline",JSONObject.NULL).put("deadlineTime",JSONObject.NULL)
        val r=PlannerResult.parse(PlanRules.validate(raw,now));assertNull(r.planId);assertEquals("date",r.questions.single().kind)
    }
    @Test fun readyCannotSkipRequiredDetails(){val raw=ready();raw.getJSONObject("task").put("deadline",JSONObject.NULL);rejected{PlanRules.validate(raw,now)}}
    @Test fun rejectsPastOutOfRangeAndOutOfOrderDates(){for(dates in listOf(listOf("2026-10-28","2026-11-05"),listOf("2026-10-29","2026-11-07"),listOf("2026-11-05","2026-10-29"))){val r=ready();dates.forEachIndexed{i,d->r.getJSONArray("steps").getJSONObject(i).put("date",d)};rejected{PlanRules.validate(r,now)}}}
    @Test fun dailyWorkHasNoTotalTimeCap(){val raw=ready();val a=raw.getJSONArray("steps");a.getJSONObject(1).put("date","2026-10-29");for(i in 0 until a.length())a.getJSONObject(i).put("minutes",240);assertEquals("ready",PlanRules.validate(raw,now).getString("status"))}
    @Test fun readyDoesNotRequireAnyDailyTimeField(){val r=PlanRules.validate(ready(),now);assertFalse(r.getJSONObject("task").has(RetiredTimeBudget.FIELD));assertEquals("ready",r.getString("status"))}
    @Test fun sameDayDeadlineDoesNotApplyARemainingTimeQuota(){val raw=ready().put("steps",JSONArray().put(ready().getJSONArray("steps").getJSONObject(0)));raw.getJSONObject("task").put("deadline","2026-10-29").put("deadlineTime","17:30");assertEquals("ready",PlanRules.validate(raw,now).getString("status"))}
    @Test fun rejectsImpossibleDatesAndFractionalWorkEstimates(){val a=ready();a.getJSONObject("task").put("deadline","2026-02-30");rejected{PlanRules.validate(a,now)};val b=ready();b.getJSONArray("steps").getJSONObject(0).put("minutes",20.5);rejected{PlanRules.validate(b,now)}}
    @Test fun rejectsMissingCompletionCriteriaAndTooManyQuestions(){val a=ready();a.getJSONArray("steps").getJSONObject(0).put("definitionOfDone","");rejected{PlanRules.validate(a,now)};val b=ready().put("questions",JSONArray("[{}, {}, {}]"));rejected{PlanRules.validate(b,now)}}
}

package com.routine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class InterviewPolicyTest {
    @Test fun generationButtonBecomesAvailableAtThreeAndStaysAvailable(){assertFalse(InterviewPolicy.generationAvailable(0));assertFalse(InterviewPolicy.generationAvailable(2));assertTrue(InterviewPolicy.generationAvailable(3));assertTrue(InterviewPolicy.generationAvailable(8))}
    private fun initial()=DeepSeekClient.examples(JSONObject().put("today","2026-10-29")).getJSONObject(0)
    private fun detail()=initial().apply{put("questions",JSONArray().put(JSONObject().put("id","scope").put("prompt","交付的受众与验收标准是什么？").put("kind","text").put("options",JSONArray())))}
    private fun failCode(code:String,block:()->Unit){try{block();fail("Expected rejection")}catch(e:AiFormatException){assertEquals(code,e.code)}}
    @Test fun basicFieldsAreCollectedTogetherInTheFirstBatch(){val first=initial();InterviewPolicy.validate(first,0,true);assertEquals(2,first.getJSONArray("questions").length());assertEquals(1,InterviewPolicy.nextRound(first,0,true))}
    @Test fun initialBatchCannotOmitUnknownBasicInformation(){val first=initial();first.put("questions",JSONArray().put(first.getJSONArray("questions").getJSONObject(0)));failCode("BASIC_INTAKE"){InterviewPolicy.validate(first,0,true)}}
    @Test fun recommendationsMustBeSelectableAndRemainModelProvided(){val first=initial();first.getJSONArray("questions").getJSONObject(1).put("options",JSONArray().put(JSONObject().put("label","已有大纲，按任务推荐").put("value","已有大纲")));failCode("TASK_RECOMMENDATIONS"){InterviewPolicy.validate(first,0,true)};first.getJSONArray("questions").getJSONObject(1).getJSONArray("options").put(JSONObject().put("label","已有初稿").put("value","已有初稿"));InterviewPolicy.validate(first,0,true);assertEquals("已有大纲",first.getJSONArray("questions").getJSONObject(1).getJSONArray("options").getJSONObject(0).getString("value"))}
    @Test fun secondAndThirdBatchesAskForTaskSpecificDetails(){val r=detail();InterviewPolicy.validate(r,1,false);assertEquals(2,InterviewPolicy.nextRound(r,1,false));InterviewPolicy.validate(r,2,false);assertEquals(3,InterviewPolicy.nextRound(r,2,false))}
    @Test fun aFourthQuestionBatchIsAllowedWithoutResettingItsCounter(){val r=detail().put("startsNewGoal",true);InterviewPolicy.validate(r,3,false);assertEquals(4,InterviewPolicy.nextRound(r,3,false))}
    @Test fun anExplicitNewGoalAfterAPreviewCanRestartAtRoundOne(){val r=initial().put("startsNewGoal",true);InterviewPolicy.validate(r,3,true);assertEquals(1,InterviewPolicy.nextRound(r,3,true))}
    @Test fun existingPlanRevisionsAreNotBlockedByCreationRoundCount(){val r=detail().put("targetPlanId","existing-plan");InterviewPolicy.validate(r,3,false);assertEquals(3,InterviewPolicy.nextRound(r,3,false))}
    @Test fun stoppingQuestionsNeverForcesFabricatedFacts(){val r=AiReplyCodec.emptyClarification("缺少截止日期，暂不能排定。").put("status","infeasible");InterviewPolicy.validate(r,3,false);assertTrue(r.getJSONObject("task").isNull("deadline"));assertEquals(0,r.getJSONArray("steps").length())}
    @Test fun generationAfterThreeRoundsDoesNotRequireADailyTimeBudget(){
        val now=ZonedDateTime.parse("2026-10-29T18:00:00+08:00[Asia/Shanghai]")
        val r=DeepSeekClient.examples(JSONObject().put("today","2026-10-29")).getJSONObject(1)
        assertFalse(r.getJSONObject("task").has(RetiredTimeBudget.FIELD))
        val validated=PlanRules.validate(r,now)
        assertEquals("ready",validated.getString("status"))
        InterviewPolicy.validate(validated,3,false);assertEquals(3,InterviewPolicy.nextRound(validated,3,false))
    }
}

package com.routine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RetiredTimeBudgetTest {
    private fun oldReply()=AiReplyCodec.emptyClarification("安排60分钟超过每天30分钟，请调整。").apply {
        put("status","infeasible")
        getJSONObject("task").put(RetiredTimeBudget.FIELD,30).put("title","论文报告").put("constraints",JSONArray().put("每天最多30分钟").put("提交PDF"))
        put("questions",JSONArray().put(JSONObject().put("id",RetiredTimeBudget.FIELD).put("prompt","每天可用多久？").put("kind","minutes").put("options",JSONArray())))
        put("warnings",JSONArray().put("每日工时超出预算").put("请核对参考文献"))
    }
    @Test fun oldQuotaErrorsAndQuestionsAreRemovedButTaskRequirementsRemain(){
        val clean=RetiredTimeBudget.cleanReply(oldReply())
        assertFalse(clean.getJSONObject("task").has(RetiredTimeBudget.FIELD))
        assertEquals("论文报告",clean.getJSONObject("task").getString("title"))
        assertEquals("提交PDF",clean.getJSONObject("task").getJSONArray("constraints").getString(0))
        assertEquals("请核对参考文献",clean.getJSONArray("warnings").getString(0))
        assertEquals(0,clean.getJSONArray("questions").length());assertFalse(clean.getString("reply").contains("超过每天"))
    }
    @Test fun storedChatCleanupPreservesUserMessagesAndOtherAnswers(){
        val user="每天30分钟是之前说的，现在取消这个限制"
        val state=JSONObject().put("result",oldReply().toString()).put("input","继续细改")
            .put("messages",JSONArray().put(JSONObject().put("role","user").put("content",user)).put(JSONObject().put("role","assistant").put("content",oldReply().toString())).toString())
            .put("answer_draft",JSONObject().put(RetiredTimeBudget.FIELD,"30").put("deadline","2026-11-01").toString())
        val clean=RetiredTimeBudget.cleanState(state)
        assertEquals(user,JSONArray(clean.getString("messages")).getJSONObject(0).getString("content"))
        assertEquals("继续细改",clean.getString("input"))
        assertFalse(JSONObject(clean.getString("answer_draft")).has(RetiredTimeBudget.FIELD))
        assertEquals("2026-11-01",JSONObject(clean.getString("answer_draft")).getString("deadline"))
        assertEquals(clean.toString(),RetiredTimeBudget.cleanState(clean).toString())
    }
    @Test fun cleanupPreservesReadyPlanIdentityStepsAndCompletionRecords(){
        val raw=DeepSeekClient.examples(JSONObject().put("today","2026-10-29")).getJSONObject(1).put("planId","saved-plan")
        raw.getJSONObject("task").put(RetiredTimeBudget.FIELD,30)
        val clean=RetiredTimeBudget.cleanReply(raw)
        assertEquals("ready",clean.getString("status"));assertEquals("saved-plan",clean.getString("planId"))
        assertEquals(raw.getJSONArray("steps").toString(),clean.getJSONArray("steps").toString())
        val steps=listOf(Step("saved-plan-0","报告","收集资料","2026-10-29","2026-11-01",20,true,planId="saved-plan",completions=listOf(CompletionRecord("整理了三篇资料","2026-10-29T18:00:00+08:00"))))
        assertEquals(steps,decode(encode(steps)))
    }
    @Test fun normalizedRepliesAndOutgoingHistoryContainNoRetiredField(){
        val clean=AiReplyCodec.normalize(oldReply());assertFalse(clean.getJSONObject("task").has(RetiredTimeBudget.FIELD))
        val wire=JSONObject(AiReplyCodec.assistantWire(oldReply().toString()))
        assertFalse(wire.getJSONObject("task").has(RetiredTimeBudget.FIELD))
        val examples=DeepSeekClient.examples(JSONObject().put("today","2026-10-29"))
        assertFalse(examples.toString().contains(RetiredTimeBudget.FIELD));assertFalse(examples.toString().contains("每天30分钟"))
    }
    @Test fun userCustomPromptIsPreserved(){assertEquals("用中文，周日不安排",RetiredTimeBudget.cleanPrompt("用中文，周日不安排","新默认"))}
}

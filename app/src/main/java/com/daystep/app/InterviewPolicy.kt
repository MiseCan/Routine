package com.daystep.app

import org.json.JSONObject

/** A round is one batch of AI questions and the user's combined answer, not each chip tap. */
object InterviewPolicy {
    const val GENERATE_AFTER_ROUNDS=3
    fun generationAvailable(asked:Int)=asked>=GENERATE_AFTER_ROUNDS
    fun baseRound(reply:JSONObject,asked:Int,newGoalAllowed:Boolean):Int =
        if(reply.optBoolean("startsNewGoal",false)&&newGoalAllowed)0 else asked.coerceAtLeast(0)
    private fun revision(reply:JSONObject)=reply.optString("targetPlanId").let{it.isNotBlank()&&it!="null"}
    fun nextRound(reply:JSONObject,asked:Int,newGoalAllowed:Boolean):Int =
        if(revision(reply))asked else baseRound(reply,asked,newGoalAllowed)+if((reply.optJSONArray("questions")?.length()?:0)>0)1 else 0
    fun validate(reply:JSONObject,asked:Int,newGoalAllowed:Boolean):JSONObject {
        val base=baseRound(reply,asked,newGoalAllowed)
        val questions=reply.optJSONArray("questions")?:return reply
        if(revision(reply)){if(questions.length()>3)throw AiFormatException("INTERVIEW_BATCH","细改计划每轮最多3项问题。");return reply}
        if(questions.length()==0)return reply
        val max=if(base==0)6 else 3
        if(questions.length()>max)throw AiFormatException("INTERVIEW_BATCH","首轮最多6项基础信息，后两轮每轮最多3项具体任务问题。")
        if(base==0){
            val task=reply.optJSONObject("task")?:return reply
            val ids=(0 until questions.length()).map{questions.getJSONObject(it).optString("id")}.toSet()
            val missing=listOf("title","deliverable","deadline","progress").filter{key->!task.has(key)||task.isNull(key)||task.optString(key).isBlank()}
            val omitted=missing.filterNot{it in ids}
            if(omitted.isNotEmpty())throw AiFormatException("BASIC_INTAKE","第一轮要集中补齐缺少的基础信息。请为 ${omitted.joinToString()} 各生成对应id的问题和基于该任务的推荐选项。")
            for(i in 0 until questions.length()){
                val q=questions.getJSONObject(i)
                if(q.optString("id") in listOf("deadline","progress")&&(q.optJSONArray("options")?.length()?:0)<2)throw AiFormatException("TASK_RECOMMENDATIONS","首轮截止日期和进度的问题必须各给出至少两个根据当前任务推荐的选项，供用户选择。")
            }
        }
        return reply
    }
}

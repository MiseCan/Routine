package com.routine

import org.json.JSONObject
import java.time.ZonedDateTime

/** The same boundary validates network replies for planning, read-only answers and app commands. */
object AppReplyValidation {
    fun validate(
        raw:JSONObject,now:ZonedDateTime,existing:List<Step>,selectedPlanId:String,
        questionRound:Int,newGoalAllowed:Boolean,modelIds:Set<String>,attachmentIds:Set<String>
    ):JSONObject {
        val normalized=AiReplyCodec.normalize(raw)
        val commands=AppCommands.parse(AiReplyCodec.validateCommandEnvelope(normalized))
        if(commands.isNotEmpty())AppCommands.validate(commands,existing,emptySet(),modelIds,attachmentIds)
        else if(normalized.optString("status")=="ready"||normalized.optJSONArray("questions")?.length()!=0)
            RevisionPolicy.validate(normalized,existing,selectedPlanId)
        if(normalized.optString("status")=="ready"){
            val title=normalized.getString("shortTitle")
            if(title.isBlank()||title.codePointCount(0,title.length)>7||title.contains('…')||title.contains("..."))
                throw AiFormatException("PLAN_SHORT_TITLE","请用不超过7字的完整短语概括计划名称，不能留空、截断或使用省略号。")
        }
        val checked=PlanRules.validate(normalized,now,existing)
        return if(commands.isEmpty())InterviewPolicy.validate(checked,questionRound,newGoalAllowed)else checked
    }
}

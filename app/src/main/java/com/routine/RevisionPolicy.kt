package com.routine

import org.json.JSONObject

object RevisionPolicy {
    fun validate(reply:JSONObject,existing:List<Step>,selectedId:String){
        val target=reply.optString("targetPlanId").takeIf{it.isNotBlank()&&it!="null"}
        if(target!=null&&existing.none{planKey(it)==target})throw AiFormatException("UNKNOWN_PLAN","AI 引用的计划已不存在，请重新描述要调整的计划。")
        if(selectedId.isNotBlank()&&target!=selectedId)throw AiFormatException("REVISION_TARGET","用户已选择修改计划 $selectedId，targetPlanId 必须为此ID，不能另建计划。")
    }
}

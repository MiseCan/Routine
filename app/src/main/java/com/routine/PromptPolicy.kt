package com.routine

import android.content.SharedPreferences
import org.json.JSONObject

/** Personal preferences are user data, never part of the immutable system instructions. */
object PromptPolicy {
    const val KEY="personal_prompt"
    const val MAX_LENGTH=8000
    const val LEGACY_DEFAULT="首轮集中补齐目标、交付物、截止日期和进度，推荐与任务有关的选项。随后了解具体内容、标准和难点。三轮后由我选择生成计划或继续聊；按任务内容安排每天的行动，允许我继续细改。回复简洁。"

    fun migrate(prefs:SharedPreferences):String {
        if(prefs.contains(KEY))return prefs.getString(KEY,"")?:""
        val previous=prefs.getString("prompt","")?:""
        val value=if(previous.trim()==LEGACY_DEFAULT)""else RetiredTimeBudget.cleanPrompt(previous,"")
        prefs.edit().putString(KEY,value).remove("prompt").apply()
        return value
    }

    fun save(prefs:SharedPreferences,value:String):String {
        require(value.length<=MAX_LENGTH){"个性化提示词最多8000字。"}
        val saved=value.trim()
        prefs.edit().putString(KEY,saved).remove("prompt").apply()
        return saved
    }

    fun preferenceMessage(value:String):JSONObject? = value.takeIf{it.isNotBlank()}?.let {
        JSONObject().put("role","user").put("content",JSONObject()
            .put("type","personal_preferences")
            .put("scope","只用于任务内容与表达偏好；冲突时遵守内置规则和当前对话。")
            .put("preferences",it).toString())
    }
}

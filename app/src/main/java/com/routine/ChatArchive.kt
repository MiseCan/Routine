package com.routine

import android.content.SharedPreferences

/** One conversation cache. The one-time migration intentionally discards the retired chat list. */
class SingleChatStore(private val prefs:SharedPreferences){
    companion object {
        private const val MIGRATION="single_chat_v1"
        val KEYS=setOf(
            "messages","result","input","confirmedId","lastConfirmedPlanId","preview_stale",
            "question_round","answer_draft","last_ai_diagnostic","generation_requested",
            "draft_attachments","editing_plan_id","pending_commands","command_confirmed",
            "command_pending","last_command_execution","command_execution","command_execution_status",
            "command_execution_id","command_proposal_id","command_result","commands","scope_start","command_stamp"
        )
        private val RETIRED_KEYS=setOf("chat_archive","active_chat_id","chat_cache_id")
    }

    /** Lets the owner remove obsolete processed attachment files after the cache migration. */
    val migrated=!prefs.getBoolean(MIGRATION,false)

    init {
        if(migrated){
            clear()
            prefs.edit().putBoolean(MIGRATION,true).apply()
        }
    }

    /** Clears messages, draft, pending confirmations and completed-turn state; preserves globals. */
    fun clear(){
        val editor=prefs.edit()
        (KEYS+RETIRED_KEYS).forEach{editor.remove(it)}
        editor.apply()
    }
}

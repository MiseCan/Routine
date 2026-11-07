package com.routine

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class SingleChatStoreTest {
    @Test fun firstMigrationDropsEveryOldConversationAndItsDraftState(){
        val prefs=MemoryPrefs()
        SingleChatStore.KEYS.forEach{prefs.data[it]="old-$it"}
        prefs.edit().putString("chat_archive","old multi-chat archive").putString("active_chat_id","old-chat").putString("chat_cache_id","old-chat").apply()
        val store=SingleChatStore(prefs)
        assertTrue(store.migrated)
        SingleChatStore.KEYS.forEach{assertFalse("Old conversation field remains: $it",prefs.contains(it))}
        listOf("chat_archive","active_chat_id","chat_cache_id").forEach{assertFalse(prefs.contains(it))}
    }

    @Test fun migrationPreservesKeysModelsPersonalPromptAndOtherGlobalPreferences(){
        val prefs=MemoryPrefs()
        val globals=mapOf("direct_model" to "real-model","direct_models" to "model-list","personal_prompt" to "先练习后复盘","prompt" to "尚待迁移的偏好","encrypted_key" to "opaque-ciphertext","theme" to "深色","dynamic_color" to true)
        prefs.data.putAll(globals)
        prefs.data["messages"]="old messages"
        SingleChatStore(prefs)
        globals.forEach{(key,value)->assertEquals(value,prefs.data[key])}
    }

    @Test fun restartKeepsTheNewSingleConversationIncludingUnsentInputAndPendingAction(){
        val prefs=MemoryPrefs()
        SingleChatStore(prefs)
        prefs.edit().putString("messages","new conversation").putString("input","未发送的补充").putString("result","new draft").putString("pending_commands","action preview").putInt("question_round",4).apply()
        val restarted=SingleChatStore(prefs)
        assertFalse(restarted.migrated)
        assertEquals("new conversation",prefs.getString("messages",null))
        assertEquals("未发送的补充",prefs.getString("input",null))
        assertEquals("new draft",prefs.getString("result",null))
        assertEquals("action preview",prefs.getString("pending_commands",null))
        assertEquals(4,prefs.getInt("question_round",0))
    }

    @Test fun clearingRemovesOnlyConversationStateAndDoesNotCreateAnotherChatOrRepeatMigration(){
        val prefs=MemoryPrefs()
        val store=SingleChatStore(prefs)
        prefs.edit().putString("personal_prompt","保留我的要求").putString("direct_model","selected").putString("messages","conversation").putString("draft_attachments","private-file-reference").putString("editing_plan_id","plan-a").putString("command_result","previous action").apply()
        store.clear()
        assertFalse(prefs.contains("messages"));assertFalse(prefs.contains("draft_attachments"));assertFalse(prefs.contains("editing_plan_id"));assertFalse(prefs.contains("command_result"))
        assertEquals("保留我的要求",prefs.getString("personal_prompt",null));assertEquals("selected",prefs.getString("direct_model",null))
        assertFalse(prefs.contains("chat_archive"));assertFalse(prefs.contains("active_chat_id"))
        assertFalse(SingleChatStore(prefs).migrated)
    }

    private class MemoryPrefs:SharedPreferences{
        val data=mutableMapOf<String,Any>()
        override fun getAll():MutableMap<String,*> = data.toMutableMap()
        override fun contains(key:String?)=data.containsKey(key)
        override fun getString(key:String?,defValue:String?)=data[key] as? String?:defValue
        override fun getInt(key:String?,defValue:Int)=data[key] as? Int?:defValue
        override fun getBoolean(key:String?,defValue:Boolean)=data[key] as? Boolean?:defValue
        override fun getLong(key:String?,defValue:Long)=data[key] as? Long?:defValue
        override fun getFloat(key:String?,defValue:Float)=data[key] as? Float?:defValue
        @Suppress("UNCHECKED_CAST") override fun getStringSet(key:String?,defValues:MutableSet<String>?)=data[key] as? MutableSet<String>?:defValues
        override fun registerOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener?){}
        override fun unregisterOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener?){}
        override fun edit():SharedPreferences.Editor=object:SharedPreferences.Editor{
            val changes=mutableMapOf<String,Any?>();var clear=false
            override fun putString(k:String?,v:String?)=apply{if(k!=null)changes[k]=v}
            override fun putStringSet(k:String?,v:MutableSet<String>?)=apply{if(k!=null)changes[k]=v}
            override fun putInt(k:String?,v:Int)=apply{if(k!=null)changes[k]=v}
            override fun putLong(k:String?,v:Long)=apply{if(k!=null)changes[k]=v}
            override fun putFloat(k:String?,v:Float)=apply{if(k!=null)changes[k]=v}
            override fun putBoolean(k:String?,v:Boolean)=apply{if(k!=null)changes[k]=v}
            override fun remove(k:String?)=apply{if(k!=null)changes[k]=null}
            override fun clear()=apply{clear=true}
            override fun commit():Boolean{apply();return true}
            override fun apply(){if(clear)data.clear();changes.forEach{(k,v)->if(v==null)data.remove(k)else data[k]=v}}
        }
    }
}

package com.routine

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RoutineUiState(val steps:List<Step>,val theme:String,val dynamicColor:Boolean)

/** Saved work and settings survive screen changes; Compose only renders and sends events. */
class RoutineState(application:Application):AndroidViewModel(application){
    private val prefs=application.getSharedPreferences("routine",0)
    private fun read()=RoutineUiState(decode(prefs.getString("steps","[]")?:"[]"),prefs.getString("theme","跟随系统")?:"跟随系统",prefs.getBoolean("dynamic_color",true))
    private val mutableState=MutableStateFlow(read())
    val uiState=mutableState.asStateFlow()
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key in setOf("steps","theme","dynamic_color"))mutableState.value=read()}
    init{prefs.edit().remove("capacity").apply();prefs.registerOnSharedPreferenceChangeListener(listener)}
    fun saveSteps(next:List<Step>){mutableState.update{it.copy(steps=next)};prefs.edit().putString("steps",encode(next)).apply();TaskReminders.sync(getApplication())}
    fun setTheme(value:String){require(value in setOf("跟随系统","浅色","深色"));mutableState.update{it.copy(theme=value)};prefs.edit().putString("theme",value).apply()}
    fun setDynamicColor(value:Boolean){mutableState.update{it.copy(dynamicColor=value)};prefs.edit().putBoolean("dynamic_color",value).apply()}
    override fun onCleared(){prefs.unregisterOnSharedPreferenceChangeListener(listener)}
}

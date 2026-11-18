@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.routine

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Synthetic screens and separate preference/files namespaces: never reads real plans or credentials. */
@RunWith(AndroidJUnit4::class)
class AndroidxAuditTest {
    @get:Rule val ui=createComposeRule()
    private val owner=object:ViewModelStoreOwner{override val viewModelStore=ViewModelStore()}
    private val testApp=IsolatedApplication(InstrumentationRegistry.getInstrumentation().targetContext)
    private fun planner():PlannerState{
        testApp.getSharedPreferences("routine_ai",0).edit().putBoolean("single_chat_v1",true).apply()
        return ViewModelProvider(owner,object:ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST")
            override fun <T:ViewModel> create(modelClass:Class<T>)=PlannerState(testApp) as T
        })[PlannerState::class.java]
    }
    @After fun clean(){ui.runOnIdle{owner.viewModelStore.clear()};testApp.clean()}
    private fun capture(name:String){
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(base.externalCacheDir,"codex-md3e-audit-20261105").apply{mkdirs()}
        ui.onRoot().captureToImage().asAndroidBitmap().let{bitmap->File(directory,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }

    @Test fun navigationRestoresChatDraftAndReturnsToTheActualCaller(){
        lateinit var controller:RoutineNavigator
        val restoration=StateRestorationTester(ui)
        restoration.setContent{
            MaterialExpressiveTheme{
                controller=rememberRoutineNavigator()
                RoutineNavigation(controller){screen,_->
                    if(screen=="chat"){
                        var draft by rememberSaveable{mutableStateOf("")}
                        TextField(draft,{draft=it},Modifier.testTag("audit-nav-draft"))
                    }else Text("audit-screen:$screen")
                }
            }
        }
        ui.runOnIdle{controller.openTopLevel("chat")}
        ui.onNodeWithTag("audit-nav-draft").performTextInput("保留草稿")
        ui.runOnIdle{controller.openTopLevel("board")}
        ui.onNodeWithText("audit-screen:board").assertIsDisplayed()
        ui.runOnIdle{controller.openTopLevel("chat")}
        ui.onNodeWithTag("audit-nav-draft").assertTextContains("保留草稿")
        ui.runOnIdle{controller.navigate("settings")}
        ui.onNodeWithText("audit-screen:settings").assertIsDisplayed()
        ui.runOnIdle{controller.navigate("about")}
        ui.onNodeWithText("audit-screen:about").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("audit-screen:about").assertIsDisplayed()
        ui.runOnIdle{assertTrue(controller.popBackStack())}
        ui.onNodeWithText("audit-screen:settings").assertIsDisplayed()
        ui.runOnIdle{assertTrue(controller.popBackStack())}
        ui.onNodeWithTag("audit-nav-draft").assertTextContains("保留草稿")
    }

    @Test fun aThousandTodayTasksAreLazyAndReachableOnASmallLargeFontScreen(){
        val today=LocalDate.now()
        val tasks=(1..1000).map{Step("audit-$it","测试计划","审查任务 $it",today.toString(),today.toString(),20,planId="audit")}
        ui.setContent{MaterialExpressiveTheme{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)){
                Box(Modifier.requiredSize(320.dp,640.dp)){Board(tasks,today,{},{},{},{},{})}
            }
        }}
        ui.onNodeWithText("审查任务 1000").assertDoesNotExist()
        ui.onNodeWithText("预计 20000 分钟").assertIsDisplayed()
        capture("board-large-font")
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("审查任务 1000"))
        ui.onNodeWithText("审查任务 1000").assertIsDisplayed()
    }

    @Test fun allPlansAreLazyAndDeletionRequiresTheTargetConfirmation(){
        val today=LocalDate.now().toString()
        val plans=(1..300).map{Step("p-$it","审查计划 ${it.toString().padStart(3,'0')}","测试行动",today,today,20,planId="p-$it")}
        var deleted:String?=null
        ui.setContent{MaterialExpressiveTheme{Board(plans,LocalDate.parse(today),{},{},{},{deleted=it},{})}}
        ui.onNodeWithText("所有计划").performClick()
        capture("all-plans")
        ui.onNodeWithText("审查计划 300").assertDoesNotExist()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("审查计划 300"))
        ui.onNodeWithText("审查计划 300").assertIsDisplayed()
        ui.onAllNodesWithText("删除").onLast().performClick()
        ui.onNodeWithText("删除计划？").assertIsDisplayed()
        ui.runOnIdle{assertNull(deleted)}
        ui.onAllNodesWithText("删除").onLast().performClick()
        ui.waitForIdle()
        ui.runOnIdle{assertEquals("p-300",deleted)}
    }

    @Test fun longChatWithIdenticalTimestampsUsesStableIdsAndStateFlowInput(){
        val messages=(1..1000).map{ConversationMessage("user","审查消息 $it","2026-11-05T10:00:00+08:00")}
        testApp.getSharedPreferences("routine_ai",0).edit().putString("messages",JSONArray().apply{messages.forEach{put(it.json())}}.toString()).apply()
        val state=planner()
        ui.setContent{MaterialExpressiveTheme{AiChatContent(state,emptyList()) {}}}
        ui.onNodeWithText("审查消息 1000").assertIsDisplayed()
        ui.onNodeWithText("审查消息 1").assertDoesNotExist()
        capture("chat")
        ui.onNode(hasSetTextAction()).performTextInput("测试草稿")
        ui.runOnIdle{assertEquals("测试草稿",state.uiState.value.input);assertFalse(state.uiState.value.hasKey)}
        ui.runOnIdle{state.draftInput("外部状态更新")}
        ui.onNode(hasSetTextAction()).assertTextContains("外部状态更新")
    }

    @Test fun personalPreferencesSaveAndClearThroughTheLifecycleCollectedState(){
        val state=planner()
        ui.setContent{MaterialExpressiveTheme{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){AiSettings(state)}}}
        capture("ai-settings")
        ui.onNodeWithText("添加个性化提示词").performScrollTo().performClick()
        ui.onNodeWithText("我的偏好（可留空）").performScrollTo().performTextInput("练习后复盘")
        ui.onNodeWithText("保存",useUnmergedTree=true).performScrollTo().performClick()
        ui.runOnIdle{assertEquals("练习后复盘",state.uiState.value.customPrompt)}
        ui.onNodeWithText("我的偏好（可留空）").assertTextContains("练习后复盘")
        ui.onNodeWithText("清空并保存").performScrollTo().performClick()
        ui.runOnIdle{assertEquals("",state.uiState.value.customPrompt)}
    }

    @Test fun aboutPageDisplaysTheActualReadonlyContract(){
        ui.setContent{MaterialExpressiveTheme{AboutPage()}}
        capture("about")
        ui.onNodeWithText("查看内置提示词").performScrollTo().performClick()
        ui.onNodeWithText("只返回符合下列schema",substring=true).performScrollTo().assertIsDisplayed()
        ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}

private class IsolatedApplication(private val base:Context):Application(){
    private val token="md3e_audit_${UUID.randomUUID()}_"
    private val directory=File(base.cacheDir,token).apply{mkdirs()}
    private val names=mutableSetOf<String>()
    init{attachBaseContext(base)}
    override fun getFilesDir():File=directory
    override fun getSharedPreferences(name:String,mode:Int):SharedPreferences{
        names+=name
        return base.getSharedPreferences(token+name,mode)
    }
    fun clean(){names.forEach{base.getSharedPreferences(token+it,0).edit().clear().commit()};directory.deleteRecursively()}
}

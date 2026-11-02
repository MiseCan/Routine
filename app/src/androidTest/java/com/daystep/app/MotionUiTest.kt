@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.daystep.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** These tests render synthetic content in a test activity; user plans and credentials are untouched. */
@RunWith(AndroidJUnit4::class)
class MotionUiTest{
    @get:Rule val ui=createComposeRule()

    @Test fun dialogCommitsOnlyAfterItsExitAnimation(){
        var shown by mutableStateOf(true)
        var commits=0
        ui.mainClock.autoAdvance=false
        ui.setContent{MaterialExpressiveTheme{if(shown)MotionDialog(onDismissRequest={shown=false},title={Text("测试弹窗")},text={Text("测试内容")},confirmLabel="测试确认",onConfirm={commits++;shown=false})}}
        ui.mainClock.advanceTimeBy(1500)
        ui.onNodeWithText("测试确认").performClick()
        ui.runOnIdle{assertEquals(0,commits)}
        ui.mainClock.advanceTimeBy(2000)
        ui.runOnIdle{assertEquals(1,commits)}
        ui.onNodeWithText("测试弹窗").assertDoesNotExist()
    }
    @Test fun removedRowsRemainUntilTheirExitFinishes(){
        var rows by mutableStateOf(listOf("第一项","第二项"))
        ui.mainClock.autoAdvance=false
        ui.setContent{MaterialExpressiveTheme{Column{MotionList(rows,{it}){Text(it)}}}}
        ui.mainClock.advanceTimeBy(1500)
        ui.runOnIdle{rows=listOf("第二项")}
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText("第一项").assertExists()
        ui.mainClock.advanceTimeBy(2000)
        ui.onNodeWithText("第一项").assertDoesNotExist()
        ui.onNodeWithText("第二项").assertIsDisplayed()
    }
    @Test fun interruptedPageTransitionsPreserveSavedState(){
        var page by mutableStateOf("board")
        ui.mainClock.autoAdvance=false
        ui.setContent{MaterialExpressiveTheme{val holder=rememberSaveableStateHolder();MotionPages(page){screen->holder.SaveableStateProvider(screen){var draft by rememberSaveable{mutableStateOf("草稿-$screen")};Text(draft)}}}}
        ui.mainClock.advanceTimeBy(1000)
        listOf("settings","board","settings","about","settings","board","chat","board").forEach{next->ui.runOnIdle{page=next};ui.mainClock.advanceTimeBy(48)}
        ui.mainClock.advanceTimeBy(2000)
        ui.onNodeWithText("草稿-board").assertIsDisplayed()
        ui.onNodeWithText("草稿-settings").assertDoesNotExist()
    }
    @Test fun completionDialogRequiresInputAndSavesSyntheticRecord(){
        var shown by mutableStateOf(true)
        var saved:Step?=null
        val fake=Step("motion-test","动画测试","测试任务","2026-10-29","2026-10-30",15)
        ui.setContent{MaterialExpressiveTheme{if(shown)CompletionDialog(fake,{shown=false}){saved=it;shown=false}}}
        ui.onNodeWithText("保存并完成").assertIsNotEnabled()
        ui.onNodeWithText("完成内容（必填）").performTextInput("仅用于验证弹窗的测试记录")
        ui.onNodeWithText("保存并完成").performClick()
        ui.waitForIdle()
        ui.runOnIdle{assertTrue(saved?.done==true);assertEquals("仅用于验证弹窗的测试记录",saved?.completions?.single()?.note)}
    }
}

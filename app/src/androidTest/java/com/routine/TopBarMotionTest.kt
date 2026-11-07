@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.routine

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Geometry checks use a synthetic header and never access user conversations. */
@RunWith(AndroidJUnit4::class)
class TopBarMotionTest {
    @get:Rule val ui=createComposeRule()
    private fun titleX()=ui.onNodeWithTag("topbar-title",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.left
    @Test fun titleMovesDuringEntryAndReturnWithoutChangingInsetsAtTheEnd(){
        var back by mutableStateOf(false)
        ui.mainClock.autoAdvance=false
        ui.setContent{MaterialExpressiveTheme{RoutineTopBar("AI 聊天",back,onBack={},action=TopBarAction.ClearChat,onAction={})}}
        ui.mainClock.advanceTimeBy(1500);val listX=titleX()
        ui.runOnIdle{back=true};ui.mainClock.advanceTimeByFrame();ui.mainClock.advanceTimeBy(48)
        val enteringX=titleX()
        ui.mainClock.advanceTimeBy(1500);val conversationX=titleX()
        assertTrue("Title should move through an intermediate position",enteringX>listX&&enteringX<conversationX)
        ui.mainClock.advanceTimeBy(160);assertEquals(conversationX,titleX(),.5f)
        ui.runOnIdle{back=false};ui.mainClock.advanceTimeByFrame();ui.mainClock.advanceTimeBy(48)
        val returningX=titleX();assertTrue("Return should move title rather than snap",returningX>listX&&returningX<conversationX)
        ui.mainClock.advanceTimeBy(1500);assertEquals(listX,titleX(),.5f)
        ui.mainClock.advanceTimeBy(160);assertEquals(listX,titleX(),.5f)
    }
    @Test fun actionCenterIsFixedThroughoutTheIconSwap(){
        var action by mutableStateOf(TopBarAction.Settings)
        ui.mainClock.autoAdvance=false
        ui.setContent{MaterialExpressiveTheme{RoutineTopBar("标题",false,onBack={},action=action,onAction={})}}
        ui.mainClock.advanceTimeBy(1500)
        val center=ui.onNodeWithContentDescription("设置").fetchSemanticsNode().boundsInRoot.center
        ui.runOnIdle{action=TopBarAction.ClearChat};ui.mainClock.advanceTimeByFrame()
        repeat(20){
            ui.mainClock.advanceTimeBy(32)
            val current=ui.onNodeWithContentDescription("清空聊天").fetchSemanticsNode().boundsInRoot.center
            assertEquals(center.x,current.x,.5f);assertEquals(center.y,current.y,.5f)
        }
        ui.mainClock.advanceTimeBy(1500)
        assertEquals(center.x,ui.onNodeWithContentDescription("清空聊天").fetchSemanticsNode().boundsInRoot.center.x,.5f)
    }
    @Test fun rapidlyReversingBackTransitionReturnsToTheOriginalPosition(){
        var back by mutableStateOf(false)
        ui.mainClock.autoAdvance=false
        ui.setContent{MaterialExpressiveTheme{RoutineTopBar("AI 聊天",back,onBack={},action=TopBarAction.ClearChat,onAction={})}}
        ui.mainClock.advanceTimeBy(1500);val origin=titleX()
        repeat(6){ui.runOnIdle{back=!back};ui.mainClock.advanceTimeBy(48)}
        ui.mainClock.advanceTimeBy(2000);assertEquals(origin,titleX(),.5f)
        ui.onNodeWithContentDescription("返回").assertDoesNotExist()
    }
}

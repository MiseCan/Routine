@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.routine

import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Runs the real page chrome and Miuix Nav locally, without a phone or private data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PageNavigationTest {
    @get:Rule val ui=createComposeRule()
    private lateinit var nav:RoutineNavigator
    private lateinit var back:OnBackPressedDispatcher
    private fun render(){
        ui.setContent{MaterialExpressiveTheme{
            nav=rememberRoutineNavigator()
            back=LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            RoutineNavigation(nav,Modifier.fillMaxSize()){page,_->
                RoutinePage(page=page,title="页面-$page",onBack={nav.navigateUp()},onAction={},onSelectPage={nav.openTopLevel(it)}){
                    var draft by rememberSaveable{mutableStateOf("")}
                    Column(Modifier.fillMaxSize()){
                        Text("内容-$page",Modifier.testTag("content-$page"))
                        if(page=="chat")TextField(draft,{draft=it},Modifier.testTag("chat-draft"))
                    }
                }
            }
        }}
        ui.waitForIdle()
    }
    private fun x(text:String)=try{ui.onNodeWithText(text,useUnmergedTree=true).fetchSemanticsNode().positionInRoot.x}catch(error:AssertionError){throw AssertionError("$text: ${ui.onRoot(useUnmergedTree=true).printToString()}",error)}
    private fun changeWhileClockPaused(action:()->Unit){
        ui.runOnIdle(action)
        // Miuix observes SnapshotStateList; deliver its queued snapshot notification before
        // advancing the separate Compose clock in Robolectric's PAUSED main looper.
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun peerTabsDoNotSlideOrShowBackAndEachRetainsItsDraft(){
        render()
        val titleAtRest=x("页面-board")
        val contentAtRest=x("内容-board")
        ui.mainClock.autoAdvance=false
        changeWhileClockPaused{nav.openTopLevel("chat")}
        ui.mainClock.advanceTimeByFrame()
        ui.mainClock.advanceTimeBy(48)
        assertEquals("Peer tabs must not use a nested-page horizontal offset",titleAtRest,x("页面-chat"),1f)
        assertEquals(contentAtRest,x("内容-chat"),1f)
        ui.onNodeWithContentDescription("返回").assertDoesNotExist()
        ui.mainClock.advanceTimeBy(1000)
        ui.mainClock.autoAdvance=true
        ui.onNodeWithTag("chat-draft").performTextInput("同级切换保留的草稿")
        ui.runOnIdle{nav.openTopLevel("board")}
        ui.waitForIdle()
        ui.onNodeWithContentDescription("返回").assertDoesNotExist()
        ui.runOnIdle{assertEquals(1,nav.backStack.size)}
        ui.runOnIdle{nav.openTopLevel("chat")}
        ui.onNodeWithTag("chat-draft").assertTextContains("同级切换保留的草稿")
        ui.onNodeWithContentDescription("返回").assertDoesNotExist()
    }

    @Test fun wholePageIncludingToolbarMovesTogetherAndReturnsToItsCaller(){
        render()
        ui.mainClock.autoAdvance=false
        changeWhileClockPaused{nav.navigate("settings")}
        ui.mainClock.advanceTimeByFrame()
        ui.mainClock.advanceTimeBy(80)
        val titleDuring=x("页面-settings")
        val contentDuring=x("内容-settings")
        ui.mainClock.advanceTimeBy(1000)
        val titleAtRest=x("页面-settings")
        val contentAtRest=x("内容-settings")
        assertTrue("The complete incoming page should slide",titleDuring-titleAtRest>10f)
        assertEquals("Toolbar and content must share one transform",titleDuring-titleAtRest,contentDuring-contentAtRest,1f)
        changeWhileClockPaused{nav.navigate("about")}
        ui.mainClock.advanceTimeBy(1000)
        changeWhileClockPaused{assertTrue(nav.popBackStack())}
        ui.mainClock.advanceTimeBy(1000)
        ui.onNodeWithText("页面-settings").assertIsDisplayed()
        ui.onNodeWithText("内容-settings").assertIsDisplayed()
        ui.onNodeWithText("页面-about").assertDoesNotExist()
    }

    @Test fun predictivePreviewKeepsBackStackUntilCommitAndCancelPreservesChatDraft(){
        render()
        ui.runOnIdle{nav.openTopLevel("chat")}
        ui.onNodeWithTag("chat-draft").performTextInput("返回取消后保留草稿")
        ui.runOnIdle{nav.navigate("settings")}
        ui.waitForIdle()
        val titleAtRest=x("页面-settings")
        for(edge in listOf(BackEventCompat.EDGE_LEFT,BackEventCompat.EDGE_RIGHT)){
            ui.runOnIdle{
                back.dispatchOnBackStarted(BackEventCompat(0f,400f,0f,edge))
                back.dispatchOnBackProgressed(BackEventCompat(200f,400f,.5f,edge))
            }
            ui.waitForIdle()
            ui.runOnIdle{assertEquals("settings",nav.currentScreen)}
            assertTrue("Preview must move before the gesture commits",x("页面-settings")-titleAtRest>100f)
            ui.runOnIdle{back.dispatchOnBackCancelled()}
            ui.waitForIdle()
            ui.runOnIdle{assertEquals("settings",nav.currentScreen)}
            assertEquals("Cancel restores the complete original page",titleAtRest,x("页面-settings"),1f)
        }
        ui.runOnIdle{
            back.dispatchOnBackStarted(BackEventCompat(0f,400f,0f,BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(300f,400f,.8f,BackEventCompat.EDGE_LEFT))
        }
        ui.waitForIdle()
        ui.runOnIdle{back.onBackPressed()}
        ui.waitForIdle()
        ui.runOnIdle{assertEquals("chat",nav.currentScreen)}
        ui.onNodeWithTag("chat-draft").assertTextContains("返回取消后保留草稿")
    }

    @Test fun rootDoesNotInterceptSystemBackToHome(){
        render()
        ui.runOnIdle{assertFalse("The system owns back-to-home at the root",back.hasEnabledCallbacks())}
        ui.runOnIdle{nav.navigate("settings")}
        ui.waitForIdle()
        ui.runOnIdle{assertTrue(back.hasEnabledCallbacks());back.onBackPressed()}
        ui.waitForIdle()
        ui.runOnIdle{assertEquals("board",nav.currentScreen);assertFalse(back.hasEnabledCallbacks())}
        ui.runOnIdle{nav.openTopLevel("chat")}
        ui.waitForIdle()
        ui.runOnIdle{assertEquals(1,nav.backStack.size);assertFalse(back.hasEnabledCallbacks())}
    }

    @Test fun miuixPointerSwipeCanCancelAndThenPopTheNestedPage(){
        render()
        ui.runOnIdle{nav.navigate("settings")}
        ui.waitForIdle()
        ui.onRoot().performTouchInput{
            down(Offset(2f,400f))
            moveTo(Offset(35f,400f),delayMillis=32)
            moveTo(Offset(90f,400f),delayMillis=160)
            moveTo(Offset(2f,400f),delayMillis=160)
            up()
        }
        ui.waitForIdle()
        ui.runOnIdle{assertEquals("settings",nav.currentScreen);assertEquals(2,nav.backStack.size)}
        ui.onRoot().performTouchInput{swipe(Offset(2f,400f),Offset(400f,400f),durationMillis=450)}
        ui.waitForIdle()
        ui.runOnIdle{assertEquals("board",nav.currentScreen);assertEquals(1,nav.backStack.size)}
        ui.onNodeWithText("页面-board").assertIsDisplayed()
    }

    @Test fun savedInstanceRestoresNestedCallerAndBothPeerDrafts(){
        val restoration=StateRestorationTester(ui)
        restoration.setContent{MaterialExpressiveTheme{
            nav=rememberRoutineNavigator()
            RoutineNavigation(nav,Modifier.fillMaxSize()){page,_->
                var draft by rememberSaveable{mutableStateOf("")}
                Column{Text("屏幕-$page");TextField(draft,{draft=it},Modifier.testTag("draft-$page"))}
            }
        }}
        ui.onNodeWithTag("draft-board").performTextInput("看板状态")
        ui.runOnIdle{nav.openTopLevel("chat")}
        ui.onNodeWithTag("draft-chat").performTextInput("聊天状态")
        ui.runOnIdle{nav.navigate("settings")}
        ui.onNodeWithTag("draft-settings").performTextInput("设置草稿")
        ui.runOnIdle{nav.navigate("about")}
        ui.onNodeWithText("屏幕-about").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("屏幕-about").assertIsDisplayed()
        ui.runOnIdle{nav.popBackStack()}
        ui.onNodeWithTag("draft-settings").assertTextContains("设置草稿")
        ui.runOnIdle{nav.popBackStack()}
        ui.onNodeWithTag("draft-chat").assertTextContains("聊天状态")
        ui.runOnIdle{nav.openTopLevel("board")}
        ui.onNodeWithTag("draft-board").assertTextContains("看板状态")
    }

    @Test fun encodedPlanIdsAndRapidRepeatedNavigationRemainDistinct(){
        render()
        val planId="计划/甲 ?#%"
        ui.runOnIdle{repeat(3){nav.openPlan(planId)}}
        ui.waitForIdle()
        ui.runOnIdle{
            assertEquals(2,nav.backStack.size)
            assertEquals(planId,(nav.backStack.last() as RoutineRoute).planId)
            assertTrue(nav.popBackStack())
        }
        ui.waitForIdle()
        ui.mainClock.autoAdvance=false
        listOf("settings","about","board","chat","board").forEach{page->
            changeWhileClockPaused{if(page in setOf("board","chat"))nav.openTopLevel(page)else nav.navigate(page)}
            ui.mainClock.advanceTimeBy(48)
        }
        ui.mainClock.advanceTimeBy(2000)
        ui.runOnIdle{assertEquals("board",nav.currentScreen);assertEquals(1,nav.backStack.size)}
        ui.onNodeWithText("页面-board").assertIsDisplayed()
    }
}

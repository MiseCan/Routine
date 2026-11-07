@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.routine

import android.app.Application
import android.graphics.Insets
import android.view.WindowInsets as AndroidInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.LocalDate

/** Local synthetic data only; no phone, API key or model call is involved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,35],qualifiers="w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LayoutRegressionTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val owner=object:ViewModelStoreOwner{override val viewModelStore=ViewModelStore()}
    @After fun clear(){ui.runOnIdle{owner.viewModelStore.clear()}}
    private fun tasks()=(1..60).map{Step("layout-$it","计划 $it","行动 $it","2026-11-06","2026-11-30",15,planId="layout-plan-$it")}
    private fun board(){
        ui.setContent{MaterialExpressiveTheme{RoutinePage("board","Routine",{},{},{}){
            Board(tasks(),LocalDate.parse("2026-11-06"),{},{},{},{})
        }}}
    }
    private fun assertFullHeightViewport(){
        val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
        val viewport=ui.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("Board controls must not reserve fixed space above the list",viewport.top-root.top<100f)
        assertTrue("The list should use most of the page",viewport.height>root.height*.7f)
    }

    @Test fun todayOverviewAndFiltersScrollAwayWithTasks(){
        board()
        assertFullHeightViewport()
        ui.onNodeWithText("今日进度  0 / 60").assertIsDisplayed()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("行动 40"))
        ui.onNodeWithText("行动 40").assertIsDisplayed()
        ui.onNodeWithText("今日进度  0 / 60").assertDoesNotExist()
        ui.onNodeWithText("今日任务").assertDoesNotExist()
        assertFullHeightViewport()
    }

    @Test fun allPlansUseOneScrollingListIncludingTheBoardOverview(){
        board()
        ui.onNodeWithText("所有计划").performClick()
        ui.waitForIdle()
        assertFullHeightViewport()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("计划 40"))
        ui.onNodeWithText("计划 40").assertIsDisplayed()
        ui.onNodeWithText("今日进度  0 / 60").assertDoesNotExist()
        assertFullHeightViewport()
    }

    @Test fun keyboardInsetsAreAppliedOnceAndBottomNavigationDoesNotLeaveAGap(){
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("routine_ai",0).edit().clear().putBoolean("single_chat_v1",true).commit()
        val planner=ViewModelProvider(owner,ViewModelProvider.AndroidViewModelFactory(app))[PlannerState::class.java]
        ui.runOnIdle{ui.activity.enableEdgeToEdge()}
        ui.setContent{MaterialExpressiveTheme{RoutinePage("chat","AI 聊天",{},{},{}){AiChatContent(planner,emptyList()) {}}}}
        fun insets(visible:Boolean){
            ui.runOnIdle{
                ui.activity.window.decorView.dispatchApplyWindowInsets(AndroidInsets.Builder()
                    .setInsets(AndroidInsets.Type.statusBars(),Insets.of(0,24,0,0))
                    .setInsets(AndroidInsets.Type.navigationBars(),Insets.of(0,0,0,24))
                    .setInsets(AndroidInsets.Type.ime(),Insets.of(0,0,0,if(visible)300 else 0))
                    .setVisible(AndroidInsets.Type.ime(),visible).build())
            }
            ui.waitForIdle()
        }
        insets(false)
        ui.onNodeWithText("看板").assertIsDisplayed()
        insets(true)
        ui.onNodeWithText("看板").assertDoesNotExist()
        val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
        val input=ui.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertEquals("Only the keyboard and 12px composer padding belong below the input",312f,root.bottom-input.bottom,2f)
        insets(false)
        ui.onNodeWithText("看板").assertIsDisplayed()
    }
}

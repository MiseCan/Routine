@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.daystep.app

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** A synthetic year-long plan, with no access to real task storage or model credentials. */
@RunWith(AndroidJUnit4::class)
class LongPlanUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun aFullYearOfDailyTasksRemainsAccessibleWithoutRenderingEveryDayAtOnce(){
        val start=LocalDate.parse("2026-10-29")
        val steps=(0L..364L).map{i->Step("long-test-$i","长期计划测试","第${i+1}天的具体行动",start.plusDays(i).toString(),"2027-10-28",20,definitionOfDone="保存当天学习笔记与自测结果",planId="long-test")}
        ui.setContent{MaterialExpressiveTheme{PlanDetail(steps,{},{},{_,_->})}}
        ui.onNodeWithText("2026-10-29 · 1 项").assertIsDisplayed()
        ui.onNodeWithText("第365天的具体行动").assertDoesNotExist()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("第365天的具体行动"))
        ui.onNodeWithText("第365天的具体行动").assertIsDisplayed()
        ui.onNodeWithText("0 / 365 项完成").assertIsDisplayed()
    }
}

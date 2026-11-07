package com.routine

import org.junit.Assert.*
import org.junit.Test

class PlanNamesTest {
    @Test fun shortNamesAreCompletePhrasesRatherThanTruncatedTitles(){
        assertEquals("数学复习",PlanNames.fallback("为准备研究生考试而进行的数学复习计划"))
        assertEquals("论文写作",PlanNames.fallback("毕业论文完整写作与修订安排"))
        assertEquals("日语学习",PlanNames.fallback("日语学习"))
        assertEquals("编辑计划",PlanNames.fallback("一个非常长且未知主题的工作安排"))
    }
    @Test fun namesMustBeAtMostSevenCharactersWithoutEllipsis(){
        assertTrue(PlanNames.valid("数学强化复习"));assertFalse(PlanNames.valid("研究生数学复习计划"));assertFalse(PlanNames.valid("数学复习…"))
        assertFalse(PlanNames.valid(""));assertTrue(PlanNames.valid("📚阅读"))
    }
    @Test fun fullAndShortPlanNamesSurviveSavingTogether(){
        val original=Step("s","完整的数学复习计划名称","做题","2026-11-01","2026-12-01",30,planId="p",shortProject="数学复习")
        assertEquals(original,decode(encode(listOf(original))).single())
    }
}

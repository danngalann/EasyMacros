package com.danngalann.easymacros.ui

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTotalsTest {
    @Test
    fun `over-target progress is full and reports the excess`() {
        val progress = targetProgress(BigDecimal("22.5"), BigDecimal("20"))

        assertTrue(progress.isOver)
        assertEquals(1f, progress.fraction)
        assertEquals(BigDecimal("2.5"), progress.difference)
    }

    @Test
    fun `under-target progress reports the remaining amount`() {
        val progress = targetProgress(BigDecimal("37"), BigDecimal("50"))

        assertFalse(progress.isOver)
        assertEquals(0.74f, progress.fraction)
        assertEquals(BigDecimal("13"), progress.difference)
    }
}

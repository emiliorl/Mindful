package com.mindshield.app.viewmodel

import com.mindshield.app.data.AppFrictionConfig
import com.mindshield.app.data.BatchCategory
import com.mindshield.app.data.BatchRule
import com.mindshield.app.data.FrictionMode
import com.mindshield.app.data.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldLogicTest {

    private fun cfg(mode: FrictionMode, vararg intents: IntentType) =
        AppFrictionConfig(mode, intents.toSet())

    private fun rule(cat: BatchCategory) = BatchRule("pkg", "App", cat)

    // ── frictionAfterToggle ──────────────────────────────────────────────────

    @Test fun toggleOnWithoutIntentsIsAlways() {
        assertEquals(FrictionMode.ALWAYS, frictionAfterToggle(cfg(FrictionMode.OFF), true))
    }

    @Test fun toggleOnWithIntentsRestoresSmart() {
        assertEquals(FrictionMode.SMART, frictionAfterToggle(cfg(FrictionMode.OFF, IntentType.WORK), true))
    }

    @Test fun toggleOffIsOff() {
        assertEquals(FrictionMode.OFF, frictionAfterToggle(cfg(FrictionMode.ALWAYS), false))
        assertEquals(FrictionMode.OFF, frictionAfterToggle(cfg(FrictionMode.SMART, IntentType.STUDY), false))
    }

    @Test fun smartRoundTripRestores() {
        var c = cfg(FrictionMode.SMART, IntentType.WORK, IntentType.STUDY)
        c = c.copy(mode = frictionAfterToggle(c, false))
        assertEquals(FrictionMode.OFF, c.mode)
        c = c.copy(mode = frictionAfterToggle(c, true))
        assertEquals(FrictionMode.SMART, c.mode)
    }

    @Test fun alwaysRoundTripRestores() {
        var c = cfg(FrictionMode.ALWAYS)
        c = c.copy(mode = frictionAfterToggle(c, false))
        c = c.copy(mode = frictionAfterToggle(c, true))
        assertEquals(FrictionMode.ALWAYS, c.mode)
    }

    // ── notifyModeOf ─────────────────────────────────────────────────────────

    @Test fun notifyModeMapping() {
        assertEquals(NotifyMode.DEFAULT, notifyModeOf(null))
        assertEquals(NotifyMode.INSTANT, notifyModeOf(rule(BatchCategory.INSTANT)))
        assertEquals(NotifyMode.BATCHED, notifyModeOf(rule(BatchCategory.BATCHED)))
    }

    // ── isProtected ──────────────────────────────────────────────────────────

    @Test fun isProtectedTruthTable() {
        val off = cfg(FrictionMode.OFF)
        assertFalse(isProtected(off, null))
        assertFalse(isProtected(off, rule(BatchCategory.INSTANT)))
        assertTrue(isProtected(off, rule(BatchCategory.BATCHED)))
        assertTrue(isProtected(cfg(FrictionMode.ALWAYS), null))
        assertTrue(isProtected(cfg(FrictionMode.SMART), null))
        assertTrue(isProtected(cfg(FrictionMode.ALWAYS), rule(BatchCategory.INSTANT)))
        assertTrue(isProtected(cfg(FrictionMode.SMART), rule(BatchCategory.BATCHED)))
    }

    @Test fun smartIntentsAloneWhileOffNotProtected() {
        assertFalse(isProtected(cfg(FrictionMode.OFF, IntentType.WORK), null))
    }

    // ── matchesFilter ────────────────────────────────────────────────────────

    @Test fun matchesFilterCases() {
        val off = cfg(FrictionMode.OFF)
        val on = cfg(FrictionMode.ALWAYS)
        assertTrue(matchesFilter(ShieldFilter.ALL, false, off, null))
        assertFalse(matchesFilter(ShieldFilter.PROTECTED, true, off, null))
        assertTrue(matchesFilter(ShieldFilter.PROTECTED, false, on, null))
        assertTrue(matchesFilter(ShieldFilter.PROTECTED, false, off, rule(BatchCategory.BATCHED)))
        assertFalse(matchesFilter(ShieldFilter.PROTECTED, false, off, rule(BatchCategory.INSTANT)))
        assertTrue(matchesFilter(ShieldFilter.SUGGESTED, true, off, null))
        assertTrue(matchesFilter(ShieldFilter.SUGGESTED, true, on, null))
        assertFalse(matchesFilter(ShieldFilter.SUGGESTED, false, on, null))
    }

    // ── matchesQuery ─────────────────────────────────────────────────────────

    @Test fun matchesQueryCases() {
        assertTrue(matchesQuery("Instagram", ""))
        assertTrue(matchesQuery("Instagram", "   "))
        assertTrue(matchesQuery("Instagram", "insta"))
        assertTrue(matchesQuery("Instagram", "GRAM"))
        assertFalse(matchesQuery("Instagram", "tiktok"))
    }

    // ── status text ──────────────────────────────────────────────────────────

    @Test fun statusTextCases() {
        val off = cfg(FrictionMode.OFF)
        assertEquals("", statusText(off, null, 6))
        assertEquals("6s pause", statusText(cfg(FrictionMode.ALWAYS), null, 6))
        assertEquals("10s pause", statusText(cfg(FrictionMode.ALWAYS), null, 10))
        assertEquals("Smart pause", statusText(cfg(FrictionMode.SMART, IntentType.WORK), null, 6))
        assertEquals("Batched", statusText(off, rule(BatchCategory.BATCHED), 6))
        assertEquals("Always instant", statusText(off, rule(BatchCategory.INSTANT), 6))
        assertEquals("6s pause · Batched", statusText(cfg(FrictionMode.ALWAYS), rule(BatchCategory.BATCHED), 6))
        assertEquals("Smart pause · Always instant", statusText(cfg(FrictionMode.SMART), rule(BatchCategory.INSTANT), 6))
    }

    @Test fun statusPartsNullsWhenUnconfigured() {
        val p = statusParts(cfg(FrictionMode.OFF), null, 6)
        assertNull(p.friction)
        assertNull(p.notify)
    }

    // ── isSuggestedCategory ──────────────────────────────────────────────────

    @Test fun suggestedCategories() {
        listOf(0, 2, 4, 5).forEach { assertTrue("category $it", isSuggestedCategory(it)) }
        listOf(-1, 1, 3, 6, 7, 99).forEach { assertFalse("category $it", isSuggestedCategory(it)) }
    }
}

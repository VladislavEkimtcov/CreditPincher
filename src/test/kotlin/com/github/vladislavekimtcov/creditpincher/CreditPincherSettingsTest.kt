package com.github.vladislavekimtcov.creditpincher

import com.github.vladislavekimtcov.creditpincher.services.CreditPincherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CreditPincherSettingsTest {

    @Test
    fun defaultsToAutoSyncDisabledWithAnHourInterval() {
        val settings = CreditPincherSettings()

        assertFalse(settings.autoSyncEnabled)
        assertEquals(CreditPincherSettings.DEFAULT_INTERVAL_MINUTES, settings.autoSyncIntervalMinutes)
    }

    @Test
    fun roundTripsEnabledFlagAndInterval() {
        val settings = CreditPincherSettings()

        settings.loadState(CreditPincherSettings.State(autoSyncEnabled = true, autoSyncIntervalMinutes = 15))

        assertEquals(true, settings.getState().autoSyncEnabled)
        assertEquals(15, settings.getState().autoSyncIntervalMinutes)
    }

    @Test
    fun clampsAnOutOfRangeIntervalLoadedFromDisk() {
        val settings = CreditPincherSettings()

        settings.loadState(CreditPincherSettings.State(autoSyncIntervalMinutes = 0))
        assertEquals(CreditPincherSettings.MIN_INTERVAL_MINUTES, settings.autoSyncIntervalMinutes)

        settings.loadState(CreditPincherSettings.State(autoSyncIntervalMinutes = 999_999))
        assertEquals(CreditPincherSettings.MAX_INTERVAL_MINUTES, settings.autoSyncIntervalMinutes)
    }

    @Test
    fun clampsAnOutOfRangeIntervalSetProgrammatically() {
        val settings = CreditPincherSettings()

        settings.autoSyncIntervalMinutes = -5
        assertEquals(CreditPincherSettings.MIN_INTERVAL_MINUTES, settings.autoSyncIntervalMinutes)

        settings.autoSyncIntervalMinutes = 100_000
        assertEquals(CreditPincherSettings.MAX_INTERVAL_MINUTES, settings.autoSyncIntervalMinutes)
    }
}

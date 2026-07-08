package com.github.vladislavekimtcov.creditpincher.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CreditUsageEntry(
    val timestamp: Instant,
    val amount: Double,
) {
    fun localDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        timestamp.atZone(zoneId).toLocalDate()
}


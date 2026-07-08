package com.github.vladislavekimtcov.creditpincher.services

import com.github.vladislavekimtcov.creditpincher.model.CreditUsageEntry
import com.github.vladislavekimtcov.creditpincher.model.UsageStats
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import java.nio.file.Path
import java.time.LocalDate

@Service(Service.Level.PROJECT)
class MyProjectService {
    private val store: CreditUsageStore
        get() = ApplicationManager.getApplication().getService(CreditUsageStore::class.java)

    fun getEntries(): List<CreditUsageEntry> = store.getEntries()

    fun addUsage(amount: Double): CreditUsageEntry = store.addUsage(amount)

    fun getMonthlyBudget(): Double? = store.getMonthlyBudget()

    fun setMonthlyBudget(amount: Double?) = store.setMonthlyBudget(amount)

    fun getStorageDirectory(): Path = store.storageDirectory()

    fun calculateStats(startDate: LocalDate, endDate: LocalDate): UsageStats =
        CreditStatsCalculator.calculate(
            entries = getEntries(),
            startDate = startDate,
            endDate = endDate,
            monthlyBudget = getMonthlyBudget(),
        )
}

package com.github.vladislavekimtcov.creditpincher.services

import com.github.vladislavekimtcov.creditpincher.model.CreditUsageEntry
import com.intellij.openapi.components.Service
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.Clock
import java.time.Instant

@Service(Service.Level.APP)
class CreditUsageStore {
    private val storage = CreditUsageStorage(defaultStorageDirectory())

    fun initialize() = storage.initialize()

    fun storageDirectory(): Path = storage.storageDirectory

    fun getEntries(): List<CreditUsageEntry> = storage.loadEntries()

    fun addUsage(amount: Double): CreditUsageEntry = storage.addUsage(amount)

    fun getMonthlyBudget(): Double? = storage.loadMonthlyBudget()

    fun setMonthlyBudget(amount: Double?) = storage.saveMonthlyBudget(amount)

    companion object {
        internal fun defaultStorageDirectory(): Path =
            Path.of(System.getProperty("user.home"), ".creditpincher")
    }
}

class CreditUsageStorage(
    val storageDirectory: Path,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val usageLogPath = storageDirectory.resolve("usage-log.csv")
    private val budgetPath = storageDirectory.resolve("monthly-budget.txt")
    private val lock = Any()

    init {
        initialize()
    }

    fun initialize() {
        synchronized(lock) {
            Files.createDirectories(storageDirectory)
            ensureFileExists(usageLogPath, "timestamp,amount\n")
            ensureFileExists(budgetPath, "")
        }
    }

    fun loadEntries(): List<CreditUsageEntry> = synchronized(lock) {
        Files.readAllLines(usageLogPath, UTF_8)
            .drop(1)
            .mapNotNull(::parseEntry)
    }

    fun addUsage(amount: Double): CreditUsageEntry {
        validatePositiveFiniteAmount(amount)
        val entry = CreditUsageEntry(timestamp = clock.instant(), amount = amount)

        synchronized(lock) {
            Files.writeString(
                usageLogPath,
                "${entry.timestamp},${entry.amount}\n",
                UTF_8,
                CREATE,
                WRITE,
                APPEND,
            )
        }

        return entry
    }

    fun loadMonthlyBudget(): Double? = synchronized(lock) {
        Files.readString(budgetPath, UTF_8)
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.toDoubleOrNull()
    }

    fun saveMonthlyBudget(amount: Double?) {
        amount?.let(::validatePositiveFiniteAmount)

        synchronized(lock) {
            Files.writeString(
                budgetPath,
                amount?.toString().orEmpty(),
                UTF_8,
                CREATE,
                WRITE,
                TRUNCATE_EXISTING,
            )
        }
    }

    private fun ensureFileExists(path: Path, initialContents: String) {
        if (!Files.exists(path)) {
            Files.writeString(path, initialContents, UTF_8, CREATE, WRITE)
        }
    }

    private fun parseEntry(line: String): CreditUsageEntry? {
        val parts = line.split(',', limit = 2)
        if (parts.size != 2) {
            return null
        }

        val timestamp = runCatching { Instant.parse(parts[0].trim()) }.getOrNull() ?: return null
        val amount = parts[1].trim().toDoubleOrNull() ?: return null

        return CreditUsageEntry(timestamp = timestamp, amount = amount)
    }

    private fun validatePositiveFiniteAmount(amount: Double) {
        require(amount.isFinite() && amount > 0.0) {
            "Amount must be a positive finite number."
        }
    }
}

package com.github.vladislavekimtcov.creditpincher.services

import com.github.vladislavekimtcov.creditpincher.MyBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import java.time.Instant
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Schedules periodic, unattended git sync of the CreditPincher storage
 * directory using the platform's shared application scheduled executor - no
 * dedicated thread is created, so this adds negligible overhead beyond the
 * handful of short-lived `git` subprocesses each sync spawns.
 *
 * App-level because the storage directory itself is app-level
 * ([CreditUsageStore.defaultStorageDirectory]): a single scheduler serves
 * every open project.
 */
@Service(Service.Level.APP)
class GitAutoSyncService : Disposable {
    private val lock = Any()
    private var scheduled: ScheduledFuture<*>? = null

    /** Guards against a scheduled tick and a manual push racing on the same directory. */
    private val syncInProgress = AtomicBoolean(false)

    @Volatile
    var lastSyncTime: Instant? = null
        private set

    @Volatile
    var lastSyncSummary: String? = null
        private set

    private var lastSyncFailed = false

    /** Idempotent - safe to call from every project's startup activity. */
    fun ensureScheduled() {
        synchronized(lock) {
            if (scheduled == null) {
                reschedule()
            }
        }
    }

    /** Cancels any existing schedule and, if enabled, starts a new one at the current interval. */
    fun reschedule() {
        synchronized(lock) {
            scheduled?.cancel(false)
            scheduled = null

            val settings = service<CreditPincherSettings>()
            if (!settings.autoSyncEnabled) {
                return
            }

            val intervalMinutes = settings.autoSyncIntervalMinutes.toLong()
            // Fixed delay, not fixed rate: after a laptop sleeps, fixed-rate would
            // fire a burst of catch-up runs once the machine wakes.
            scheduled = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                ::runScheduledSync,
                intervalMinutes,
                intervalMinutes,
                TimeUnit.MINUTES,
            )
        }
    }

    /**
     * Attempts to acquire the sync lock for a caller-driven sync (the tool
     * window's "Commit & push" button), so it never runs git concurrently
     * with a scheduled tick against the same directory. Returns `false`
     * without invoking [action] if a sync is already in progress.
     */
    fun <T> tryRunExclusively(action: () -> T): T? {
        if (!syncInProgress.compareAndSet(false, true)) {
            return null
        }
        return try {
            action()
        } finally {
            syncInProgress.set(false)
        }
    }

    private fun runScheduledSync() {
        if (!syncInProgress.compareAndSet(false, true)) {
            return
        }
        try {
            val store = ApplicationManager.getApplication().getService(CreditUsageStore::class.java)
            val gitService = GitBackupService(store.storageDirectory(), NoOpGitConflictResolver())

            val state = gitService.inspect()
            if (!state.gitAvailable || !state.isRepository || !state.hasRemote) {
                return
            }
            if (!state.hasUncommittedChanges && state.ahead == 0 && state.behind == 0 && state.hasUpstream) {
                lastSyncTime = Instant.now()
                lastSyncSummary = MyBundle["status.autoSyncUpToDate"]
                lastSyncFailed = false
                return
            }

            val result = gitService.sync()
            lastSyncTime = Instant.now()

            if (result.success) {
                lastSyncSummary = MyBundle["status.autoSyncSuccess"]
                lastSyncFailed = false
            } else {
                lastSyncSummary = MyBundle["status.autoSyncFailure"]
                notifyFailureOnce(result)
            }
        } catch (e: Exception) {
            thisLogger().warn("Scheduled CreditPincher git sync failed", e)
        } finally {
            syncInProgress.set(false)
        }
    }

    /**
     * Only balloons on a success-to-failure transition, so a broken remote
     * doesn't notify every interval. A lock collision with another IDE
     * instance syncing the same directory concurrently is expected and
     * transient - it is logged, not surfaced, and does not count as a
     * failure for the purposes of that transition.
     */
    private fun notifyFailureOnce(result: GitBackupService.GitResult) {
        if (result.output.contains("index.lock", ignoreCase = true)) {
            thisLogger().warn("CreditPincher git sync skipped: another process holds the git index lock")
            return
        }
        if (lastSyncFailed) {
            return
        }
        lastSyncFailed = true

        val messageKey = if (result.conflict) "notification.autoSyncConflict" else "notification.autoSyncFailed"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CreditPincher")
            .createNotification(
                MyBundle["notification.autoSyncFailedTitle"],
                MyBundle[messageKey, result.output],
                NotificationType.WARNING,
            )
            .notify(null)
    }

    override fun dispose() {
        synchronized(lock) {
            scheduled?.cancel(false)
            scheduled = null
        }
    }
}

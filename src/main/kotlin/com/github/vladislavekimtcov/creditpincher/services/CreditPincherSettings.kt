package com.github.vladislavekimtcov.creditpincher.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persisted, app-wide plugin preferences. Lives in the IDE's own config
 * storage rather than the `~/.creditpincher` storage directory, since that
 * directory is itself synced across machines and a per-machine preference
 * file there would just become another recurring merge conflict.
 */
@Service(Service.Level.APP)
@State(name = "CreditPincherSettings", storages = [Storage("creditpincher.xml")])
class CreditPincherSettings : PersistentStateComponent<CreditPincherSettings.State> {

    data class State(
        var autoSyncEnabled: Boolean = false,
        var autoSyncIntervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state.copy(
            autoSyncIntervalMinutes = state.autoSyncIntervalMinutes.coerceIn(
                MIN_INTERVAL_MINUTES,
                MAX_INTERVAL_MINUTES,
            ),
        )
    }

    var autoSyncEnabled: Boolean
        get() = state.autoSyncEnabled
        set(value) {
            state.autoSyncEnabled = value
        }

    var autoSyncIntervalMinutes: Int
        get() = state.autoSyncIntervalMinutes
        set(value) {
            state.autoSyncIntervalMinutes = value.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        }

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 60
        const val MIN_INTERVAL_MINUTES = 5
        const val MAX_INTERVAL_MINUTES = 1440
    }
}

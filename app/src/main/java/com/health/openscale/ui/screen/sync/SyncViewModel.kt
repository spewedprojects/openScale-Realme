package com.health.openscale.ui.screen.sync

import androidx.lifecycle.ViewModel
import com.health.openscale.core.database.DatabaseRepository
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class SyncViewModel @Inject constructor(
    val databaseRepository: DatabaseRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("openScale_preferences", Context.MODE_PRIVATE)

    private val _isSyncEnabled = MutableStateFlow(sharedPreferences.getBoolean("syncEnabledHealthConnect", false))
    val isSyncEnabled: StateFlow<Boolean> = _isSyncEnabled.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(sharedPreferences.getLong("lastSyncHealthConnect", 0L))
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    fun setSyncEnabled(enabled: Boolean) {
        _isSyncEnabled.value = enabled
        sharedPreferences.edit().putBoolean("syncEnabledHealthConnect", enabled).apply()
    }

    fun setLastSyncTimestamp(timestamp: Long) {
        _lastSyncTimestamp.value = timestamp
        sharedPreferences.edit().putLong("lastSyncHealthConnect", timestamp).apply()
    }
}

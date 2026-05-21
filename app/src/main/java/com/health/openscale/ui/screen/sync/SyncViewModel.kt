package com.health.openscale.ui.screen.sync

import androidx.lifecycle.ViewModel
import com.health.openscale.core.database.DatabaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    val databaseRepository: DatabaseRepository
) : ViewModel()

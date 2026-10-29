package com.kitchen.clock.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.kitchen.clock.data.ActivityAnchor
import com.kitchen.clock.data.FoldStateRepositoryImpl
import com.kitchen.clock.data.RearDisplayGateway
import com.kitchen.clock.data.SystemClockRepository
import com.kitchen.clock.domain.UpdateNightLight

// Ручной граф: одна фабрика ViewModel на хранилище Activity.
class FoldClockGraph(
    activity: ComponentActivity,
) {
    val viewModel: FoldClockViewModel by activity.viewModels {
        factory(activity.applicationContext)
    }

    private companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val anchor = ActivityAnchor()
                val updateNightLight = UpdateNightLight()
                @Suppress("UNCHECKED_CAST")
                return FoldClockViewModel(
                    savedStateHandle = extras.createSavedStateHandle(),
                    anchor = anchor,
                    foldStateRepository = FoldStateRepositoryImpl(
                        context = appContext,
                        anchor = anchor,
                        updateNightLight = updateNightLight,
                    ),
                    systemClockRepository = SystemClockRepository(appContext),
                    rearDisplayGateway = RearDisplayGateway(anchor),
                ) as T
            }
        }
    }
}

package com.kitchen.clock.data

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Текущая Activity для сессии; старый экземпляр не затирает новый после пересоздания.
class ActivityAnchor {
    private val current = MutableStateFlow<Activity?>(null)

    val activity: StateFlow<Activity?> = current

    fun bind(activity: Activity) {
        current.value = activity
    }

    fun unbind(activity: Activity) {
        if (current.value === activity) {
            current.value = null
        }
    }

    fun isBound(activity: Activity): Boolean = current.value === activity
}

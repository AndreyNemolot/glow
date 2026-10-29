package com.kitchen.clock.data

import android.app.Activity
import com.kitchen.clock.domain.FoldState
import kotlinx.coroutines.flow.Flow

// Наблюдение угла с гистерезисом, посеянным из сохранённого состояния.
interface FoldStateRepository {
    fun observe(initialOpened: Boolean): Flow<FoldState>

    fun observeOnInnerDisplay(): Flow<Boolean>

    // Снимок складки без ожидания Flow; null, если расширение окна старше 9.
    fun currentOnInnerDisplay(activity: Activity): Boolean?
}

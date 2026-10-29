package com.kitchen.clock.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge

// Минутный тик и системные broadcast обновляют LocalDateTime до следующей минуты.
class SystemClockRepository(
    context: Context,
) {
    private val appContext: Context = context.applicationContext

    fun now(): Flow<LocalDateTime> = merge(
        minuteTicker(),
        clockChanges().catch { error ->
            Log.e(TAG, "clock broadcast failed", error)
        },
    ).flowOn(Dispatchers.Default)

    private fun minuteTicker(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            val now = Instant.now()
            val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
            val waitMillis = java.time.Duration.between(now, nextMinute).toMillis().coerceAtLeast(0L)
            delay(waitMillis)
        }
    }

    private fun clockChanges(): Flow<LocalDateTime> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.i(TAG, "clockBroadcast=${intent?.action}")
                trySend(LocalDateTime.now())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    private companion object {
        const val TAG = "FoldClock"
    }
}

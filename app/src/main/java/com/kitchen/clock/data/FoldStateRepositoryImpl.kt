package com.kitchen.clock.data

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Display
import androidx.window.WindowSdkExtensions
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.kitchen.clock.domain.FoldSource
import com.kitchen.clock.domain.FoldState
import com.kitchen.clock.domain.UpdateNightLight
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan

// Датчик шарнира, иначе FoldingFeature: FLAT=180, HALF_OPENED=90, нет складки=0.
@OptIn(ExperimentalCoroutinesApi::class)
class FoldStateRepositoryImpl(
    context: Context,
    private val anchor: ActivityAnchor,
    private val updateNightLight: UpdateNightLight,
) : FoldStateRepository {
    private val appContext = context.applicationContext
    private val sensorManager: SensorManager =
        appContext.getSystemService(SensorManager::class.java)
            ?: error("SensorManager is not available")

    override fun observe(initialOpened: Boolean): Flow<FoldState> {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        val angles = if (sensor != null) {
            Log.i(TAG, "foldSource=SENSOR")
            hingeAngles(sensor)
        } else {
            Log.i(TAG, "foldSource=WINDOW_MANAGER")
            anchor.activity.filterNotNull().flatMapLatest(::foldingFeatureAngles)
        }
        val source = if (sensor != null) FoldSource.SENSOR else FoldSource.WINDOW_MANAGER
        return angles
            .accumulate(initialOpened, source)
            .catch { error ->
                Log.e(TAG, "fold observation failed source=$source", error)
            }
    }

    override fun observeOnInnerDisplay(): Flow<Boolean> =
        anchor.activity.flatMapLatest { activity ->
            if (activity == null) {
                flowOf(false)
            } else {
                innerDisplay(activity)
            }
        }.catch { error ->
            Log.e(TAG, "inner display observation failed", error)
            emit(false)
        }

    private fun hingeAngles(sensor: Sensor): Flow<Float> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                if (values.isNotEmpty()) {
                    trySend(values[0])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        val registered = sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )
        if (!registered) {
            Log.e(TAG, "hinge listener registration failed")
            close()
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }.distinctUntilChanged()

    override fun currentOnInnerDisplay(activity: Activity): Boolean? {
        if (WindowSdkExtensions.getInstance().extensionVersion < CURRENT_LAYOUT_EXTENSION) {
            return null
        }
        if (activity.isDestroyed || activity.isFinishing) {
            return null
        }
        if (!activity.window.decorView.isAttachedToWindow) {
            return null
        }
        val displayId = activity.display?.displayId ?: return null
        if (displayId == Display.INVALID_DISPLAY) {
            return null
        }
        return try {
            WindowInfoTracker.getOrCreate(activity)
                .getCurrentWindowLayoutInfo(activity)
                .displayFeatures
                .any { feature -> feature is FoldingFeature }
        } catch (error: RuntimeException) {
            Log.e(TAG, "current window layout failed", error)
            null
        }
    }

    private fun innerDisplay(activity: Activity): Flow<Boolean> =
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { info -> info.displayFeatures.any { it is FoldingFeature } }
            .distinctUntilChanged()
            .onEach { onInner -> Log.i(TAG, "onInnerDisplay=$onInner") }

    private fun foldingFeatureAngles(activity: Activity): Flow<Float> =
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map(::foldingFeatureDegrees)
            .distinctUntilChanged()
            .onEach { angle -> Log.i(TAG, "windowAngle=$angle") }

    private fun foldingFeatureDegrees(info: WindowLayoutInfo): Float {
        val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        return when (feature?.state) {
            null -> 0f
            FoldingFeature.State.FLAT -> 180f
            FoldingFeature.State.HALF_OPENED -> 90f
            else -> 0f
        }
    }

    private fun Flow<Float>.accumulate(
        initialOpened: Boolean,
        source: FoldSource,
    ): Flow<FoldState> = scan(
        initial = FoldState(
            angleDegrees = null,
            isOpenedEnough = initialOpened,
            source = source,
        ),
    ) { previous, angle ->
        FoldState(
            angleDegrees = angle,
            isOpenedEnough = updateNightLight.update(previous.isOpenedEnough, angle),
            source = source,
        )
    }

    private companion object {
        const val TAG = "FoldClock"
        const val CURRENT_LAYOUT_EXTENSION = 9
    }
}

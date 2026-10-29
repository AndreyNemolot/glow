package com.kitchen.clock.ui

import android.app.Activity
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchen.clock.data.ActivityAnchor
import com.kitchen.clock.data.FoldStateRepository
import com.kitchen.clock.data.RearArea
import com.kitchen.clock.data.RearDisplayGateway
import com.kitchen.clock.data.SystemClockRepository
import com.kitchen.clock.domain.ClockFace
import com.kitchen.clock.domain.DayCell
import com.kitchen.clock.domain.DisplayLook
import com.kitchen.clock.domain.DisplayMode
import com.kitchen.clock.domain.FoldSource
import com.kitchen.clock.domain.FoldState
import com.kitchen.clock.domain.displayMode
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Календарь пересобирается только при смене LocalDate, ночник пишется в SavedStateHandle.
class FoldClockViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val anchor: ActivityAnchor,
    private val foldStateRepository: FoldStateRepository,
    systemClockRepository: SystemClockRepository,
    val rearDisplayGateway: RearDisplayGateway,
) : ViewModel() {
    private var calendarDate: LocalDate? = null
    private var cachedDateLine: String = ""
    private var cachedMonthTitle: String = ""
    private var cachedWeekdayLabels: List<String> = emptyList()
    private var cachedDayCells: List<DayCell> = emptyList()

    private val lidOpenSaved: Boolean =
        savedStateHandle.get<Boolean>(NIGHT_LIGHT_KEY) ?: false

    private val foldState: StateFlow<FoldState> = foldStateRepository.observe(lidOpenSaved)
        .onEach { fold ->
            val previous = savedStateHandle.get<Boolean>(NIGHT_LIGHT_KEY)
            savedStateHandle[NIGHT_LIGHT_KEY] = fold.isOpenedEnough
            if (previous != fold.isOpenedEnough) {
                Log.i(
                    TAG,
                    "nightLightOn=${fold.isOpenedEnough} angle=${fold.angleDegrees} source=${fold.source}",
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = FoldState(
                angleDegrees = null,
                isOpenedEnough = lidOpenSaved,
                source = FoldSource.SENSOR,
            ),
        )

    private val hardwareRear: StateFlow<RearArea> = rearDisplayGateway.rearArea.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = RearArea(
            mode = DisplayMode.SingleScreen,
            canPresent = false,
            canTransfer = false,
            transferPossible = false,
        ),
    )

    val rearArea: StateFlow<RearArea> = combine(hardwareRear, foldState) { area, fold ->
        val mode = displayMode(
            lidOpen = fold.isOpenedEnough,
            transferPossible = area.transferPossible,
        )
        if (mode != area.mode) {
            Log.i(TAG, "selectedMode=$mode rearMode=${area.mode} lidOpen=${fold.isOpenedEnough}")
        }
        area.copy(mode = mode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = RearArea(
            mode = displayMode(lidOpen = lidOpenSaved, transferPossible = false),
            canPresent = false,
            canTransfer = false,
            transferPossible = false,
        ),
    )

    val onInnerDisplay = MutableStateFlow(false)

    private val externalBrightness = MutableStateFlow(
        DisplayLook.coerceBrightness(
            savedStateHandle.get<Float>(EXTERNAL_BRIGHTNESS_KEY)
                ?: DisplayLook.DEFAULT_EXTERNAL_BRIGHTNESS,
        ),
    )

    private val innerHue = MutableStateFlow(
        DisplayLook.coerceHue(
            savedStateHandle.get<Float>(INNER_HUE_KEY) ?: DisplayLook.DEFAULT_INNER_HUE,
        ),
    )

    private val lampSettings = combine(externalBrightness, innerHue) { brightness, hue ->
        brightness to hue
    }

    init {
        viewModelScope.launch {
            foldStateRepository.observeOnInnerDisplay().collect { observed ->
                val next = resolveInnerDisplay(observed)
                if (next != observed) {
                    Log.i(TAG, "onInnerDisplay=$next observed=$observed source=snapshot")
                }
                onInnerDisplay.value = next
            }
        }
    }

    // Пустой снимок не сбрасывает ночник: false пишет только поток раскладки.
    fun refreshInnerDisplay(activity: Activity) {
        if (foldStateRepository.currentOnInnerDisplay(activity) != true) {
            return
        }
        if (onInnerDisplay.value) {
            return
        }
        Log.i(TAG, "onInnerDisplay=true source=snapshot")
        onInnerDisplay.value = true
    }

    private fun resolveInnerDisplay(observed: Boolean): Boolean {
        val activity = anchor.activity.value
        val snapshot = activity?.let { foldStateRepository.currentOnInnerDisplay(it) }
        return when {
            snapshot == true -> true
            observed && snapshot == false -> false
            observed -> true
            else -> false
        }
    }

    val uiState: StateFlow<FoldClockUiState> = combine(
        foldState,
        systemClockRepository.now(),
        rearArea.map { area -> area.mode },
        onInnerDisplay,
        lampSettings,
    ) { fold, time, mode, onInner, look ->
        toState(time, fold.isOpenedEnough, mode, onInner, look.first, look.second)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = toState(
            time = LocalDateTime.now(),
            nightLightOn = lidOpenSaved,
            mode = displayMode(lidOpen = lidOpenSaved, transferPossible = false),
            onInnerDisplay = false,
            externalBrightness = externalBrightness.value,
            innerHueDegrees = innerHue.value,
        ),
    )

    // Сохраняет яркость внешнего экрана в пределах шкалы.
    fun setExternalBrightness(value: Float) {
        val next = DisplayLook.coerceBrightness(value)
        if (next == externalBrightness.value) {
            return
        }
        externalBrightness.value = next
        savedStateHandle[EXTERNAL_BRIGHTNESS_KEY] = next
    }

    // Сохраняет оттенок внутреннего ночника.
    fun setInnerHue(value: Float) {
        val next = DisplayLook.coerceHue(value)
        if (next == innerHue.value) {
            return
        }
        innerHue.value = next
        savedStateHandle[INNER_HUE_KEY] = next
    }

    private fun toState(
        time: LocalDateTime,
        nightLightOn: Boolean,
        mode: DisplayMode,
        onInnerDisplay: Boolean,
        externalBrightness: Float,
        innerHueDegrees: Float,
    ): FoldClockUiState {
        val date = time.toLocalDate()
        if (date != calendarDate) {
            calendarDate = date
            cachedDateLine = ClockFace.dateLine(date)
            cachedMonthTitle = ClockFace.monthTitle(date)
            cachedWeekdayLabels = ClockFace.weekdayLabels()
            cachedDayCells = ClockFace.monthGrid(date)
        }
        val burnIn = ClockFace.burnInOffset(time)
        return FoldClockUiState(
            timeLabel = ClockFace.minuteLabel(time),
            dateLine = cachedDateLine,
            weekdayLabels = cachedWeekdayLabels,
            dayCells = cachedDayCells,
            nightLightOn = nightLightOn,
            onInnerDisplay = onInnerDisplay,
            burnInXDp = burnIn.xDp,
            burnInYDp = burnIn.yDp,
            displayMode = mode,
            monthTitle = cachedMonthTitle,
            externalBrightness = externalBrightness,
            innerHueDegrees = innerHueDegrees,
        )
    }

    fun bind(activity: Activity) {
        anchor.bind(activity)
    }

    fun unbind(activity: Activity) {
        anchor.unbind(activity)
    }

    fun isBound(activity: Activity): Boolean = anchor.isBound(activity)

    private companion object {
        const val TAG = "FoldClock"
        const val NIGHT_LIGHT_KEY = "night_light_on"
        const val EXTERNAL_BRIGHTNESS_KEY = "external_brightness"
        const val INNER_HUE_KEY = "inner_hue"
    }
}

package com.kitchen.clock

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.kitchen.clock.domain.DisplayLook
import com.kitchen.clock.domain.DisplayMode
import com.kitchen.clock.domain.activityShowsNightLight
import com.kitchen.clock.ui.CoverClockScreen
import com.kitchen.clock.ui.ExitAppButton
import com.kitchen.clock.ui.FixedCoverOrientation
import com.kitchen.clock.ui.FoldClockGraph
import com.kitchen.clock.ui.FoldClockUiState
import com.kitchen.clock.ui.NightLightScreen
import com.kitchen.clock.ui.SessionOwners
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Яркость и сессия заднего экрана — эффекты Activity, не работа внутри композиции.
@OptIn(ExperimentalWindowApi::class)
class MainActivity : ComponentActivity() {
    private val graph: FoldClockGraph by lazy { FoldClockGraph(this) }
    private var coverOwners: SessionOwners? = null
    private var coverWindow: Window? = null
    private var appliedBrightness: Float? = null
    private var appliedCoverBrightness: Float? = null
    private var appliedBackground: Int? = null
    private var acceptRearSession: Boolean = false
    private var innerRetryPosted: Boolean = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshInnerDisplay()

        override fun onDisplayRemoved(displayId: Int) = refreshInnerDisplay()

        override fun onDisplayChanged(displayId: Int) = refreshInnerDisplay()
    }

    override fun onStart() {
        acceptRearSession = true
        super.onStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        window.lockReverseLandscape()
        window.applyClockChrome()
        val viewModel = graph.viewModel
        viewModel.bind(this)
        refreshInnerDisplay()
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, null)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        applyNightBrightness(
                            state.copy(onInnerDisplay = viewModel.onInnerDisplay.value),
                        )
                    }
                }
                launch {
                    viewModel.uiState
                        .map { state -> state.nightLightOn to state.displayMode }
                        .distinctUntilChanged()
                        .collect { refreshInnerDisplay() }
                }
                launch {
                    viewModel.rearArea.collect { area ->
                        if (!acceptRearSession) {
                            return@collect
                        }
                        viewModel.rearDisplayGateway.sync(
                            mode = area.mode,
                            onPresenter = ::installCover,
                            onEnded = ::destroyCover,
                        )
                    }
                }
            }
        }
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val onInner by viewModel.onInnerDisplay.collectAsStateWithLifecycle()
            ActivityDisplay(
                state = state.copy(onInnerDisplay = onInner),
                onExternalBrightnessChange = viewModel::setExternalBrightness,
                onInnerHueChange = viewModel::setInnerHue,
                onExit = ::finish,
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.applyClockChrome()
        syncOrientation()
        refreshInnerDisplay()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.applyClockChrome()
            syncOrientation()
        }
    }

    override fun onStop() {
        acceptRearSession = false
        val viewModel = graph.viewModel
        if (viewModel.isBound(this)) {
            viewModel.rearDisplayGateway.close()
            destroyCover()
        }
        super.onStop()
    }

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        val viewModel = graph.viewModel
        if (viewModel.isBound(this)) {
            viewModel.rearDisplayGateway.close()
            destroyCover()
        }
        viewModel.unbind(this)
        super.onDestroy()
    }

    private fun installCover(presenter: WindowAreaSessionPresenter) {
        val owners = SessionOwners(viewModelStore)
        val previous = coverOwners
        coverOwners = owners
        previous?.destroy()
        owners.start()
        val viewModel = graph.viewModel
        val composeView = ComposeView(presenter.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owners))
            setViewTreeLifecycleOwner(owners)
            setViewTreeViewModelStoreOwner(owners)
            setViewTreeSavedStateRegistryOwner(owners)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                FixedCoverOrientation(modifier = Modifier.fillMaxSize()) {
                    CoverClockScreen(
                        state = state,
                        onExternalBrightnessChange = viewModel::setExternalBrightness,
                        onInnerHueChange = viewModel::setInnerHue,
                        onExit = ::finish,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        presenter.setContentView(composeView)
        coverWindow = presenter.window
        appliedCoverBrightness = null
        presenter.window?.applyClockChrome()
        syncOrientation()
        applyNightBrightness(
            viewModel.uiState.value.copy(onInnerDisplay = viewModel.onInnerDisplay.value),
        )
        Log.i(TAG, "coverContent=installed")
    }

    private fun destroyCover() {
        coverWindow = null
        appliedCoverBrightness = null
        val owners = coverOwners ?: return
        coverOwners = null
        owners.destroy()
        Log.i(TAG, "coverContent=destroyed")
    }

    // Внутренний и внешний экраны независимо держат один reverseLandscape.
    private fun syncOrientation() {
        if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
        window.lockReverseLandscape()
        coverWindow?.lockReverseLandscape()
    }

    private fun refreshInnerDisplay() {
        val viewModel = graph.viewModel
        if (!viewModel.isBound(this)) {
            return
        }
        viewModel.refreshInnerDisplay(this)
        syncOrientation()
        val state = viewModel.uiState.value
        applyNightBrightness(state.copy(onInnerDisplay = viewModel.onInnerDisplay.value))
        val waitingForInner = state.nightLightOn &&
            state.displayMode == DisplayMode.DualScreen &&
            !viewModel.onInnerDisplay.value
        if (!waitingForInner || innerRetryPosted) {
            return
        }
        innerRetryPosted = true
        window.decorView.post {
            innerRetryPosted = false
            if (isDestroyed || !graph.viewModel.isBound(this@MainActivity)) {
                return@post
            }
            graph.viewModel.refreshInnerDisplay(this@MainActivity)
            val latest = graph.viewModel.uiState.value
            applyNightBrightness(
                latest.copy(onInnerDisplay = graph.viewModel.onInnerDisplay.value),
            )
        }
    }

    private fun applyNightBrightness(state: FoldClockUiState) {
        val orange = activityShowsNightLight(
            onInnerDisplay = state.onInnerDisplay,
            mode = state.displayMode,
            nightLightOn = state.nightLightOn,
        )
        val background = if (orange) {
            DisplayLook.nightPalette(state.innerHueDegrees).backgroundArgb
        } else {
            BLACK
        }
        if (appliedBackground != background) {
            appliedBackground = background
            window.setBackgroundDrawable(ColorDrawable(background))
            Log.i(TAG, "background=${Integer.toHexString(background)} hue=${state.innerHueDegrees}")
        }
        // Полная яркость окна держит обе панели на потолке: внешняя только повторяет внутреннюю.
        val brightness = state.externalBrightness
        if (appliedBrightness != brightness) {
            appliedBrightness = brightness
            val params = window.attributes
            params.screenBrightness = brightness
            window.attributes = params
            syncOrientation()
            Log.i(TAG, "brightness=$brightness orange=$orange mode=${state.displayMode}")
        }
        applyCoverBrightness(state.externalBrightness)
    }

    private fun applyCoverBrightness(brightness: Float) {
        val cover = coverWindow
        if (cover == null) {
            appliedCoverBrightness = null
            return
        }
        if (appliedCoverBrightness == brightness) {
            return
        }
        appliedCoverBrightness = brightness
        val params = cover.attributes
        params.screenBrightness = brightness
        cover.attributes = params
        Log.i(TAG, "coverBrightness=$brightness")
    }

    private companion object {
        const val TAG = "FoldClock"
        const val BLACK = 0xFF000000.toInt()
    }
}

@Composable
private fun ActivityDisplay(
    state: FoldClockUiState,
    onExternalBrightnessChange: (Float) -> Unit,
    onInnerHueChange: (Float) -> Unit,
    onExit: () -> Unit,
) {
    val orange = activityShowsNightLight(
        onInnerDisplay = state.onInnerDisplay,
        mode = state.displayMode,
        nightLightOn = state.nightLightOn,
    )
    Box(modifier = Modifier.fillMaxSize()) {
        if (orange) {
            val palette = DisplayLook.nightPalette(state.innerHueDegrees)
            NightLightScreen(
                deep = Color(palette.deepArgb),
                bright = Color(palette.brightArgb),
                modifier = Modifier.fillMaxSize(),
            )
            ExitAppButton(
                onExit = onExit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = state.burnInXDp.dp, y = state.burnInYDp.dp),
            )
        } else {
            CoverClockScreen(
                state = state,
                onExternalBrightnessChange = onExternalBrightnessChange,
                onInnerHueChange = onInnerHueChange,
                onExit = onExit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

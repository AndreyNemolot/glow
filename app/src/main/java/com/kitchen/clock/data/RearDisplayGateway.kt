package com.kitchen.clock.data

import android.util.Log
import android.view.WindowManager
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaCapability.Operation.Companion.OPERATION_PRESENT_ON_AREA
import androidx.window.area.WindowAreaCapability.Operation.Companion.OPERATION_TRANSFER_ACTIVITY_TO_AREA
import androidx.window.area.WindowAreaCapability.Status.Companion.WINDOW_AREA_STATUS_ACTIVE
import androidx.window.area.WindowAreaCapability.Status.Companion.WINDOW_AREA_STATUS_AVAILABLE
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSession
import androidx.window.area.WindowAreaSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.kitchen.clock.domain.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

// In-flight запрос живёт до колбэка: повторный start при уже активной сессии роняет процесс.
@OptIn(ExperimentalWindowApi::class)
class RearDisplayGateway(
    private val anchor: ActivityAnchor,
) {
    private val controller: WindowAreaController = WindowAreaController.getOrCreate()
    private var latestRear: WindowAreaInfo? = null
    private var session: WindowAreaSession? = null
    private var opening: Boolean = false
    private var cancelOpening: Boolean = false
    private var startRejected: Boolean = false
    private var activeOp: RearOp? = null
    private var desiredOp: RearOp? = null
    private var inflightRequest: Int? = null
    private var suppressAdoptOp: RearOp? = null
    private var request: Int = 0
    private var onSessionEnded: () -> Unit = {}
    private var onPresenter: (WindowAreaSessionPresenter) -> Unit = {}

    val rearArea: Flow<RearArea> = controller.windowAreaInfos
        .map { infos ->
            val rear = infos.firstOrNull { info ->
                info.type == WindowAreaInfo.Type.TYPE_REAR_FACING
            }
            latestRear = rear
            rear.toRearArea()
        }
        .onEach { area ->
            Log.i(
                TAG,
                "displayMode=${area.mode} canPresent=${area.canPresent} canTransfer=${area.canTransfer}",
            )
        }
        .catch { error ->
            Log.e(TAG, "windowAreaInfos failed", error)
            emit(
                RearArea(
                    mode = DisplayMode.SingleScreen,
                    canPresent = false,
                    canTransfer = false,
                    transferPossible = false,
                ),
            )
        }

    fun sync(
        mode: DisplayMode,
        onPresenter: (WindowAreaSessionPresenter) -> Unit,
        onEnded: () -> Unit,
    ) {
        this.onPresenter = onPresenter
        onSessionEnded = onEnded
        val desired = mode.toRearOp()
        desiredOp = desired
        clearAdoptSuppression()
        if (desired == null) {
            close()
            return
        }
        if (inflightRequest != null) {
            if (activeOp != desired && !cancelOpening) {
                request += 1
                cancelOpening = true
                Log.i(TAG, "queued op=$desired")
            }
            return
        }
        if (startRejected && holdRejected(desired)) {
            return
        }
        if (session != null && activeOp == desired) {
            return
        }
        if (session != null) {
            Log.i(TAG, "queued op=$desired")
            releaseSession()
            return
        }
        val rear = latestRear
        if (rear == null) {
            Log.i(TAG, "sync skipped mode=$mode rearPresent=false")
            return
        }
        if (waitUntilTransferEnds(rear, desired)) {
            return
        }
        if (adoptIfActive(rear, desired)) {
            return
        }
        start(rear, desired)
    }

    // Перенос гасит внутренний экран: закрыть его и не показывать оба, пока он ACTIVE.
    private fun waitUntilTransferEnds(rear: WindowAreaInfo, desired: RearOp): Boolean {
        if (desired != RearOp.PRESENT) {
            return false
        }
        if (rear.statusOf(RearOp.TRANSFER) != WINDOW_AREA_STATUS_ACTIVE) {
            return false
        }
        if (suppressAdoptOp != RearOp.TRANSFER) {
            val active = activeSession(rear, RearOp.TRANSFER)
            if (active != null) {
                Log.i(TAG, "session=close-transfer-for-present")
                try {
                    active.close()
                    suppressAdoptOp = RearOp.TRANSFER
                } catch (error: IllegalStateException) {
                    Log.e(TAG, "transfer close for present failed", error)
                }
            }
        }
        Log.i(TAG, "present waiting transferStatus=ACTIVE")
        return true
    }

    fun close() {
        desiredOp = null
        if (inflightRequest != null && session == null) {
            if (!cancelOpening) {
                request += 1
                cancelOpening = true
                Log.i(TAG, "session=cancel-when-started")
            }
            onSessionEnded()
            return
        }
        if (startRejected && session == null) {
            endRejectedSession()
            onSessionEnded()
            return
        }
        releaseSession()
    }

    private fun holdRejected(desired: RearOp): Boolean {
        val rear = latestRear
        val rejected = activeOp
        if (rejected != desired) {
            Log.i(TAG, "start rejection cleared op=$rejected next=$desired")
            startRejected = false
            opening = false
            activeOp = null
            return false
        }
        if (desired == RearOp.PRESENT &&
            rear != null &&
            rear.statusOf(RearOp.TRANSFER) == WINDOW_AREA_STATUS_ACTIVE
        ) {
            Log.i(TAG, "start rejection bypass op=$desired transferStatus=ACTIVE")
            startRejected = false
            opening = false
            activeOp = null
            return false
        }
        if (rear == null) {
            Log.i(TAG, "start held op=$desired rearPresent=false")
            return true
        }
        val status = rear.statusOf(rejected)
        if (status == WINDOW_AREA_STATUS_ACTIVE && suppressAdoptOp != rejected &&
            adoptIfActive(rear, rejected, notifyPresenter = true)
        ) {
            return true
        }
        if (status == WINDOW_AREA_STATUS_AVAILABLE || status == WINDOW_AREA_STATUS_ACTIVE) {
            Log.i(TAG, "start held op=$desired status=$status")
            return true
        }
        Log.i(TAG, "start rejection cleared op=$rejected status=$status")
        startRejected = false
        opening = false
        activeOp = null
        return false
    }

    private fun endRejectedSession() {
        val op = activeOp
        val rear = latestRear
        val current = if (op != null && rear != null) activeSession(rear, op) else null
        if (current != null) {
            Log.i(TAG, "session=close-active op=$op")
            try {
                current.close()
                suppressAdoptOp = op
            } catch (error: IllegalStateException) {
                Log.e(TAG, "session close failed op=$op", error)
            }
        }
        opening = false
        cancelOpening = false
        startRejected = false
        activeOp = null
    }

    private fun clearAdoptSuppression() {
        val op = suppressAdoptOp ?: return
        val rear = latestRear ?: return
        if (rear.statusOf(op) != WINDOW_AREA_STATUS_ACTIVE) {
            suppressAdoptOp = null
        }
    }

    private fun releaseSession() {
        val current = session
        val closedOp = activeOp
        session = null
        opening = false
        activeOp = null
        cancelOpening = false
        startRejected = false
        if (current == null) {
            onSessionEnded()
            return
        }
        request += 1
        Log.i(TAG, "session=close")
        try {
            current.close()
        } catch (error: IllegalStateException) {
            Log.e(TAG, "session close failed", error)
            onSessionEnded()
            restartDesired()
            return
        }
        suppressAdoptOp = closedOp
        onSessionEnded()
    }

    private fun restartDesired() {
        if (opening || session != null || inflightRequest != null || startRejected) {
            return
        }
        val op = desiredOp ?: return
        val rear = latestRear ?: return
        if (rear.statusOf(op) != WINDOW_AREA_STATUS_AVAILABLE) {
            return
        }
        start(rear, op)
    }

    private fun start(rear: WindowAreaInfo, op: RearOp) {
        val status = rear.statusOf(op)
        if (status != WINDOW_AREA_STATUS_AVAILABLE) {
            Log.i(TAG, "start skipped op=$op status=$status")
            return
        }
        val host = anchor.activity.value
        if (host == null) {
            Log.i(TAG, "start skipped op=$op activity=null")
            return
        }
        if (inflightRequest != null) {
            Log.i(TAG, "start skipped op=$op inflight=$inflightRequest")
            return
        }
        val requestId = ++request
        inflightRequest = requestId
        opening = true
        cancelOpening = false
        startRejected = false
        activeOp = op
        Log.i(TAG, "start op=$op request=$requestId")
        try {
            when (op) {
                RearOp.PRESENT -> controller.presentContentOnWindowArea(
                    rear.token,
                    host,
                    host.mainExecutor,
                    presentationCallback(requestId),
                )
                RearOp.TRANSFER -> controller.transferActivityToWindowArea(
                    rear.token,
                    host,
                    host.mainExecutor,
                    transferCallback(requestId),
                )
            }
        } catch (error: IllegalStateException) {
            if (request == requestId) {
                request -= 1
            }
            if (inflightRequest == requestId) {
                inflightRequest = null
            }
            opening = true
            activeOp = op
            startRejected = true
            Log.e(TAG, "start rejected op=$op request=$requestId", error)
        } catch (error: SecurityException) {
            if (request == requestId) {
                request -= 1
            }
            if (inflightRequest == requestId) {
                inflightRequest = null
            }
            opening = false
            activeOp = null
            Log.e(TAG, "start failed op=$op request=$requestId", error)
        }
    }

    private fun adoptIfActive(
        rear: WindowAreaInfo,
        op: RearOp,
        notifyPresenter: Boolean = true,
    ): Boolean {
        if (suppressAdoptOp == op) {
            return false
        }
        if (session != null && activeOp == op && rear.statusOf(op) == WINDOW_AREA_STATUS_ACTIVE) {
            opening = false
            cancelOpening = false
            startRejected = false
            return true
        }
        val active = activeSession(rear, op) ?: return false
        session = active
        opening = false
        cancelOpening = false
        startRejected = false
        activeOp = op
        Log.i(TAG, "session=adopted op=$op")
        if (notifyPresenter && op == RearOp.PRESENT) {
            deliverPresenter(active)
        }
        return true
    }

    private fun activeSession(rear: WindowAreaInfo, op: RearOp): WindowAreaSession? {
        if (rear.statusOf(op) != WINDOW_AREA_STATUS_ACTIVE) {
            return null
        }
        return try {
            rear.getActiveSession(op.operation)
        } catch (error: RuntimeException) {
            Log.e(TAG, "adopt failed op=$op", error)
            null
        }
    }

    private fun deliverPresenter(active: WindowAreaSession) {
        val presenter = active as? WindowAreaSessionPresenter
        if (presenter == null) {
            Log.e(TAG, "present session has no presenter")
            return
        }
        presenter.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onPresenter(presenter)
    }

    private fun onStarted(requestId: Int, op: RearOp, started: WindowAreaSession, deliver: () -> Unit) {
        if (requestId != request || cancelOpening) {
            Log.i(TAG, "stale op=$op request=$requestId")
            closeStarted(requestId, op, started)
            return
        }
        opening = false
        startRejected = false
        if (inflightRequest == requestId) {
            inflightRequest = null
        }
        session = started
        activeOp = op
        Log.i(TAG, "started op=$op request=$requestId")
        deliver()
    }

    private fun closeStarted(requestId: Int, op: RearOp, started: WindowAreaSession) {
        try {
            started.close()
        } catch (error: IllegalStateException) {
            Log.e(TAG, "stale close failed op=$op request=$requestId", error)
            finishCancelled(requestId, op)
        }
    }

    private fun onPlatformEnded(requestId: Int, op: RearOp, error: Throwable?) {
        if (error != null) {
            Log.e(TAG, "ended op=$op request=$requestId", error)
        } else {
            Log.i(TAG, "ended op=$op request=$requestId")
        }
        if (requestId != request) {
            if (session == null && requestId + 1 == request) {
                if (opening) {
                    finishCancelled(requestId, op)
                } else {
                    restartDesired()
                }
            }
            return
        }
        if (inflightRequest == requestId) {
            inflightRequest = null
        }
        if (opening && session == null) {
            holdDismissed(op)
            return
        }
        opening = false
        session = null
        activeOp = null
        cancelOpening = false
        startRejected = false
        onSessionEnded()
        restartDesired()
    }

    // Диалог системы закрыт до сессии: не открывать его снова, пока capability не сменится.
    private fun holdDismissed(op: RearOp) {
        opening = true
        session = null
        activeOp = op
        cancelOpening = false
        startRejected = true
        inflightRequest = null
        Log.i(TAG, "start dismissed op=$op")
        onSessionEnded()
    }

    private fun finishCancelled(requestId: Int, op: RearOp) {
        if (!(opening && session == null && requestId + 1 == request)) {
            return
        }
        if (inflightRequest == requestId) {
            inflightRequest = null
        }
        if (desiredOp == null) {
            holdDismissed(op)
            return
        }
        opening = false
        activeOp = null
        cancelOpening = false
        startRejected = false
        Log.i(TAG, "cancelled op=$op request=$requestId")
        onSessionEnded()
        restartDesired()
    }

    private fun presentationCallback(requestId: Int): WindowAreaPresentationSessionCallback =
        object : WindowAreaPresentationSessionCallback {
            override fun onSessionStarted(session: WindowAreaSessionPresenter) {
                onStarted(requestId, RearOp.PRESENT, session) {
                    session.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onPresenter(session)
                }
            }

            override fun onSessionEnded(t: Throwable?) {
                onPlatformEnded(requestId, RearOp.PRESENT, t)
            }

            override fun onContainerVisibilityChanged(isVisible: Boolean) {
                Log.i(TAG, "rearContainerVisible=$isVisible")
            }
        }

    private fun transferCallback(requestId: Int): WindowAreaSessionCallback =
        object : WindowAreaSessionCallback {
            override fun onSessionStarted(session: WindowAreaSession) {
                onStarted(requestId, RearOp.TRANSFER, session) {}
            }

            override fun onSessionEnded(t: Throwable?) {
                onPlatformEnded(requestId, RearOp.TRANSFER, t)
            }
        }

    private fun DisplayMode.toRearOp(): RearOp? = when (this) {
        DisplayMode.DualScreen -> RearOp.PRESENT
        DisplayMode.RearTransfer -> RearOp.TRANSFER
        DisplayMode.SingleScreen -> null
    }

    private fun WindowAreaInfo.statusOf(op: RearOp): WindowAreaCapability.Status {
        return getCapability(op.operation).status
    }

    private enum class RearOp(
        val operation: WindowAreaCapability.Operation,
    ) {
        PRESENT(OPERATION_PRESENT_ON_AREA),
        TRANSFER(OPERATION_TRANSFER_ACTIVITY_TO_AREA),
    }

    private companion object {
        const val TAG = "FoldClock"
    }
}

data class RearArea(
    val mode: DisplayMode,
    val canPresent: Boolean,
    val canTransfer: Boolean,
    val transferPossible: Boolean,
)

@OptIn(ExperimentalWindowApi::class)
private fun WindowAreaInfo?.toRearArea(): RearArea {
    val rear = this ?: return RearArea(
        mode = DisplayMode.SingleScreen,
        canPresent = false,
        canTransfer = false,
        transferPossible = false,
    )
    val present = rear.getCapability(OPERATION_PRESENT_ON_AREA).status
    val transfer = rear.getCapability(OPERATION_TRANSFER_ACTIVITY_TO_AREA).status
    val transferPossible = transfer == WINDOW_AREA_STATUS_AVAILABLE ||
        transfer == WINDOW_AREA_STATUS_ACTIVE
    val mode = when {
        present == WINDOW_AREA_STATUS_AVAILABLE || present == WINDOW_AREA_STATUS_ACTIVE ->
            DisplayMode.DualScreen
        transferPossible -> DisplayMode.RearTransfer
        else -> DisplayMode.SingleScreen
    }
    return RearArea(
        mode = mode,
        canPresent = present == WINDOW_AREA_STATUS_AVAILABLE,
        canTransfer = transfer == WINDOW_AREA_STATUS_AVAILABLE,
        transferPossible = transferPossible,
    )
}

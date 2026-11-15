package com.macremote.app

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private const val TAP_MAX_MS = 250L
private const val LONG_PRESS_MS = 450L

/** 鼠标加速：慢慢滑精细，快速滑走得远。 */
fun accel(d: Float, sens: Float): Float = d * sens * (1f + min(abs(d), 30f) / 12f)

/**
 * 触控板手势：
 * 单指滑动 = 移动；轻点 = 左键；两指轻点 = 右键；两指滑动 = 滚轮；长按不动再拖 = 按住左键拖动。
 */
fun Modifier.touchpad(client: RemoteClient, sensitivity: Float, reverseScroll: Boolean): Modifier = composed {
    val view = LocalView.current
    pointerInput(sensitivity, reverseScroll) {
        val slop = viewConfiguration.touchSlop
        val scrollStep = 18.dp.toPx() // 手指滑多少像素算滚一行
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            val startTime = first.uptimeMillis
            var maxPointers = 1
            var travel = 0f
            var dragging = false
            var pendX = 0f
            var pendY = 0f
            var scrollAcc = 0f

            while (true) {
                val remain = LONG_PRESS_MS - (currentEvent.changes.first().uptimeMillis - startTime)
                // 还没动过、单指、没进入拖动：等长按超时
                val ev = if (!dragging && maxPointers == 1 && travel < slop && remain > 0) {
                    withTimeoutOrNull(remain) { awaitPointerEvent() }
                } else awaitPointerEvent()
                if (ev == null) {
                    dragging = true
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    client.send("b down")
                    continue
                }
                val pressed = ev.changes.filter { it.pressed }
                maxPointers = maxOf(maxPointers, pressed.size)
                if (pressed.isEmpty()) {
                    val dur = ev.changes.first().uptimeMillis - startTime
                    if (dragging) client.send("b up")
                    else if (dur < TAP_MAX_MS && travel < slop) client.send(if (maxPointers >= 2) "c r" else "c l")
                    break
                }
                if (pressed.size == 1 && maxPointers == 1) {
                    val d: Offset = pressed[0].positionChange()
                    travel += abs(d.x) + abs(d.y)
                    if (travel >= slop || dragging) {
                        pendX += accel(d.x, sensitivity)
                        pendY += accel(d.y, sensitivity)
                        // 攒够一个像素再发，减少包数量
                        if (abs(pendX) >= 1f || abs(pendY) >= 1f) {
                            client.send("m ${"%.1f".format(java.util.Locale.US, pendX)} ${"%.1f".format(java.util.Locale.US, pendY)}")
                            pendX = 0f; pendY = 0f
                        }
                    }
                } else if (pressed.size >= 2) {
                    val dy = pressed.map { it.positionChange().y }.average().toFloat()
                    travel += abs(dy)
                    if (travel >= slop) {
                        // 默认像手机触屏一样：手指往上推，页面往下走
                        scrollAcc += if (reverseScroll) -dy else dy
                        val lines = (scrollAcc / scrollStep).toInt()
                        if (lines != 0) {
                            client.send("s $lines")
                            scrollAcc -= lines * scrollStep
                        }
                    }
                }
                ev.changes.forEach { it.consume() }
            }
        }
    }
}

/** 点击触发；repeat=true 时按住 400ms 后每 120ms 连发。 */
fun Modifier.pressRepeat(repeat: Boolean, scope: CoroutineScope, action: () -> Unit): Modifier =
    pointerInput(repeat) {
        awaitEachGesture {
            awaitFirstDown()
            action()
            var job: Job? = null
            if (repeat) {
                job = scope.launch {
                    delay(400)
                    while (true) { action(); delay(120) }
                }
            }
            waitForUpOrCancellation()
            job?.cancel()
        }
    }

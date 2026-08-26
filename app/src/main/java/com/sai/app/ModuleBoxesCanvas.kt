package com.sai.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.sai.core.layout.ModuleBoxFrame
import com.sai.core.layout.WorkspaceLayoutMath
import kotlin.math.abs

/** Free-position module cards: drag the header to move, corner to resize, tap to grow. */
class ModuleBoxesCanvas(context: Context) : FrameLayout(context) {

    var onFramesChanged: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val frames = linkedMapOf<ModuleType, ModuleBoxFrame>()
    private val cards = mutableMapOf<ModuleType, View>()
    private var pendingOrder: List<ModuleType> = emptyList()
    private var pendingStored: Map<ModuleType, ModuleBoxFrame> = emptyMap()
    private var pendingFactory: ((ModuleType) -> View)? = null

    init {
        setBackgroundColor(0x00000000)
    }

    fun setModules(
        order: List<ModuleType>,
        stored: Map<ModuleType, ModuleBoxFrame>,
        cardFor: (ModuleType) -> View,
    ) {
        pendingOrder = order
        pendingStored = stored
        pendingFactory = cardFor
        if (width > 0 && height > 0) {
            layoutPending()
        } else {
            post { if (width > 0) layoutPending() }
        }
    }

    fun snapshot(): Map<ModuleType, ModuleBoxFrame> =
        frames.mapValues { ModuleBoxFrame(it.value.xDp, it.value.yDp, it.value.wDp, it.value.hDp) }

    fun select(type: ModuleType) {
        val canvasW = width / density
        val canvasH = height / density
        if (canvasW <= 0f || canvasH <= 0f) return
        val current = frames[type] ?: return
        frames[type] = WorkspaceLayoutMath.selectGrow(current, canvasW, canvasH)
        cards[type]?.bringToFront()
        applyFrame(type)
        onFramesChanged?.invoke()
    }

    fun moveBy(type: ModuleType, dxPx: Float, dyPx: Float) {
        val canvasW = width / density
        val canvasH = height / density
        val current = frames[type] ?: return
        current.xDp += dxPx / density
        current.yDp += dyPx / density
        frames[type] = WorkspaceLayoutMath.clampBox(current, canvasW, canvasH)
        applyFrame(type)
    }

    fun resizeBy(type: ModuleType, dwPx: Float, dhPx: Float) {
        val canvasW = width / density
        val canvasH = height / density
        val current = frames[type] ?: return
        current.wDp += dwPx / density
        current.hDp += dhPx / density
        frames[type] = WorkspaceLayoutMath.clampBox(current, canvasW, canvasH)
        applyFrame(type)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachMoveHandle(type: ModuleType, handle: View, onTap: () -> Unit) {
        var lastX = 0f
        var lastY = 0f
        var dragged = false
        val slop = 12f * density
        handle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX
                    lastY = ev.rawY
                    dragged = false
                    cards[type]?.bringToFront()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - lastX
                    val dy = ev.rawY - lastY
                    if (!dragged && abs(dx) + abs(dy) > slop) dragged = true
                    if (dragged) {
                        moveBy(type, dx, dy)
                        lastX = ev.rawX
                        lastY = ev.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (dragged) onFramesChanged?.invoke() else onTap()
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachResizeHandle(type: ModuleType, handle: View) {
        var lastX = 0f
        var lastY = 0f
        handle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX
                    lastY = ev.rawY
                    cards[type]?.bringToFront()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    resizeBy(type, ev.rawX - lastX, ev.rawY - lastY)
                    lastX = ev.rawX
                    lastY = ev.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onFramesChanged?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && pendingFactory != null && childCount == 0) {
            layoutPending()
        }
    }

    private fun layoutPending() {
        val factory = pendingFactory ?: return
        val order = pendingOrder
        if (order.isEmpty()) return
        val canvasW = width / density
        val canvasH = height / density
        if (canvasW <= 1f || canvasH <= 1f) return
        removeAllViews()
        cards.clear()
        frames.clear()
        val defaults = WorkspaceLayoutMath.defaultGrid(order.size, canvasW, canvasH)
        order.forEachIndexed { index, type ->
            val stored = pendingStored[type]
            val frame = WorkspaceLayoutMath.clampBox(
                stored ?: defaults.getOrElse(index) { ModuleBoxFrame(WorkspaceLayoutMath.GAP_DP, WorkspaceLayoutMath.GAP_DP, 240f, 180f) },
                canvasW,
                canvasH,
            )
            frames[type] = frame
            val card = factory(type)
            cards[type] = card
            addView(card)
            applyFrame(type)
        }
        pendingFactory = null
    }

    private fun applyFrame(type: ModuleType) {
        val card = cards[type] ?: return
        val frame = frames[type] ?: return
        val lp = (card.layoutParams as? LayoutParams) ?: LayoutParams(0, 0)
        lp.width = (frame.wDp * density).toInt().coerceAtLeast(1)
        lp.height = (frame.hDp * density).toInt().coerceAtLeast(1)
        lp.leftMargin = (frame.xDp * density).toInt()
        lp.topMargin = (frame.yDp * density).toInt()
        card.layoutParams = lp
    }
}

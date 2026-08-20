package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/** A drag-to-adjust rotary knob: drag vertically to change value, drawn as a 270-degree arc dial. */
class Knob(context: Context, private val min: Float, private val max: Float) : View(context) {

    var value: Float = min
        private set

    var onChange: ((Float) -> Unit)? = null

    private var dragStartY = 0f
    private var dragStartValue = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(60, 62, 68)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = AppTheme.accentColor(context)
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val arc = RectF()

    fun setValue(newValue: Float, notify: Boolean = false) {
        value = newValue.coerceIn(min, max)
        invalidate()
        if (notify) onChange?.invoke(value)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val strokeInset = 12f
        val size = min(width, height).toFloat()
        arc.set(strokeInset, strokeInset, size - strokeInset, size - strokeInset)

        canvas.drawArc(arc, START_ANGLE, SWEEP_ANGLE, false, trackPaint)

        val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)
        canvas.drawArc(arc, START_ANGLE, SWEEP_ANGLE * fraction, false, valuePaint)

        val center = size / 2f
        val angleDeg = START_ANGLE + SWEEP_ANGLE * fraction
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val radius = center - strokeInset
        val px = center + radius * 0.6f * kotlin.math.cos(angleRad).toFloat()
        val py = center + radius * 0.6f * kotlin.math.sin(angleRad).toFloat()
        canvas.drawLine(center, center, px, py, pointerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartY = event.y
                dragStartValue = value
                TouchScrollGuard.lock(this)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = dragStartY - event.y
                val range = max - min
                val newValue = dragStartValue + (deltaY / DRAG_PIXELS_FOR_FULL_RANGE) * range
                setValue(newValue, notify = true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                TouchScrollGuard.unlock(this)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val START_ANGLE = 135f
        private const val SWEEP_ANGLE = 270f
        private const val DRAG_PIXELS_FOR_FULL_RANGE = 300f

        /** Builds a label + knob + live value readout, stacked vertically, ready to drop into any dialog. */
        fun labeled(
            context: Context,
            label: String,
            min: Float,
            max: Float,
            initial: Float,
            format: (Float) -> String = { "%.0f".format(it) },
            onChange: (Float) -> Unit,
        ): LinearLayout {
            val density = context.resources.displayMetrics.density
            val knob = Knob(context, min, max).apply {
                setValue(initial)
                layoutParams = LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt())
            }
            val valueLabel = TextView(context).apply {
                text = format(initial)
                setTextColor(Color.rgb(160, 170, 180))
                textSize = 11f
                gravity = android.view.Gravity.CENTER
            }
            knob.onChange = { v ->
                valueLabel.text = format(v)
                onChange(v)
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
                addView(TextView(context).apply {
                    text = label
                    setTextColor(AppTheme.accentColor(context))
                    textSize = 11f
                    gravity = android.view.Gravity.CENTER
                })
                addView(knob)
                addView(valueLabel)
            }
        }
    }
}

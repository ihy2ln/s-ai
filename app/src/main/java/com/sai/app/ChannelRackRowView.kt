package com.sai.app

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** One Channel Rack row: mute, solo, pan, volume, mixer route, channel button, step grid. */
class ChannelRackRowView(context: Context) : LinearLayout(context) {

    val muteLed = MuteLedView(context)
    val soloLed = MuteLedView(context).apply {
        onColor = AppTheme.gold
        muted = true
    }
    val volumeKnob = Knob(context, 0f, 1f)
    val panKnob = Knob(context, 0f, 1f)
    val mixerTrackLabel = TextView(context)
    val channelButton = Button(context)
    val stepRow = StepRowView(context)

    var onChannelClick: (() -> Unit)? = null
    var onMixerTrackClick: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val density = resources.displayMetrics.density
        val knobSize = (34 * density).toInt()
        val controlGap = (2 * density).toInt()

        volumeKnob.setValue(0.78f)
        panKnob.setValue(0.5f)

        mixerTrackLabel.apply {
            setTextColor(AppTheme.textSecondary)
            textSize = 10f
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onMixerTrackClick?.invoke() }
        }

        channelButton.apply {
            setTextColor(AppTheme.textPrimary)
            textSize = 9f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minHeight = 0
            minimumHeight = 0
            setOnClickListener { onChannelClick?.invoke() }
        }

        addView(muteLed, LayoutParams((22 * density).toInt(), LayoutParams.MATCH_PARENT).apply {
            setMargins(controlGap, 0, controlGap, 0)
        })
        addView(soloLed, LayoutParams((22 * density).toInt(), LayoutParams.MATCH_PARENT).apply {
            setMargins(0, 0, controlGap, 0)
        })
        addView(volumeKnob, LayoutParams(knobSize, LayoutParams.MATCH_PARENT).apply {
            setMargins(0, (2 * density).toInt(), 0, (2 * density).toInt())
        })
        addView(panKnob, LayoutParams(knobSize, LayoutParams.MATCH_PARENT).apply {
            setMargins(0, (2 * density).toInt(), 0, (2 * density).toInt())
        })
        addView(mixerTrackLabel, LayoutParams((26 * density).toInt(), LayoutParams.MATCH_PARENT))
        addView(channelButton, LayoutParams((76 * density).toInt(), LayoutParams.MATCH_PARENT).apply {
            setMargins(controlGap, 0, controlGap, 0)
        })
        addView(stepRow, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    fun bind(state: RackChannelState, channelIndex: Int, displayName: String, rowHeightPx: Int, stepCount: Int) {
        val density = resources.displayMetrics.density
        val vPad = (2 * density).toInt().coerceAtMost(rowHeightPx / 4)
        muteLed.muted = state.muted
        soloLed.muted = !state.soloed
        volumeKnob.setValue(state.volume)
        panKnob.setValue(state.pan)
        mixerTrackLabel.text = if (state.mixerTrack <= 0) "---" else state.mixerTrack.toString()
        channelButton.text = displayName
        channelButton.setBackgroundColor(ChannelRackStore.channelColor(channelIndex, state.instrumentId != null))
        channelButton.setPadding((4 * density).toInt(), vPad, (4 * density).toInt(), vPad)
        stepRow.stepCount = stepCount
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, rowHeightPx)
    }
}

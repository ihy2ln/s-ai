package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** The Theme dialog: a color wheel that can set the window background or the app accent color,
 *  a picture/video background with a mirror toggle, and separate opacity controls for the
 *  background (a dark scrim over the picture/video - never clears the choice, just reveals it)
 *  and for the pill buttons. Everything here is global and applied via Activity.recreate(). */
object ThemeMenu {

    fun show(context: Context, onPickPicture: () -> Unit, onPickVideo: () -> Unit, onRecreate: () -> Unit) {
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val wheel = ColorWheelView(context).apply {
            layoutParams = LinearLayout.LayoutParams((240 * density).toInt(), (240 * density).toInt())
        }

        val setBackgroundButton = Button(context).apply { text = "Set as Background" }
        val setAccentButton = Button(context).apply { text = "Set as Accent Color" }
        val colorButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(setBackgroundButton)
            addView(setAccentButton)
        }

        val choosePictureButton = Button(context).apply { text = "Choose Picture" }
        val chooseVideoButton = Button(context).apply { text = "Choose Video" }
        val mediaButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(choosePictureButton)
            addView(chooseVideoButton)
        }

        val mirrorButton = Button(context).apply {
            text = if (AppBackground.mirrorEnabled(context)) "Mirror: On" else "Mirror: Off"
        }

        val bgOpacityLabel = TextView(context).apply {
            text = "Background Opacity: ${AppTheme.backgroundOpacityPercent(context)}%"
            setTextColor(Color.WHITE)
            setPadding(0, pad / 2, 0, 0)
        }
        val bgOpacitySeekBar = SeekBar(context).apply {
            max = 100
            progress = AppTheme.backgroundOpacityPercent(context)
        }

        val opacityLabel = TextView(context).apply {
            text = "Window / Button Opacity: ${AppTheme.opacityPercent(context)}%"
            setTextColor(Color.WHITE)
            setPadding(0, pad / 2, 0, 0)
        }
        val opacitySeekBar = SeekBar(context).apply {
            max = 90
            progress = (AppTheme.opacityPercent(context) - 10).coerceIn(0, 90)
        }

        val resetButton = Button(context).apply { text = "Reset to Default" }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Pick a color, then choose what it applies to"
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, pad / 2)
            })
            addView(wheel)
            addView(colorButtonsRow)
            addView(mediaButtonsRow)
            addView(mirrorButton)
            addView(bgOpacityLabel)
            addView(bgOpacitySeekBar)
            addView(opacityLabel)
            addView(opacitySeekBar)
            addView(resetButton)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Theme")
            .setView(content)
            .setNegativeButton("Close", null)
            .create()

        setBackgroundButton.setOnClickListener {
            AppBackground.setColor(context, wheel.currentColor())
            dialog.dismiss()
            onRecreate()
        }
        setAccentButton.setOnClickListener {
            AppTheme.setAccentColor(context, wheel.currentColor())
            dialog.dismiss()
            onRecreate()
        }
        choosePictureButton.setOnClickListener {
            dialog.dismiss()
            onPickPicture()
        }
        chooseVideoButton.setOnClickListener {
            dialog.dismiss()
            onPickVideo()
        }
        mirrorButton.setOnClickListener {
            val newValue = !AppBackground.mirrorEnabled(context)
            AppBackground.setMirrorEnabled(context, newValue)
            mirrorButton.text = if (newValue) "Mirror: On" else "Mirror: Off"
            onRecreate()
        }
        bgOpacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                bgOpacityLabel.text = "Background Opacity: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                AppTheme.setBackgroundOpacityPercent(context, bgOpacitySeekBar.progress)
                dialog.dismiss()
                onRecreate()
            }
        })
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                opacityLabel.text = "Window / Button Opacity: ${progress + 10}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                AppTheme.setOpacityPercent(context, opacitySeekBar.progress + 10)
                dialog.dismiss()
                onRecreate()
            }
        })
        resetButton.setOnClickListener {
            AppBackground.resetToDefault(context)
            AppTheme.resetToDefault(context)
            dialog.dismiss()
            onRecreate()
        }

        dialog.show()
    }
}

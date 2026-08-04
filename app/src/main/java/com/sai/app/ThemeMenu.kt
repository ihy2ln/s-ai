package com.sai.app

import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** The Theme dialog: a color wheel that can set the window background or the app accent color,
 *  a picture-as-background option, and a window/button opacity slider. Everything here is global,
 *  applied consistently across every screen rather than per-widget, to keep this "low weight". */
object ThemeMenu {

    fun show(context: android.content.Context, rootView: View, onPickPicture: () -> Unit, onRecreate: () -> Unit) {
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

        val choosePictureButton = Button(context).apply { text = "Choose Picture for Background" }

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
            addView(choosePictureButton)
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
            AppBackground.apply(context, rootView)
            dialog.dismiss()
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

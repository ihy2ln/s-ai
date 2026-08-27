package com.sai.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.sai.core.audio.InsertChain

/** Landscape mixer: 8 insert faders + master, mute/solo, meters, live insert FX. Export writes a stereo WAV mixdown. */
class MixerActivity : ComponentActivity() {

    private lateinit var strips: MutableList<MixerStripState>
    private val meterViews = mutableListOf<MeterView>()
    private val handler = Handler(Looper.getMainLooper())
    private val decay = object : Runnable {
        override fun run() {
            MixerStore.decayPeaks()
            val peaks = MixerStore.snapshotPeaks()
            for (i in meterViews.indices) {
                meterViews[i].level = peaks.getOrElse(i) { 0f }
            }
            handler.postDelayed(this, 50)
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/x-wav")) { uri ->
        if (uri != null) MixdownExporter.writeTo(this, uri)
    }

    private val stemsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) MixdownExporter.writeStems(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        strips = MixerStore.loadStrips(this)
        setContentView(AppBackground.wrap(this, buildUi()))
    }

    override fun onResume() {
        super.onResume()
        handler.post(decay)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(decay)
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (10 * density).toInt()

        val title = TextView(this).apply {
            text = "MIXER"
            setTextColor(AppTheme.accentColor(this@MixerActivity))
            typeface = Typeface.MONOSPACE
            textSize = 18f
        }
        val exportButton = Button(this).apply {
            text = "Export WAV"
            textSize = 12f
            setOnClickListener { confirmExport() }
        }
        val stemsButton = Button(this).apply {
            text = "Stems"
            textSize = 12f
            setOnClickListener { confirmStems() }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(exportButton)
            addView(stemsButton)
            addView(PillButton.create(this@MixerActivity, "N") { NavMenu.show(this@MixerActivity) })
        }

        val stripsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until MixerStore.STRIP_COUNT) {
            stripsRow.addView(stripColumn(i, " ${i + 1} ", isMaster = false))
        }
        stripsRow.addView(stripColumn(MixerStore.STRIP_COUNT, "MST", isMaster = true))

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(titleRow)
            addView(
                HorizontalScrollView(this@MixerActivity).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(stripsRow)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun stripColumn(index: Int, label: String, isMaster: Boolean): LinearLayout {
        val density = resources.displayMetrics.density
        val colW = (56 * density).toInt()
        val meter = MeterView(this).apply {
            layoutParams = LinearLayout.LayoutParams((10 * density).toInt(), (48 * density).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, (4 * density).toInt(), 0, (4 * density).toInt())
            }
        }
        meterViews.add(meter)

        val fader = FaderView(this)
        val initial = if (isMaster) MixerStore.masterVolume(this) else strips[index].volume
        fader.setValue(initial)
        fader.onChange = { value ->
            if (isMaster) MixerStore.setMasterVolume(this, value)
            else {
                strips[index] = strips[index].withVolume(value)
                MixerStore.saveStrips(this, strips)
            }
        }

        val mute = compactToggle(
            text = if ((if (isMaster) MixerStore.masterMuted(this) else strips[index].muted)) "M" else "m",
            lit = if (isMaster) MixerStore.masterMuted(this) else strips[index].muted,
            litColor = Color.rgb(200, 50, 50),
        )
        mute.setOnClickListener {
            if (isMaster) {
                val next = !MixerStore.masterMuted(this)
                MixerStore.setMasterMuted(this, next)
                styleToggle(mute, "M", "m", next, Color.rgb(200, 50, 50))
            } else {
                val next = !strips[index].muted
                strips[index] = strips[index].withMuted(next)
                MixerStore.saveStrips(this, strips)
                styleToggle(mute, "M", "m", next, Color.rgb(200, 50, 50))
            }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
            }
            addView(TextView(this@MixerActivity).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
            })
            addView(fxButton(index, isMaster))
            addView(meter)
            addView(fader, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(mute)
        }

        if (!isMaster) {
            val soloed = strips[index].soloed
            val solo = compactToggle(if (soloed) "S" else "s", soloed, Color.rgb(220, 180, 40))
            solo.setOnClickListener {
                val next = !strips[index].soloed
                strips[index] = strips[index].withSoloed(next)
                MixerStore.saveStrips(this, strips)
                styleToggle(solo, "S", "s", next, Color.rgb(220, 180, 40))
            }
            column.addView(solo)
        }

        return column
    }

    private fun fxButton(index: Int, isMaster: Boolean): Button {
        val chain = if (isMaster) MixerStore.masterChain(this) else strips[index].chain
        val button = compactToggle(chain.shortLabel(), chain.isActive, AppTheme.accentColor(this))
        styleFx(button, chain)
        button.setOnClickListener {
            val current = if (isMaster) MixerStore.masterChain(this) else strips[index].chain
            val title = if (isMaster) "Master chain" else "Insert ${index + 1} chain"
            InsertChainMenu.show(this, title, current) { next ->
                if (isMaster) MixerStore.setMasterChain(this, next)
                else {
                    strips[index] = strips[index].withChain(next)
                    MixerStore.saveStrips(this, strips)
                }
                styleFx(button, next)
            }
        }
        return button
    }

    private fun styleFx(button: Button, chain: InsertChain) {
        button.text = chain.shortLabel()
        button.setTextColor(if (chain.isActive) Color.BLACK else Color.WHITE)
        button.setBackgroundColor(if (chain.isActive) AppTheme.accentColor(this) else Color.rgb(50, 52, 58))
    }

    private fun compactToggle(text: String, lit: Boolean, litColor: Int): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            this.text = text
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, (2 * density).toInt(), 0, 0)
            }
            styleToggle(this, text.uppercase(), text.lowercase(), lit, litColor)
        }
    }

    private fun styleToggle(button: Button, onText: String, offText: String, lit: Boolean, litColor: Int) {
        button.text = if (lit) onText else offText
        button.setTextColor(if (lit) Color.BLACK else Color.WHITE)
        button.setBackgroundColor(if (lit) litColor else Color.rgb(50, 52, 58))
    }

    private fun confirmExport() {
        AlertDialog.Builder(this)
            .setTitle("Export mixdown")
            .setMessage("Render the song through the mixer to a stereo WAV?")
            .setPositiveButton("Export") { _, _ ->
                exportLauncher.launch("sai-mix-${System.currentTimeMillis()}.wav")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmStems() {
        AlertDialog.Builder(this)
            .setTitle("Export stems")
            .setMessage("Render each tracker track plus playlist audio as a zip of WAV files?")
            .setPositiveButton("Export") { _, _ ->
                stemsLauncher.launch("sai-stems-${System.currentTimeMillis()}.zip")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

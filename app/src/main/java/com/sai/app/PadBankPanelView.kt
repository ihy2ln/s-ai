package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** 4×4 sample pad bank. Tap to play; long-press to assign a library sound. */
class PadBankPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onPadPlay: ((SampleEntry) -> Unit)? = null

    private val library = SampleLibrary(context)
    private val padButtons = mutableListOf<Button>()
    private var ids = PadBankStore.load(context)

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        addView(TextView(context).apply {
            text = "Tap a pad to play. Long-press to assign."
            setTextColor(Color.rgb(140, 150, 165))
            textSize = 11f
        })
        var row: LinearLayout? = null
        for (index in 0 until PadBankStore.PAD_COUNT) {
            if (index % 4 == 0) {
                row = LinearLayout(context).apply { orientation = HORIZONTAL }
                addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            }
            val pad = Button(context).apply {
                textSize = 11f
                setTextColor(Color.WHITE)
                minHeight = 0
                minimumHeight = 0
                setOnClickListener { play(index) }
                setOnLongClickListener {
                    assign(index)
                    true
                }
            }
            padButtons.add(pad)
            row!!.addView(pad, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins((3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt())
            })
        }
        refresh()
    }

    fun refresh() {
        ids = PadBankStore.load(context)
        for (index in padButtons.indices) {
            val entry = ids.getOrNull(index)?.let { library.get(it) }
            padButtons[index].text = entry?.displayName ?: "%02X".format(index)
            padButtons[index].setBackgroundColor(ChannelRackStore.channelColor(index, entry != null))
        }
    }

    private fun play(index: Int) {
        val id = ids.getOrNull(index)
        val entry = id?.let { library.get(it) }
        if (entry == null) {
            assign(index)
            return
        }
        val handled = onPadPlay
        Haptics.tap(context)
        if (handled != null) {
            handled.invoke(entry)
            return
        }
        try {
            val choke = ModuleLayoutStore.isChokeEnabled(context, ModuleType.PADS)
            AudioPlayback.playOneShot(
                SampleLoader.decode(context.contentResolver, entry.uri),
                context = context,
                chokeGroup = if (choke) "pads" else null,
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't play ${entry.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun assign(index: Int) {
        val entries = library.all()
        if (entries.isEmpty()) {
            Toast.makeText(context, "Import a sample first (Menu > Samples or Sounds).", Toast.LENGTH_LONG).show()
            return
        }
        val labels = (entries.map { it.displayName } + "Clear pad").toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Pad %02X".format(index))
            .setItems(labels) { _, which ->
                val id = if (which == entries.size) null else entries[which].id
                PadBankStore.set(context, index, id)
                refresh()
            }
            .show()
    }
}

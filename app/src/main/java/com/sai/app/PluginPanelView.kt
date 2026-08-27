package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sai.core.audio.InsertFx
import com.sai.core.audio.InsertKind
import com.sai.core.audio.InsertSlot
import com.sai.core.audio.InstrumentVoice
import com.sai.core.audio.Wav
import com.sai.core.plugin.PluginDescriptor
import com.sai.core.plugin.PluginRole

/** Home panel for a VST-style instrument or effect module. */
class PluginPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val plugin: PluginDescriptor? = null,
    private val moduleType: ModuleType = ModuleType.PULSE_KEYS,
) : LinearLayout(context, attrs) {

    var onSaveToLibrary: ((sourceName: String, wav: Wav) -> Unit)? = null
    var onSendToRack: ((sourceName: String, wav: Wav) -> Unit)? = null

    private val params: MutableMap<String, Double> = HashMap()
    private var keyboardOctave = 4
    private val status: TextView

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val descriptor = plugin

        if (descriptor == null) {
            status = TextView(context).apply {
                text = "Unknown module"
                setTextColor(Color.rgb(140, 150, 160))
            }
            addView(status)
        } else {
            params.putAll(descriptor.defaultParams)
            params.putAll(PluginParamStore.load(context, descriptor.id))
            if (descriptor.canInsertOnMixer) {
                val kind = insertKind()
                if (kind != null) params.putAll(InsertFx.mergeDefaults(kind, params))
            } else {
                seedInstrumentDefaults(descriptor)
            }

            status = TextView(context).apply {
                text = "${descriptor.format.badge}  ${descriptor.vendor}"
                setTextColor(Color.rgb(140, 150, 165))
                textSize = 11f
            }
            val subtitle = TextView(context).apply {
                text = descriptor.subtitle
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }

            addView(status)
            addView(subtitle)
            addView(
                HorizontalScrollView(context).apply {
                    isNestedScrollingEnabled = false
                    addView(buildKnobs(descriptor))
                },
            )

            if (descriptor.role == PluginRole.INSTRUMENT && descriptor.homeModule != "SCALE") {
                addView(buildKeyboard())
                addView(
                    HorizontalScrollView(context).apply {
                        isNestedScrollingEnabled = false
                        addView(buildInstrumentButtons())
                    },
                )
            } else {
                if (descriptor.canInsertOnMixer) {
                    addView(
                        HorizontalScrollView(context).apply {
                            isNestedScrollingEnabled = false
                            addView(buildEffectButtons(descriptor))
                        },
                    )
                } else {
                    addView(TextView(context).apply {
                        text = "Scale reference only — no audio engine. Delay before Reverb; Tune before EQ on mixer chains."
                        setTextColor(Color.rgb(140, 150, 165))
                        textSize = 11f
                    })
                }
            }
        }
    }

    private fun insertKind(): InsertKind? {
        val name = plugin?.insertKind ?: return null
        return try {
            InsertKind.valueOf(name)
        } catch (e: Exception) {
            null
        }
    }

    private fun seedInstrumentDefaults(descriptor: PluginDescriptor) {
        if (descriptor.homeModule == "SCALE") {
            if (!params.containsKey("root")) params["root"] = 0.0
            if (!params.containsKey("mode")) params["mode"] = 0.0
            return
        }
        val kind = InstrumentVoice.kindForHomeModule(descriptor.homeModule ?: "") ?: return
        val sample = InstrumentVoice.render(kind, 60)
        if (!params.containsKey("cutoff")) params["cutoff"] = 4000.0
        if (!params.containsKey("resonance")) params["resonance"] = 0.2
        if (!params.containsKey("drive")) params["drive"] = 0.0
        if (!params.containsKey("attack")) params["attack"] = 0.01
        if (!params.containsKey("release")) params["release"] = 0.12
        sample.frameCount
    }

    private fun persist() {
        val id = plugin?.id ?: return
        PluginParamStore.save(context, id, params)
    }

    private fun buildKnobs(descriptor: PluginDescriptor): LinearLayout {
        val knobs = LinearLayout(context).apply { orientation = HORIZONTAL }
        if (descriptor.role == PluginRole.INSTRUMENT && descriptor.homeModule != "SCALE") {
            knobs.addView(knob("CUTOFF", 200f, 12000f, "cutoff") { "%.0fHz".format(it) })
            knobs.addView(knob("RES", 0f, 1f, "resonance") { "%.2f".format(it) })
            knobs.addView(knob("CRUNCH", 0f, 1f, "drive") { "%.2f".format(it) })
            knobs.addView(knob("ATK", 0f, 0.4f, "attack") { "%.2fs".format(it) })
            knobs.addView(knob("REL", 0.02f, 0.8f, "release") { "%.2fs".format(it) })
            return knobs
        }
        when (insertKind()) {
            InsertKind.DELAY -> {
                knobs.addView(knob("TIME", 20f, 800f, "time") { "%.0fms".format(it) })
                knobs.addView(knob("FBK", 0f, 0.9f, "feedback") { "%.2f".format(it) })
                knobs.addView(knob("MIX", 0f, 1f, "mix") { "%.2f".format(it) })
            }
            InsertKind.DISTORTION -> {
                knobs.addView(knob("DRIVE", 0f, 1f, "drive") { "%.2f".format(it) })
                knobs.addView(knob("TONE", 0f, 1f, "tone") { "%.2f".format(it) })
                knobs.addView(knob("MIX", 0f, 1f, "mix") { "%.2f".format(it) })
            }
            InsertKind.CHORUS -> {
                knobs.addView(knob("RATE", 0.1f, 6f, "rate") { "%.2fHz".format(it) })
                knobs.addView(knob("DEPTH", 0f, 1f, "depth") { "%.2f".format(it) })
                knobs.addView(knob("MIX", 0f, 1f, "mix") { "%.2f".format(it) })
            }
            InsertKind.LIMITER -> {
                knobs.addView(knob("THRES", -18f, 0f, "threshold") { "%.0fdB".format(it) })
                knobs.addView(knob("REL", 10f, 400f, "release") { "%.0fms".format(it) })
            }
            InsertKind.REVERB -> {
                knobs.addView(knob("SIZE", 0f, 1f, "size") { "%.2f".format(it) })
                knobs.addView(knob("DAMP", 0f, 1f, "damp") { "%.2f".format(it) })
                knobs.addView(knob("MIX", 0f, 1f, "mix") { "%.2f".format(it) })
                knobs.addView(knob("DUCK", 0f, 1f, "duck") { "%.2f".format(it) })
            }
            InsertKind.PHASER -> {
                knobs.addView(knob("RATE", 0.05f, 8f, "rate") { "%.2fHz".format(it) })
                knobs.addView(knob("DEPTH", 0f, 1f, "depth") { "%.2f".format(it) })
                knobs.addView(knob("MIX", 0f, 1f, "mix") { "%.2f".format(it) })
            }
            InsertKind.CRUSH -> {
                knobs.addView(knob("BITS", 4f, 16f, "bits") { "%.0f".format(it) })
                knobs.addView(knob("RATE", 0.05f, 1f, "rate") { "%.2f".format(it) })
                knobs.addView(knob("MIX", 0f, 1f, "mix") { "%.2f".format(it) })
            }
            InsertKind.TAPE -> {
                knobs.addView(knob("DRIVE", 0f, 1f, "drive") { "%.2f".format(it) })
                knobs.addView(knob("WOW", 0f, 1f, "wow") { "%.2f".format(it) })
                knobs.addView(knob("MIX", 0f, 1f, "mix") { "%.2f".format(it) })
            }
            InsertKind.GATE -> {
                knobs.addView(knob("THRES", 0.001f, 0.5f, "threshold") { "%.3f".format(it) })
                knobs.addView(knob("ATT", 0.5f, 40f, "attack") { "%.0fms".format(it) })
                knobs.addView(knob("REL", 10f, 400f, "release") { "%.0fms".format(it) })
            }
            InsertKind.DEESS -> {
                knobs.addView(knob("AMT", 0f, 1f, "amount") { "%.2f".format(it) })
                knobs.addView(knob("FREQ", 4000f, 12000f, "frequency") { "%.0fHz".format(it) })
            }
            InsertKind.AMP -> {
                knobs.addView(knob("DRIVE", 0f, 1f, "drive") { "%.2f".format(it) })
                knobs.addView(knob("TONE", 0f, 1f, "tone") { "%.2f".format(it) })
                knobs.addView(knob("MIX", 0f, 1f, "mix") { "%.2f".format(it) })
            }
            InsertKind.TUNE -> {
                knobs.addView(knob("AMT", 0f, 1f, "amount") { "%.2f".format(it) })
                knobs.addView(knob("NOTE", 48f, 72f, "note") { "%.0f".format(it) })
            }
            else -> {
                if (descriptor.homeModule == "SCALE") {
                    knobs.addView(knob("ROOT", 0f, 11f, "root") { scaleNoteName(it.toInt()) })
                    knobs.addView(knob("MODE", 0f, 6f, "mode") { scaleModeName(it.toInt()) })
                }
            }
        }
        return knobs
    }

    private fun knob(label: String, min: Float, max: Float, key: String, format: (Float) -> String) =
        Knob.labeled(
            context,
            label,
            min,
            max,
            (params[key] ?: min.toDouble()).toFloat(),
            format,
        ) {
            params[key] = it.toDouble()
            persist()
        }

    private fun compactButton(label: String, onClick: () -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(context).apply {
            text = label
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun renderNote(midiNote: Int): Wav? {
        val descriptor = plugin ?: return null
        val kind = InstrumentVoice.kindForHomeModule(descriptor.homeModule ?: moduleType.name) ?: return null
        return InstrumentVoice.render(kind, midiNote, params)
    }

    private fun playNote(midiNote: Int) {
        val wav = renderNote(midiNote) ?: return
        val choke = ModuleLayoutStore.isChokeEnabled(context, moduleType)
        AudioPlayback.playOneShot(wav, context = context, chokeGroup = if (choke) moduleType.name else null)
    }

    private fun buildKeyboard(): LinearLayout {
        val density = resources.displayMetrics.density
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val black = setOf(1, 3, 6, 8, 10)
        val octaveLabel = TextView(context).apply {
            text = "C$keyboardOctave"
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            textSize = 11f
        }
        val keys = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(compactButton("-") {
                keyboardOctave = (keyboardOctave - 1).coerceIn(1, 7)
                octaveLabel.text = "C$keyboardOctave"
            })
            addView(octaveLabel)
            addView(compactButton("+") {
                keyboardOctave = (keyboardOctave + 1).coerceIn(1, 7)
                octaveLabel.text = "C$keyboardOctave"
            })
            for ((index, name) in names.withIndex()) {
                addView(Button(context).apply {
                    text = name
                    textSize = 10f
                    minHeight = 0
                    minimumHeight = 0
                    minWidth = 0
                    setPadding((6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt())
                    setTextColor(if (index in black) Color.WHITE else Color.BLACK)
                    setBackgroundColor(if (index in black) Color.rgb(40, 42, 48) else Color.rgb(220, 220, 225))
                    setOnClickListener { playNote((keyboardOctave + 1) * 12 + index) }
                })
            }
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            addView(TextView(context).apply {
                text = "Keys — POLY stacks notes; MONO cuts the previous one"
                setTextColor(Color.rgb(140, 150, 165))
                textSize = 10f
            })
            addView(HorizontalScrollView(context).apply { addView(keys) })
        }
    }

    private fun buildInstrumentButtons(): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(compactButton("Preview") { playNote(60) })
        addView(compactButton("To Rack") { sendToRack() })
        addView(compactButton("Save to Library") { saveToLibrary() })
    }

    private fun buildEffectButtons(descriptor: PluginDescriptor): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(compactButton("Preview") { previewEffect() })
        addView(compactButton("Insert on Mixer") { pickMixerStrip(descriptor) })
    }

    private fun toneForPreview(): Wav = InstrumentVoice.render(
        com.sai.core.audio.VoiceKind.SAW_LEAD,
        midiNote = 60,
        params = mapOf("duration" to 0.8),
    )

    private fun previewEffect() {
        if (ModuleLayoutStore.isBypassEnabled(context, moduleType)) {
            AudioPlayback.playOneShot(toneForPreview(), context = context)
            return
        }
        val kind = insertKind() ?: return
        val slot = InsertSlot(kind = kind, params = params.toMap(), engineId = plugin?.engineId.orEmpty())
        AudioPlayback.playOneShot(InsertFx.apply(toneForPreview(), slot), context = context)
    }

    private fun pickMixerStrip(descriptor: PluginDescriptor) {
        val labels = (1..MixerStore.STRIP_COUNT).map { "Insert $it" } + "Master"
        AlertDialog.Builder(context)
            .setTitle("Append ${descriptor.name}")
            .setItems(labels.toTypedArray()) { _, which ->
                val slot = InsertSlot(
                    kind = insertKind() ?: return@setItems,
                    params = params.toMap(),
                    engineId = descriptor.engineId.ifBlank { descriptor.id },
                )
                val stripIndex = if (which >= MixerStore.STRIP_COUNT) null else which
                MixerStore.appendInsert(context, stripIndex, slot)
                val where = if (stripIndex == null) "master" else "insert ${stripIndex + 1}"
                Toast.makeText(context, "${descriptor.name} appended on $where", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scaleNoteName(value: Int): String {
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[value.coerceIn(0, 11)]
    }

    private fun scaleModeName(value: Int): String {
        val names = arrayOf("Major", "Dorian", "Phrygian", "Lydian", "Mixolydian", "Minor", "Locrian")
        return names[value.coerceIn(0, 6)]
    }

    private fun saveToLibrary() {
        val wav = renderNote(60) ?: return
        onSaveToLibrary?.invoke(plugin?.name ?: moduleType.label, wav)
    }

    private fun sendToRack() {
        val wav = renderNote(60) ?: return
        onSendToRack?.invoke(plugin?.name ?: moduleType.label, wav)
    }
}

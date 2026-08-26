package com.sai.app

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.sai.core.audio.MixerMath
import com.sai.core.audio.SongMixdown
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO
import com.sai.core.tracker.Song
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

object MixdownExporter {

    fun render(
        context: Context,
        onlyTrack: Int? = null,
        audioOnly: Boolean = false,
    ): Wav {
        val project = TrackerProjectStore.get(context)
        val clips = PlaylistStore.load(context)
        val library = SampleLibrary(context).byId()
        val used = project.phrases.values.flatMap { it.steps }.mapNotNull { it.instrument }.toSet() +
            clips.mapNotNull { it.sampleId }
        val samples = mutableMapOf<Int, Wav>()
        val resolver = context.contentResolver
        for (id in used) {
            val entry = library[id] ?: continue
            try {
                samples[id] = SampleLoader.decode(resolver, entry.uri)
            } catch (e: Exception) {
                // Skip a sample that fails to decode rather than aborting the mix.
            }
        }
        val racks = ChannelRackStore.loadChannels(context)
        val channels = (0 until project.song.trackCount).map { track ->
            val rack = racks.getOrNull(track)
            MixerMath.Channel(
                muted = rack?.muted ?: false,
                soloed = rack?.soloed ?: false,
                volume = rack?.volume ?: 1f,
                pan = rack?.pan ?: 0.5f,
                mixerTrack = rack?.mixerTrack ?: 0,
            )
        }
        return SongMixdown.render(
            song = project.song,
            phrases = project.phrases,
            bpm = project.bpm,
            samplesById = samples,
            channels = channels,
            strips = MixerStore.mathStrips(context),
            mixerMaster = MixerStore.masterVolume(context),
            masterMuted = MixerStore.masterMuted(context),
            projectMaster = ProjectPlayback.masterVolume(context) / 127f,
            pitchSemitones = ProjectPlayback.pitchSemitones(context),
            masterInsert = MixerStore.masterInsert(context),
            chokeSameTrack = ModuleLayoutStore.isChokeEnabled(context, ModuleType.TRACKER),
            patternLengthAt = { project.patternLength(it) },
            swingPercent = project.swing,
            clips = clips,
            onlyTrack = onlyTrack,
            audioOnly = audioOnly,
        )
    }

    fun writeTo(context: Context, uri: Uri) {
        Toast.makeText(context, "Rendering mixdown…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val wav = render(context)
                context.contentResolver.openOutputStream(uri)!!.use { out -> WavIO.write(wav, out) }
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Exported stereo WAV", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun writeStems(context: Context, uri: Uri) {
        Toast.makeText(context, "Rendering stems…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val tracks = Song.TRACK_COUNT
                context.contentResolver.openOutputStream(uri)!!.use { out ->
                    ZipOutputStream(out).use { zip ->
                        for (track in 0 until tracks) {
                            val wav = render(context, onlyTrack = track)
                            zip.putNextEntry(ZipEntry("trk-${track + 1}.wav"))
                            WavIO.write(wav, zip)
                            zip.closeEntry()
                        }
                        val audio = render(context, audioOnly = true)
                        zip.putNextEntry(ZipEntry("audio.wav"))
                        WavIO.write(audio, zip)
                        zip.closeEntry()
                    }
                }
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Exported ${tracks} tracks + audio stem zip", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, "Stem export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

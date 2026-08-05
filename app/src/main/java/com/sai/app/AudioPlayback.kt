package com.sai.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import com.sai.core.audio.Wav
import kotlin.math.max

object AudioPlayback {

    /** The currently-playing [AudioTrack] per choke group (see [playOneShot]'s chokeGroup param). */
    private val activeTracks = mutableMapOf<String, AudioTrack>()

    /** [context], when provided, is used to detect the current output route (headphones/Bluetooth/
     *  speaker) so wide stereo content can be narrowed toward mono on a phone's built-in speaker,
     *  where separated stereo speakers aren't available and full width just comb-filters.
     *
     *  [chokeGroup], when non-null, gives this one-shot "Cut Itself" (monophonic) behavior: any
     *  sound still playing under the same group key is immediately stopped so the new note isn't
     *  layered over it - the classic tracker/sampler "cut previous note on this channel" trick. */
    fun playOneShot(wav: Wav, rate: Float = 1.0f, context: Context? = null, chokeGroup: String? = null) {
        if (chokeGroup != null) {
            val previous = synchronized(activeTracks) { activeTracks.remove(chokeGroup) }
            previous?.let { stopSilently(it) }
        }

        val effective = if (wav.channels == 2 && context != null && AudioRoute.shouldNarrowForSpeaker(context)) {
            narrowToMono(wav)
        } else {
            wav
        }
        val channelMask = if (effective.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val pcmBytes = ByteArray(effective.samples.size * 2)
        var i = 0
        for (s in effective.samples) {
            val v = s.toInt()
            pcmBytes[i++] = (v and 0xFF).toByte()
            pcmBytes[i++] = ((v shr 8) and 0xFF).toByte()
        }

        val minBufferSize = AudioTrack.getMinBufferSize(effective.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(effective.sampleRate)
                .setChannelMask(channelMask)
                .build(),
            max(minBufferSize, pcmBytes.size),
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track.write(pcmBytes, 0, pcmBytes.size)
        if (rate != 1.0f) {
            try {
                track.playbackParams = PlaybackParams().setSpeed(rate).setPitch(rate)
            } catch (e: Exception) {
                // Some devices/formats reject a changed playback rate; fall back to unpitched playback.
            }
        }
        track.play()
        if (chokeGroup != null) {
            synchronized(activeTracks) { activeTracks[chokeGroup] = track }
        }

        val durationMs = (effective.frameCount.toDouble() / effective.sampleRate / rate * 1000).toLong().coerceAtLeast(50)
        Thread {
            Thread.sleep(durationMs + 50)
            if (chokeGroup != null) {
                synchronized(activeTracks) {
                    if (activeTracks[chokeGroup] === track) activeTracks.remove(chokeGroup)
                }
            }
            stopSilently(track)
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun stopSilently(track: AudioTrack) {
        try { track.stop() } catch (e: Exception) { /* already stopped/released */ }
        try { track.release() } catch (e: Exception) { /* already released */ }
    }

    private fun narrowToMono(wav: Wav): Wav {
        val frames = wav.frameCount
        val out = ShortArray(frames)
        for (i in 0 until frames) {
            val l = wav.samples[i * 2].toInt()
            val r = wav.samples[i * 2 + 1].toInt()
            out[i] = ((l + r) / 2).toShort()
        }
        return Wav(wav.sampleRate, 1, out)
    }
}

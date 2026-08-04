package com.sai.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import com.sai.core.audio.Wav
import kotlin.math.max

object AudioPlayback {

    fun playOneShot(wav: Wav, rate: Float = 1.0f) {
        val channelMask = if (wav.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val pcmBytes = ByteArray(wav.samples.size * 2)
        var i = 0
        for (s in wav.samples) {
            val v = s.toInt()
            pcmBytes[i++] = (v and 0xFF).toByte()
            pcmBytes[i++] = ((v shr 8) and 0xFF).toByte()
        }

        val minBufferSize = AudioTrack.getMinBufferSize(wav.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(wav.sampleRate)
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

        val durationMs = (wav.frameCount.toDouble() / wav.sampleRate / rate * 1000).toLong().coerceAtLeast(50)
        Thread {
            Thread.sleep(durationMs + 50)
            track.stop()
            track.release()
        }.apply {
            isDaemon = true
            start()
        }
    }
}

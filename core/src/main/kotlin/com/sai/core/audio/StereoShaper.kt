package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Positions a sound in the stereo field: left/right pan, stereo width (mid-side), and a
 * simulated front/back "depth" (quieter, darker, and slightly delayed the farther back it is -
 * phones only have left/right speakers, so front/back is approximated with distance cues rather
 * than true extra channels).
 */
object StereoShaper {

    /**
     * @param pan -1 (hard left) .. 1 (hard right), 0 = center
     * @param width 0 (mono) .. 2 (extra wide), 1 = unchanged stereo image
     * @param depth 0 (up front) .. 1 (far back): reduces gain, darkens, and adds a touch of delay
     */
    fun apply(wav: Wav, pan: Double, width: Double, depth: Double): Wav {
        val stereo = toStereo(wav)
        val frames = stereo.frameCount

        val panClamped = pan.coerceIn(-1.0, 1.0)
        val angle = (panClamped + 1.0) / 2.0 * (PI / 2.0)
        val leftGain = cos(angle)
        val rightGain = sin(angle)

        val widthClamped = width.coerceIn(0.0, 2.0)

        val depthClamped = depth.coerceIn(0.0, 1.0)
        val depthGain = 10.0.pow(-(depthClamped * 12.0) / 20.0)
        val cutoffHz = 20000.0 - depthClamped * 15000.0
        val lpAlpha = lowPassAlpha(cutoffHz, stereo.sampleRate)
        val delayFrames = (depthClamped * 0.02 * stereo.sampleRate).toInt()

        val out = ShortArray((frames + delayFrames) * 2)
        var lpL = 0.0
        var lpR = 0.0
        for (i in 0 until frames) {
            val l = stereo.samples[i * 2] / 32768.0
            val r = stereo.samples[i * 2 + 1] / 32768.0
            val mid = (l + r) / 2.0
            val side = (l - r) / 2.0

            val newL = (mid + side * widthClamped) * leftGain * depthGain
            val newR = (mid - side * widthClamped) * rightGain * depthGain
            lpL += lpAlpha * (newL - lpL)
            lpR += lpAlpha * (newR - lpR)

            val destIndex = (i + delayFrames) * 2
            out[destIndex] = (lpL * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            out[destIndex + 1] = (lpR * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Wav(stereo.sampleRate, 2, out)
    }

    private fun toStereo(wav: Wav): Wav {
        if (wav.channels == 2) return wav
        val frames = wav.frameCount
        val out = ShortArray(frames * 2)
        if (wav.channels == 1) {
            for (i in 0 until frames) {
                out[i * 2] = wav.samples[i]
                out[i * 2 + 1] = wav.samples[i]
            }
        } else {
            for (i in 0 until frames) {
                out[i * 2] = wav.samples[i * wav.channels]
                out[i * 2 + 1] = wav.samples[i * wav.channels + 1]
            }
        }
        return Wav(wav.sampleRate, 2, out)
    }

    private fun lowPassAlpha(cutoffHz: Double, sampleRate: Int): Double {
        val rc = 1.0 / (2 * PI * cutoffHz)
        val dt = 1.0 / sampleRate
        return dt / (rc + dt)
    }
}

package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.random.Random

/**
 * "Warp" tools for reshaping a sample independently of its original speed and key: a time
 * stretch (duration change, pitch preserved), a pitch shift (key change, duration preserved),
 * a BPM sync (stretch a loop to match a target tempo), and a granular scatter effect.
 */
object SampleWarp {

    /** Overlap-add time stretch: changes duration by [factor] (>1 = longer/slower, <1 =
     *  shorter/faster) while keeping pitch the same. Clamped to 0.25x-4x to keep output sane. */
    fun timeStretch(wav: Wav, factor: Double, grainMs: Double = 40.0): Wav {
        val f = factor.coerceIn(0.25, 4.0)
        val frames = wav.frameCount
        if (frames == 0 || f == 1.0) return wav
        val channels = wav.channels

        val grainFrames = (wav.sampleRate * grainMs / 1000.0).toInt().coerceIn(1, frames.coerceAtLeast(1))
        val hopIn = (grainFrames / 2).coerceAtLeast(1)
        val hopOut = (hopIn * f).toInt().coerceAtLeast(1)
        val outFrames = (frames * f).toInt().coerceAtLeast(1)

        val outSum = DoubleArray((outFrames + grainFrames) * channels)
        val outWeight = DoubleArray(outFrames + grainFrames)

        var inPos = 0
        var outPos = 0
        while (inPos < frames) {
            val grainEnd = (inPos + grainFrames).coerceAtMost(frames)
            val length = grainEnd - inPos
            for (i in 0 until length) {
                val w = hannWindow(i, length)
                val dstFrame = outPos + i
                for (c in 0 until channels) {
                    outSum[dstFrame * channels + c] += wav.samples[(inPos + i) * channels + c] * w
                }
                outWeight[dstFrame] += w
            }
            inPos += hopIn
            outPos += hopOut
        }

        val result = ShortArray(outFrames * channels)
        for (frame in 0 until outFrames) {
            val w = if (outWeight[frame] > 1e-6) outWeight[frame] else 1.0
            for (c in 0 until channels) {
                val v = outSum[frame * channels + c] / w
                result[frame * channels + c] = v.toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return wav.copy(samples = result)
    }

    /** Shifts pitch by [semitones] while keeping the original duration: resample (which changes
     *  both pitch and speed) then time-stretch back to the original length. */
    fun pitchShift(wav: Wav, semitones: Double): Wav {
        if (semitones == 0.0) return wav
        val rate = 2.0.pow(semitones / 12.0)
        val resampled = resampleByRate(wav, rate)
        return timeStretch(resampled, rate)
    }

    /** Time-stretches [wav] so a loop originally at [sourceBpm] plays back at [targetBpm]. */
    fun bpmSync(wav: Wav, sourceBpm: Double, targetBpm: Double): Wav {
        if (sourceBpm <= 0.0 || targetBpm <= 0.0) return wav
        return timeStretch(wav, sourceBpm / targetBpm)
    }

    /** Chops [wav] into [grainMs]-long grains and scrambles their order by [scatter] (0 = left
     *  untouched, 1 = fully randomized), for a granular/glitch texture. Grains keep their
     *  original size and simply play back in a shuffled sequence. */
    fun granulate(wav: Wav, grainMs: Double = 60.0, scatter: Double = 0.5, seed: Long = 0L): Wav {
        val channels = wav.channels
        val frames = wav.frameCount
        if (frames == 0) return wav
        val grainFrames = (wav.sampleRate * grainMs / 1000.0).toInt().coerceIn(1, frames)
        val grainCount = (frames + grainFrames - 1) / grainFrames
        if (grainCount <= 1) return wav

        val order = (0 until grainCount).toMutableList()
        val random = Random(seed)
        val swaps = (grainCount * scatter.coerceIn(0.0, 1.0)).toInt()
        repeat(swaps) {
            val a = random.nextInt(grainCount)
            val b = random.nextInt(grainCount)
            val tmp = order[a]
            order[a] = order[b]
            order[b] = tmp
        }

        val out = ShortArray(frames * channels)
        var writeFrame = 0
        for (grainIndex in order) {
            val start = grainIndex * grainFrames
            val end = (start + grainFrames).coerceAtMost(frames)
            val length = (end - start).coerceAtMost(frames - writeFrame)
            if (length <= 0) continue
            for (f in 0 until length) {
                for (c in 0 until channels) {
                    out[(writeFrame + f) * channels + c] = wav.samples[(start + f) * channels + c]
                }
            }
            writeFrame += length
        }
        return wav.copy(samples = out)
    }

    /** Resamples by [rate] (>1 = higher pitch / shorter, <1 = lower / longer). Pitch and
     *  duration both change — the same trick AudioTrack speed uses for tracker notes. */
    fun resample(wav: Wav, rate: Double): Wav {
        if (rate == 1.0 || wav.frameCount == 0) return wav
        return resampleByRate(wav, rate.coerceAtLeast(0.01))
    }

    private fun resampleByRate(wav: Wav, rate: Double): Wav {
        val srcFrames = wav.frameCount
        val dstFrames = (srcFrames / rate).toInt().coerceAtLeast(1)
        val out = ShortArray(dstFrames * wav.channels)
        for (i in 0 until dstFrames) {
            val srcPos = i * rate
            val srcIndex = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val nextIndex = (srcIndex + 1).coerceAtMost(srcFrames - 1)
            val frac = srcPos - srcIndex
            for (c in 0 until wav.channels) {
                val a = wav.samples[srcIndex * wav.channels + c]
                val b = wav.samples[nextIndex * wav.channels + c]
                out[i * wav.channels + c] = (a + (b - a) * frac).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return wav.copy(samples = out)
    }

    private fun hannWindow(index: Int, length: Int): Double {
        if (length <= 1) return 1.0
        return 0.5 - 0.5 * cos(2.0 * PI * index / (length - 1))
    }
}

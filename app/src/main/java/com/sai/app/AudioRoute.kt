package com.sai.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

enum class Route(val label: String) {
    HEADPHONES("Headphones"),
    BLUETOOTH("Bluetooth"),
    SPEAKER("Speaker"),
    UNKNOWN("Audio"),
}

/** Detects the current audio output route (wired headphones, Bluetooth, or the phone's built-in speaker). */
object AudioRoute {

    fun current(context: Context): Route {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return Route.UNKNOWN
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        if (devices.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }) {
            return Route.HEADPHONES
        }
        if (devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }) {
            return Route.BLUETOOTH
        }
        if (devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }) {
            return Route.SPEAKER
        }
        return Route.UNKNOWN
    }

    /** Whether audio should be narrowed toward mono: tiny phone speakers comb-filter a wide stereo image. */
    fun shouldNarrowForSpeaker(context: Context): Boolean = current(context) == Route.SPEAKER
}

package com.sai.app

import com.sai.core.audio.Wav

/** In-memory clipboard shared across sample-editing sessions - Cut copies a selection here,
 *  Paste reads it back. Not persisted: it's a working-session clipboard, not project data. */
object SampleClipboard {
    var wav: Wav? = null
}

package com.sai.core.stem

import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO

data class StemSplitOutput(
    val kind: StemKind,
    val wav: Wav,
    val sourceFilename: String,
)

sealed class StemSplitPhase {
    data object Idle : StemSplitPhase()
    data object Uploading : StemSplitPhase()
    data class Processing(val message: String) : StemSplitPhase()
    data class Downloading(val stem: StemKind, val index: Int, val total: Int) : StemSplitPhase()
    data class Complete(val outputs: List<StemSplitOutput>) : StemSplitPhase()
    data class Failed(val message: String) : StemSplitPhase()
    data object Cancelled : StemSplitPhase()
}

/**
 * Orchestrates a single stem-split run against ComfyUI (or future cloud backends).
 */
class StemSplitJob(
    private val settings: StemSplitSettings,
    private val clientFactory: (StemSplitSettings) -> ComfyUiClient = { ComfyUiClient(it) },
) {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun run(
        input: Wav,
        sourceName: String,
        mode: StemSplitMode,
        requestedStems: Set<StemKind>,
        workflowJson: String,
        onPhase: (StemSplitPhase) -> Unit,
    ): StemSplitPhase {
        cancelled = false
        if (settings.backend == StemBackend.CLOUD) {
            val failed = StemSplitPhase.Failed(
                "Cloud stem splitting is not configured yet. Use ComfyUI on your PC (see Guide → Split stems).",
            )
            onPhase(failed)
            return failed
        }
        if (settings.comfyBaseUrl.isBlank()) {
            val failed = StemSplitPhase.Failed("Set your ComfyUI server URL in Stem Splitter settings.")
            onPhase(failed)
            return failed
        }

        val client = clientFactory(settings)
        val stems = requestedStems.ifEmpty { mode.defaultKinds() }

        onPhase(StemSplitPhase.Uploading)
        if (cancelled()) return finishCancelled(onPhase)

        val ping = client.ping()
        if (ping.isFailure) {
            return fail(
                onPhase,
                "Can't reach ComfyUI at ${settings.comfyBaseUrl.trim()}: ${ping.exceptionOrNull()?.message ?: "offline"}",
            )
        }

        val uploadName = sanitizeFilename("$sourceName-input.wav")
        val wavBytes = java.io.ByteArrayOutputStream().use { out ->
            WavIO.write(input, out)
            out.toByteArray()
        }
        val uploaded = client.uploadAudio(uploadName, wavBytes)
        if (uploaded.isFailure) {
            return fail(onPhase, "Upload failed: ${uploaded.exceptionOrNull()?.message}")
        }
        if (cancelled()) return finishCancelled(onPhase)

        val template = try {
            StemWorkflow.loadFromJson(workflowJson, settings.demucsModel, uploaded.getOrThrow().name)
        } catch (e: Exception) {
            return fail(onPhase, "Workflow error: ${e.message}")
        }

        onPhase(StemSplitPhase.Processing("Queueing on ComfyUI…"))
        val promptId = client.queuePrompt(template.workflow)
        if (promptId.isFailure) {
            return fail(onPhase, "Queue failed: ${promptId.exceptionOrNull()?.message}")
        }
        if (cancelled()) return finishCancelled(onPhase)

        val history = client.waitForCompletion(
            promptId = promptId.getOrThrow(),
            cancel = { cancelled() },
            onStatus = { message -> onPhase(StemSplitPhase.Processing(message)) },
        )
        if (history.isFailure) {
            val message = history.exceptionOrNull()?.message.orEmpty()
            if (message == "Cancelled") return finishCancelled(onPhase)
            return fail(onPhase, message.ifBlank { "ComfyUI job failed" })
        }
        if (cancelled()) return finishCancelled(onPhase)

        val mapped = try {
            StemWorkflow.outputFilesForStems(template, history.getOrThrow(), stems)
        } catch (e: Exception) {
            return fail(onPhase, e.message ?: "Missing stem outputs from ComfyUI")
        }

        val outputs = mutableListOf<StemSplitOutput>()
        mapped.entries.forEachIndexed { index, (kind, file) ->
            onPhase(StemSplitPhase.Downloading(kind, index + 1, mapped.size))
            if (cancelled()) return finishCancelled(onPhase)
            val bytes = client.downloadOutput(file)
            if (bytes.isFailure) {
                return fail(onPhase, "Download ${kind.label} failed: ${bytes.exceptionOrNull()?.message}")
            }
            val wav = try {
                WavIO.read(bytes.getOrThrow())
            } catch (e: Exception) {
                return fail(onPhase, "${kind.label} is not a valid WAV: ${e.message}")
            }
            outputs.add(StemSplitOutput(kind, wav, file.filename))
        }

        val complete = StemSplitPhase.Complete(outputs)
        onPhase(complete)
        return complete
    }

    private fun cancelled(): Boolean = cancelled

    private fun finishCancelled(onPhase: (StemSplitPhase) -> Unit): StemSplitPhase {
        val phase = StemSplitPhase.Cancelled
        onPhase(phase)
        return phase
    }

    private fun fail(onPhase: (StemSplitPhase) -> Unit, message: String): StemSplitPhase {
        val phase = StemSplitPhase.Failed(message)
        onPhase(phase)
        return phase
    }

    private fun sanitizeFilename(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "sai-input.wav" }

}

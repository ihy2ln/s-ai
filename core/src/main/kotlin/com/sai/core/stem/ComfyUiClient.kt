package com.sai.core.stem

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class ComfyUploadedFile(
    val name: String,
    val subfolder: String,
    val type: String,
)

data class ComfyOutputFile(
    val filename: String,
    val subfolder: String,
    val type: String,
)

/**
 * Minimal ComfyUI HTTP client for uploading audio, queueing workflows, and downloading outputs.
 * See docs/wiki/stem-splitter.md for the required custom nodes and example workflow.
 */
class ComfyUiClient(
    private val settings: StemSplitSettings,
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        (URL(url).openConnection() as HttpURLConnection)
    },
) {
    private val clientId = "sai-android-${UUID.randomUUID()}"

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "ComfyUI URL must start with http:// or https://"
        }
        return trimmed
    }

    fun ping(): Result<Unit> = runCatching {
        val base = requireBaseUrl()
        val conn = openGet("$base/system_stats")
        val code = conn.responseCode
        if (code !in 200..299) {
            error("ComfyUI returned HTTP $code")
        }
        conn.inputStream.use { it.readBytes() }
    }

    fun uploadAudio(filename: String, wavBytes: ByteArray): Result<ComfyUploadedFile> = runCatching {
        val base = requireBaseUrl()
        val boundary = "----SaiBoundary${UUID.randomUUID()}"
        val conn = connectionFactory("$base/upload/image").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            applyAuth(this)
            connectTimeout = settings.requestTimeoutMs
            readTimeout = settings.requestTimeoutMs
        }
        conn.outputStream.use { out ->
            writeMultipart(out, boundary, filename, wavBytes)
        }
        val body = readResponse(conn)
        if (conn.responseCode !in 200..299) {
            error(parseError(body, conn.responseCode))
        }
        val json = JSONObject(body)
        ComfyUploadedFile(
            name = json.getString("name"),
            subfolder = json.optString("subfolder", ""),
            type = json.optString("type", "input"),
        )
    }

    fun queuePrompt(workflow: JSONObject): Result<String> = runCatching {
        val base = requireBaseUrl()
        val payload = JSONObject()
            .put("prompt", workflow)
            .put("client_id", clientId)
        val conn = openPost("$base/prompt", payload.toString())
        val body = readResponse(conn)
        if (conn.responseCode !in 200..299) {
            error(parseError(body, conn.responseCode))
        }
        val json = JSONObject(body)
        json.getString("prompt_id")
    }

    fun waitForCompletion(
        promptId: String,
        cancel: () -> Boolean,
        onStatus: (String) -> Unit,
    ): Result<Map<String, List<ComfyOutputFile>>> = runCatching {
        val base = requireBaseUrl()
        val deadline = System.currentTimeMillis() + settings.maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            if (cancel()) error("Cancelled")
            val history = fetchHistory(base, promptId)
            if (history != null) {
                return@runCatching parseHistoryOutputs(history)
            }
            val queueStatus = fetchQueueStatus(base, promptId)
            onStatus(queueStatus ?: "Waiting for ComfyUI…")
            Thread.sleep(settings.pollIntervalMs)
        }
        error("Timed out waiting for ComfyUI (>${settings.maxWaitMs / 1000}s)")
    }

    fun downloadOutput(file: ComfyOutputFile): Result<ByteArray> = runCatching {
        val base = requireBaseUrl()
        val query = buildString {
            append("filename=").append(encode(file.filename))
            append("&subfolder=").append(encode(file.subfolder))
            append("&type=").append(encode(file.type))
        }
        val conn = openGet("$base/view?$query")
        val bytes = readResponseBytes(conn)
        if (conn.responseCode !in 200..299) {
            error(parseError(String(bytes), conn.responseCode))
        }
        bytes
    }

    private fun requireBaseUrl(): String {
        val url = settings.comfyBaseUrl.trim()
        require(url.isNotEmpty()) { "Set a ComfyUI server URL in Stem Splitter settings" }
        return normalizeBaseUrl(url)
    }

    private fun openGet(url: String): HttpURLConnection =
        connectionFactory(url).apply {
            requestMethod = "GET"
            applyAuth(this)
            connectTimeout = settings.requestTimeoutMs
            readTimeout = settings.requestTimeoutMs
        }

    private fun openPost(url: String, jsonBody: String): HttpURLConnection =
        connectionFactory(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            applyAuth(this)
            connectTimeout = settings.requestTimeoutMs
            readTimeout = settings.requestTimeoutMs
            outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        }

    private fun applyAuth(conn: HttpURLConnection) {
        val key = settings.comfyApiKey.trim()
        if (key.isNotEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer $key")
        }
    }

    private fun fetchHistory(base: String, promptId: String): JSONObject? {
        val conn = openGet("$base/history/$promptId")
        val body = readResponse(conn)
        if (conn.responseCode !in 200..299) return null
        val json = JSONObject(body)
        if (!json.has(promptId)) return null
        return json.getJSONObject(promptId)
    }

    private fun fetchQueueStatus(base: String, promptId: String): String? {
        val conn = openGet("$base/queue")
        val body = readResponse(conn)
        if (conn.responseCode !in 200..299) return null
        val json = JSONObject(body)
        val running = json.optJSONArray("queue_running") ?: JSONArray()
        for (i in 0 until running.length()) {
            val item = running.optJSONArray(i) ?: continue
            if (item.length() > 1 && item.optString(1) == promptId) {
                return "Processing on ComfyUI…"
            }
        }
        val pending = json.optJSONArray("queue_pending") ?: JSONArray()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONArray(i) ?: continue
            if (item.length() > 1 && item.optString(1) == promptId) {
                return "Queued on ComfyUI…"
            }
        }
        return null
    }

    private fun writeMultipart(out: OutputStream, boundary: String, filename: String, bytes: ByteArray) {
        val crlf = "\r\n"
        fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
        write("--$boundary$crlf")
        write("""Content-Disposition: form-data; name="image"; filename="$filename"$crlf""")
        write("Content-Type: audio/wav$crlf$crlf")
        out.write(bytes)
        write(crlf)
        write("--$boundary$crlf")
        write("""Content-Disposition: form-data; name="type"$crlf$crlf""")
        write("input$crlf")
        write("--$boundary$crlf")
        write("""Content-Disposition: form-data; name="overwrite"$crlf$crlf""")
        write("true$crlf")
        write("--$boundary--$crlf")
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
    }

    private fun readResponseBytes(conn: HttpURLConnection): ByteArray {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: ByteArray(0)
    }

    private fun parseError(body: String, code: Int): String {
        if (body.isBlank()) return "HTTP $code"
        return try {
            val json = JSONObject(body)
            json.optString("error", json.optString("message", body))
        } catch (_: Exception) {
            body.take(240)
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        fun parseHistoryOutputs(history: JSONObject): Map<String, List<ComfyOutputFile>> {
            val outputs = history.optJSONObject("outputs") ?: return emptyMap()
            val result = linkedMapOf<String, List<ComfyOutputFile>>()
            val keys = outputs.keys()
            while (keys.hasNext()) {
                val nodeId = keys.next()
                val node = outputs.optJSONObject(nodeId) ?: continue
                val files = mutableListOf<ComfyOutputFile>()
                collectFiles(node.optJSONArray("audio"), files)
                collectFiles(node.optJSONArray("images"), files)
                if (files.isNotEmpty()) result[nodeId] = files
            }
            return result
        }

        private fun collectFiles(array: JSONArray?, out: MutableList<ComfyOutputFile>) {
            if (array == null) return
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val filename = item.optString("filename")
                if (filename.isBlank()) continue
                out.add(
                    ComfyOutputFile(
                        filename = filename,
                        subfolder = item.optString("subfolder", ""),
                        type = item.optString("type", "output"),
                    ),
                )
            }
        }
    }
}

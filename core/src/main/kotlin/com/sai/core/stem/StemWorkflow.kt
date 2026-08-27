package com.sai.core.stem

import org.json.JSONObject

/**
 * Bundled ComfyUI workflow templates. Node IDs must match the JSON under assets/comfyui/.
 *
 * Required ComfyUI custom nodes (install on your PC — see docs/wiki/stem-splitter.md):
 * - ComfyUI-Demucs (DemucsSeparate + DemucsStem nodes) or equivalent SaveAudio outputs.
 */
object StemWorkflow {

    data class Template(
        val workflow: JSONObject,
        val outputByStem: Map<StemKind, String>,
    )

    fun loadFromJson(raw: String, model: String, uploadedFilename: String): Template {
        val root = JSONObject(raw)
        val meta = root.optJSONObject("__meta")
            ?: error("Workflow JSON is missing a __meta section (see docs/wiki/stem-splitter.md)")
        val outputByStem = parseOutputMap(meta)
        val prompt = root.getJSONObject("prompt")
        val patched = JSONObject(prompt.toString())
        patchLoadAudio(patched, uploadedFilename)
        patchModel(patched, model)
        return Template(patched, outputByStem)
    }

    fun outputFilesForStems(
        template: Template,
        historyOutputs: Map<String, List<ComfyOutputFile>>,
        requested: Set<StemKind>,
    ): Map<StemKind, ComfyOutputFile> {
        val result = linkedMapOf<StemKind, ComfyOutputFile>()
        for (kind in requested) {
            val nodeId = template.outputByStem[kind]
                ?: error("Workflow does not define an output for ${kind.label}")
            val files = historyOutputs[nodeId]
                ?: error("ComfyUI did not return output for ${kind.label} (node $nodeId)")
            val file = files.firstOrNull()
                ?: error("ComfyUI returned an empty file list for ${kind.label}")
            result[kind] = file
        }
        return result
    }

    private fun parseOutputMap(meta: JSONObject): Map<StemKind, String> {
        val outputs = meta.optJSONObject("outputs")
            ?: error("Workflow __meta.outputs is required")
        val map = linkedMapOf<StemKind, String>()
        for (kind in StemKind.entries) {
            val nodeId = outputs.optString(kind.name.lowercase(), "")
            if (nodeId.isNotBlank()) map[kind] = nodeId
        }
        if (map.isEmpty()) error("Workflow __meta.outputs has no stem mappings")
        return map
    }

    private fun patchLoadAudio(prompt: JSONObject, filename: String) {
        val keys = prompt.keys()
        while (keys.hasNext()) {
            val nodeId = keys.next()
            val node = prompt.optJSONObject(nodeId) ?: continue
            if (node.optString("class_type") != "LoadAudio") continue
            val inputs = node.optJSONObject("inputs") ?: continue
            inputs.put("audio", filename)
        }
    }

    private fun patchModel(prompt: JSONObject, model: String) {
        val keys = prompt.keys()
        while (keys.hasNext()) {
            val nodeId = keys.next()
            val node = prompt.optJSONObject(nodeId) ?: continue
            val classType = node.optString("class_type")
            if (classType != "DemucsSeparate" && classType != "DemucsSeparator") continue
            val inputs = node.optJSONObject("inputs") ?: continue
            if (inputs.has("model")) inputs.put("model", model)
        }
    }
}

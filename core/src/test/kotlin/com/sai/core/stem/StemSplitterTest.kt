package com.sai.core.stem

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StemWorkflowTest {

  private val sampleWorkflow = """
    {
      "__meta": {
        "outputs": {
          "vocals": "20",
          "drums": "21",
          "instrumental": "24"
        }
      },
      "prompt": {
        "1": {
          "class_type": "LoadAudio",
          "inputs": { "audio": "old.wav" }
        },
        "2": {
          "class_type": "DemucsSeparate",
          "inputs": { "audio": ["1", 0], "model": "htdemucs" }
        }
      }
    }
  """.trimIndent()

  @Test
  fun `patches load audio and model`() {
    val template = StemWorkflow.loadFromJson(sampleWorkflow, "mdx_extra", "uploaded.wav")
    val load = template.workflow.getJSONObject("1")
    assertEquals("uploaded.wav", load.getJSONObject("inputs").getString("audio"))
    val demucs = template.workflow.getJSONObject("2")
    assertEquals("mdx_extra", demucs.getJSONObject("inputs").getString("model"))
    assertEquals("20", template.outputByStem[StemKind.VOCALS])
  }

  @Test
  fun `maps history outputs to stems`() {
    val template = StemWorkflow.loadFromJson(sampleWorkflow, "htdemucs", "x.wav")
    val history = mapOf(
      "20" to listOf(ComfyOutputFile("vocals.wav", "", "output")),
      "21" to listOf(ComfyOutputFile("drums.wav", "", "output")),
    )
    val mapped = StemWorkflow.outputFilesForStems(
      template,
      history,
      setOf(StemKind.VOCALS, StemKind.DRUMS),
    )
    assertEquals("vocals.wav", mapped[StemKind.VOCALS]?.filename)
    assertEquals("drums.wav", mapped[StemKind.DRUMS]?.filename)
  }

  @Test
  fun `requires meta outputs`() {
    assertFailsWith<IllegalStateException> {
      StemWorkflow.loadFromJson("""{"prompt":{}}""", "htdemucs", "a.wav")
    }
  }
}

class ComfyUiClientTest {

  @Test
  fun `normalizes base url`() {
    val client = ComfyUiClient(StemSplitSettings(comfyBaseUrl = "http://192.168.1.10:8188/"))
    assertEquals("http://192.168.1.10:8188", client.normalizeBaseUrl("http://192.168.1.10:8188/"))
  }

  @Test
  fun `parses history audio outputs`() {
    val history = JSONObject(
      """
      {
        "outputs": {
          "20": {
            "audio": [
              { "filename": "sai_vocals_00001_.wav", "subfolder": "", "type": "output" }
            ]
          }
        }
      }
      """.trimIndent(),
    )
    val parsed = ComfyUiClient.parseHistoryOutputs(history)
    assertEquals(1, parsed.size)
    assertEquals("sai_vocals_00001_.wav", parsed["20"]?.first()?.filename)
  }

  @Test
  fun `rejects invalid url`() {
    val client = ComfyUiClient(StemSplitSettings())
    assertFailsWith<IllegalArgumentException> {
      client.normalizeBaseUrl("192.168.0.5:8188")
    }
  }
}

class StemSplitJobTest {

  private val workflow = """
    {
      "__meta": { "outputs": { "vocals": "20", "instrumental": "24" } },
      "prompt": {
        "1": { "class_type": "LoadAudio", "inputs": { "audio": "x.wav" } }
      }
    }
  """.trimIndent()

  @Test
  fun `fails when comfy url missing`() {
    val job = StemSplitJob(StemSplitSettings())
    val phase = job.run(
      input = com.sai.core.audio.Wav(44_100, 2, ShortArray(100)),
      sourceName = "loop",
      mode = StemSplitMode.TWO_STEM,
      requestedStems = setOf(StemKind.VOCALS, StemKind.INSTRUMENTAL),
      workflowJson = workflow,
      onPhase = {},
    )
    assertTrue(phase is StemSplitPhase.Failed)
    assertTrue((phase as StemSplitPhase.Failed).message.contains("ComfyUI server URL"))
  }

  @Test
  fun `cloud backend returns clear message`() {
    val job = StemSplitJob(StemSplitSettings(backend = StemBackend.CLOUD, comfyBaseUrl = "http://x"))
    val phase = job.run(
      input = com.sai.core.audio.Wav(44_100, 2, ShortArray(100)),
      sourceName = "loop",
      mode = StemSplitMode.TWO_STEM,
      requestedStems = emptySet(),
      workflowJson = workflow,
      onPhase = {},
    )
    assertTrue(phase is StemSplitPhase.Failed)
    assertTrue((phase as StemSplitPhase.Failed).message.contains("Cloud"))
  }
}

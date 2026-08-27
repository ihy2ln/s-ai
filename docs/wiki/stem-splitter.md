# Split stems (ComfyUI + Demucs)

S.Ai's stem splitter separates a mixed WAV into **vocals**, **drums**, **bass**, **other**, or a **2-stem** vocal/instrumental pair. Quality comes from **Demucs** running on **your PC** via [ComfyUI](https://github.com/comfyanonymous/ComfyUI) — the Android app uploads audio, queues a workflow, and saves returned WAVs to your library.

On-device separation was evaluated and rejected for this release: Demucs models are ~300MB+, inference is slow on mobile CPUs, and quality still trails a desktop GPU run.

## Quick start (phone)

1. **M → Split Stems** (or **Sampler → Split Stems** with a loaded sample, or long-press a sound in **Sounds**)
2. **⚙ Settings** → ComfyUI URL, e.g. `http://192.168.1.10:8188` → **Test** → **Save**
3. Pick **4-stem** or **2-stem**, select stems, tap **Split**
4. Stems land in your library as `source — Vocals`, etc. Enable **Also send stems to Channel Rack** to auto-assign rows.

## PC setup

### 1. Install ComfyUI

```bash
git clone https://github.com/comfyanonymous/ComfyUI
cd ComfyUI
pip install -r requirements.txt
```

### 2. Install Demucs custom nodes

S.Ai ships a workflow that expects **DemucsSeparate**, **DemucsStem**, and **SaveAudio** nodes. Install a ComfyUI-Demucs pack that provides those class names, for example:

```bash
cd ComfyUI/custom_nodes
git clone https://github.com/christopher-bedford/ComfyUI-Demucs comfyui-demucs
cd comfyui-demucs && pip install -r requirements.txt
```

If your pack uses different `class_type` names, edit `app/src/main/assets/comfyui/demucs-4stem.json` (or replace it with your own workflow — keep the `__meta.outputs` stem → node map).

### 3. Listen on your LAN

```bash
python main.py --listen 0.0.0.0 --port 8188
```

Find your PC's LAN IP (`ipconfig` / `ip addr`). Phone and PC must be on the same Wi‑Fi.

### 4. Test from the phone

Open **Split Stems → ⚙ → Test**. If it fails:

- Firewall: allow TCP 8188 on the PC
- URL must include `http://` and port
- Try opening `http://<pc-ip>:8188` in the phone browser

## Bundled workflow

The app loads `assets/comfyui/demucs-4stem.json`. A copy lives at `docs/comfyui/demucs-4stem.json`.

`__meta.outputs` maps stem names to ComfyUI node IDs that write `SaveAudio` files:

| Stem | Node ID |
| --- | --- |
| vocals | 20 |
| drums | 21 |
| bass | 22 |
| other | 23 |
| instrumental | 24 |

2-stem mode requests **vocals** + **instrumental** only.

## API flow (for debugging)

1. `POST /upload/image` — multipart WAV upload to ComfyUI input folder
2. `POST /prompt` — queue patched workflow JSON
3. Poll `GET /history/{prompt_id}` until outputs appear
4. `GET /view?filename=…&type=output` — download each stem WAV

Optional `Authorization: Bearer …` if you front ComfyUI with a proxy.

## Cloud backend

Settings include a **Cloud** backend placeholder (Replicate / HuggingFace). It is **not wired** in this release — use ComfyUI for real separation.

## Limitations vs Serato / FL / Logic

| Feature | Desktop DAWs | S.Ai |
| --- | --- | --- |
| Latency | Local, seconds | Network + PC GPU; depends on Wi‑Fi |
| Quality | Tuned product UX | Demucs quality (very good, not magic) |
| Real-time preview | Often yes | No — batch job with progress UI |
| Offline | Yes | Needs reachable ComfyUI (or future cloud) |
| Model size in app | N/A | None bundled |

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Can't reach ComfyUI | Check URL, firewall, same Wi‑Fi |
| Queue failed / node not found | Install Demucs custom nodes; verify `class_type` names |
| Missing stem output | Workflow `__meta.outputs` must match SaveAudio node IDs |
| Cancelled but PC still busy | ComfyUI keeps running; wait or clear its queue |
| Invalid WAV | ComfyUI must export 16-bit PCM WAV |

## Files in this repo

- `core/.../stem/` — ComfyUI client, workflow patcher, job state machine
- `app/.../StemSplitterActivity.kt` — UI
- `app/src/main/assets/comfyui/demucs-4stem.json` — default workflow

# S.Ai User Manual

Welcome to **S.Ai** — a landscape-first sampler and tracker for Android. This manual covers every screen, control, and workflow in the app.

## Table of Contents

- [Getting Started](#getting-started)
- [Transport Bar](#transport-bar)
- [Menu Buttons (E / N / MX / M)](#menu-buttons-e--n--mx--m)
- [Home Modules](#home-modules)
- [Sampler Module](#sampler-module)
- [Synth Module](#synth-module)
- [Tracker Module](#tracker-module)
- [Channel Rack](#channel-rack)
- [Phrase Editor](#phrase-editor)
- [Piano Roll](#piano-roll)
- [Sample Editor](#sample-editor)
- [Effects (MX)](#effects-mx)
- [Sound Library](#sound-library)
- [Project Save & Load](#project-save--load)
- [Theme & Appearance](#theme--appearance)
- [Live Recording](#live-recording)
- [Tips & Shortcuts](#tips--shortcuts)

## Getting Started

S.Ai opens in **landscape orientation** and stays there for the best module layout.

**Quick start:**

- Import audio via **M → Samples** or any module **+** button
- Tap a sample in the list to load it into the Sampler
- Tap slice pads to preview sounds
- Press the green **Play** triangle to hear your song
- Use the **Channel Rack** to paint step patterns FL-style

S.Ai ships **no bundled sounds** — bring your own audio files.

## Transport Bar

The transport bar runs across the top of the Home screen.

| Control | Icon | Action |
| --- | --- | --- |
| **Project title** | Text (marquee) | Tap to rename the project |
| **BPM** | `BPM 120` label | Tap to type a value (20–300); drag vertically to scrub |
| **Tempo dot** | Blue dot | Pulses on downbeats during playback |
| **Play / Stop** | Green triangle / white square | Start or stop song playback |
| **Record** | Red circle | Arm live punch-in recording |
| **Edit** | Blue square | Project menu: rename, save, load, undo, redo |
| **Tempo (Tap)** | Yellow **T** in yellow circle on black | Tap repeatedly to set BPM from your taps |
| **Route** | 🎧 📶 🔊 | Shows current audio output (headphones, Bluetooth, speaker) |

When you tap a transport shape button, its label appears for **one second** then fades out.

### Tap Tempo

Tap the **T** button at least twice in rhythm. S.Ai averages the last 2–5 taps. If you pause for more than 2 seconds, the tap sequence resets.

## Menu Buttons (E / N / MX / M)

Four pill buttons sit at the end of the transport row:

| Button | Name | Opens |
| --- | --- | --- |
| **E** | Expand | Full-screen the current module, or return to Split View |
| **N** | Navigate | Back / navigation menu (long-press anywhere on Home also opens this) |
| **MX** | Mixer / Effects | Effects chain on the loaded sampler sound |
| **M** | Menu | App menu including **Manual**, samples, theme, modules, project tools |

## Home Modules

The Home screen stacks up to four modules. Each module has:

- **▲ ▼** — move module up or down
- **−** — remove module from screen (data is kept)
- **MONO / POLY** — Cut Itself (choke) toggle for preview playback

Drag the **divider line** under a module up or down to resize it. Drag down to expand the module above the line; drag up to shrink it. If neighboring modules are already at their minimum size, the layout grows and you can scroll. There is a divider under every module, including the last one.

| Module | Purpose |
| --- | --- |
| **SAMPLER** | Waveform view, slice pads, record audio |
| **SYNTH** | Filter and waveform generator on loaded audio |
| **TRACKER** | 32-position song grid with 8 tracks |
| **CHANNEL RACK** | FL-style step sequencer with mute, vol, pan, mixer route |

Add removed modules back via **M → Add Module**.

## Sampler Module

### Module toolbar

- **Record Audio** — capture from microphone (requires permission)
- **+** — import audio file
- **MONO/POLY** — when MONO, playing a new slice stops the previous preview

### Waveform & slices

- Adjust slice count with **−** and **+** (1–16 equal divisions)
- Colored pads **00–0F** preview each slice
- **Save Slices** exports slices to your sample library as separate WAV files

### Sample list

Below the modules, your library entries appear as tappable rows.

- **Tap** — load into Sampler
- **Tap while Record armed + playing** — punch sample into Tracker track 1 at current step
- **Long-press → Edit** — open Sample Editor
- **Long-press → Mixer** — open Effects on that sample

## Synth Module

Load a sample or generate a waveform:

| Button | Waveform |
| --- | --- |
| Circle | Sine |
| Saw | Sawtooth |
| Triangle | Triangle |
| Square | Square |

Six knobs shape the sound:

- **LOW CUT / HIGH CUT** — band limits
- **CUTOFF / RES / CRUNCH** — filter character
- **PITCH** — ±24 semitones

**Apply** bakes the filter into the WAV. **Save to Library** stores the result under the Synth category.

## Tracker Module

The tracker is a classic hex-style song arrangement grid.

- **32 rows** — song positions `00`–`1F`
- **8 columns** — tracks `1`–`8`
- Each cell holds a **phrase ID** (hex) or `--` if empty
- The active row highlights during playback
- Status line shows **PP:S** (position and step)

### Phrase slots

| Cell state | Tap action |
| --- | --- |
| Empty | **New Phrase** or **Assign Existing #** |
| Filled | **Edit** (Phrase Editor) or **Clear** |

Phrases contain 16 steps with note, instrument, and volume data.

## Channel Rack

Inspired by FL Studio's Channel Rack. Each row is one channel:

| Control | Function |
| --- | --- |
| **Mute LED** | Green = audible; tap to mute |
| **Vol knob** | Channel volume |
| **Pan knob** | Stereo pan (0.5 = center) |
| **Mixer track** | Tap to cycle route `---` through `1`–`9` |
| **Channel button** | Tap to assign a sample; red = unassigned |
| **Step grid** | 16 steps; tap or drag to paint on/off |

### Toolbar

- **< / >** — change song pattern (position 0–31)
- **...** — Mute all, Unmute all, Delete empty channels
- **− / +** — zoom row height
- **+ Add channel** — show more rows (up to 8)

Open full-screen via **MX → Channel Rack**.

## Phrase Editor

Open from a filled tracker cell → **Edit**.

Split layout with **E (Expand)**:

- **Split View** — Sampler on top, step grid below
- **Sampler Full Screen**
- **Steps Full Screen**

### Step grid columns

| Column | Meaning |
| --- | --- |
| **NOTE** | MIDI note 0–127 |
| **INS** | Instrument index from library |
| **VOL** | Volume 0–127 |

Tap any cell value to edit it. **M → Piano Roll** opens the note-based editor.

## Piano Roll

Vertical piano keys (C3–C5) with 16 horizontal step columns.

- Pick an instrument first
- Tap or drag on a row to place a note at that step
- Only one note per step (placing a note clears others on that step)

## Sample Editor

Open via long-press on a library sample → **Edit**.

### Trim & edit

- **Start / End** sliders define the selection
- **Cut** — copy selection to clipboard and remove
- **Paste** — insert clipboard at start
- **Gain** — ±24 dB
- **Reverse** / **Normalize** checkboxes

### Warp

- **Tempo** — 50–200% time stretch
- **Pitch** — ±24 semitones
- **Granulate** — grain size and scatter
- **Source BPM + Sync** — match project tempo

**Play** previews the chain. **Save** exports a WAV file.

## Effects (MX)

Available from **MX**, sample long-press → Mixer, or Sound Library long-press.

| Effect | Controls |
| --- | --- |
| **Synth (Filter)** | LOW/HIGH CUT, CUTOFF, RES, CRUNCH, PITCH |
| **Compressor** | THRES, RATIO, ATT, REL, GAIN |
| **Reverb** | SIZE, DAMP, MIX |
| **Equalizer** | LOW CUT, 7 bands, MID CUT, HIGH CUT |
| **Stereo Shaper** | PAN, WIDTH, DEPTH |
| **Channel Rack** | Opens full-screen Channel Rack |

Each dialog has **Preview** (one-shot), **Apply** (writes back), and **Close**.

## Sound Library

**M → Sounds** opens the categorized browser.

Categories: Kicks, Snares, Hats, Percussion, Vocals, SFX, Synth, Samples.

- **Tap** — preview
- **Long-press → Move to Category**
- **Long-press → Mixer** — effects

## Project Save & Load

Your project **auto-saves** continuously (BPM, name, song, phrases, undo stack).

Manual export/import:

- **Edit → Save** or **M → Save Project** — JSON file
- **Edit → Load** or **M → Load Project** — replace current project
- **M → New Project** — clear song and phrases (library untouched)

**Undo / Redo** is available from Edit menu, M menu, and Phrase Editor menu.

## Theme & Appearance

**M → Theme** (also in Phrase Editor):

- **Color wheel** — pick a color
- **Set as Background** — solid color layer
- **Set as Accent Color** — titles, knobs, highlights
- **Choose Picture / Video** — custom background media
- **Mirror** — flip background horizontally
- **Background Opacity** — dark scrim over media (0–100%)
- **Window / Button Opacity** — panel and pill transparency
- **Reset to Default**

## Live Recording

1. Tap the **red Record circle** to arm
2. Start **Play**
3. Tap any sample in the list — it plays immediately and writes to **Tracker track 1** at the current step

Disarm Record when finished.

## Tips & Shortcuts

- **Long-press Home screen** — open Navigate (N) menu
- **Drag BPM label vertically** — scrub tempo without opening a dialog
- **E button** — quickly full-screen any module for focused editing
- **MONO mode** — use on Sampler/Synth when you want choke-style one-shot playback
- **Channel Rack + Tracker** share the same song data and playback engine

---

*S.Ai — workflow-first mobile music production. No bundled samples; your sounds, your songs.*

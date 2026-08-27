# S.Ai User Manual

Welcome to **S.Ai** — a landscape-first sampler and tracker for Android. This page is the same wiki that opens in the app via **M → Manual** or **N → Manual**.

## Table of Contents

- [Getting Started](#getting-started)
- [Transport Bar](#transport-bar)
- [Menu Buttons (E / N / MX / P / M)](#menu-buttons-e--n--mx--p--m)
- [Home Modules](#home-modules)
- [Sampler Module](#sampler-module)
- [Synth Module](#synth-module)
- [Tracker Module](#tracker-module)
- [Channel Rack](#channel-rack)
- [Mixer](#mixer)
- [Playlist](#playlist)
- [Phrase Editor](#phrase-editor)
- [Piano Roll](#piano-roll)
- [Pad Bank](#pad-bank)
- [Sample Editor](#sample-editor)
- [Effects (MX)](#effects-mx)
- [Sound Library](#sound-library)
- [Project Save & Load](#project-save--load)
- [Theme & Appearance](#theme--appearance)
- [Live Recording](#live-recording)
- [How-tos](#how-tos)
- [Glossary](#glossary)
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

Open this wiki any time with **M → Manual**. On the left, tap a section to jump. Type in the search box to highlight matches; press search on the keyboard for the next hit.

See also: [How-tos](#how-tos), [Glossary](#glossary).

## Transport Bar

The transport bar runs across the top of the Home screen.

| Control | Icon | Action |
| --- | --- | --- |
| **Project title** | Text (marquee) | Tap to rename the project |
| **BPM** | `BPM 120` label | Tap to type a value (20–300); drag vertically to scrub |
| **Tempo dot** | Blue dot | Pulses on downbeats during playback |
| **Play / Stop** | Green triangle / white square | Start or stop song playback |
| **Record** | Red circle | Arm live punch-in recording |
| **Sound** | Blue square | Project sound: global pitch and master volume |
| **Tempo (Tap)** | Yellow **T** in yellow circle on black | Tap repeatedly to set BPM from your taps |
| **CLK** | Text | Metronome clicks on the beat during playback (not in WAV export) |
| **IN** | Text | One-bar count-in of clicks before the song starts |
| **LOOP** | `SONG` / `PAT` / `RNG` | Loop the whole song, the current Channel Rack pattern, or a selected tracker row range |
| **Route** | 🎧 📶 🔊 | Shows current audio output (headphones, Bluetooth, speaker) |

When you tap a transport shape button, its label appears for **one second** then fades out.

### Tap Tempo

Tap the **T** button at least twice in rhythm. S.Ai averages the last 2–5 taps. If you pause for more than 2 seconds, the tap sequence resets.

## Menu Buttons (E / N / MX / P / M)

Four pill buttons sit at the end of the transport row:

| Button | Name | Opens |
| --- | --- | --- |
| **E** | Expand | Full-screen the current module, or return to Split View |
| **N** | Navigate | Home, Phrase, Piano Roll, Sample Editor, Sounds, Channel Rack, Mixer, Playlist, Manual (long-press Home also opens this) |
| **MX** | Mixer / Effects | Mixer first, then effects on the loaded sampler sound |
| **P** | Project | Rename, save/load project package, new, undo, redo, export stereo WAV mixdown, export stems |
| **M** | Menu | Manual, samples, sounds, theme, **Layout**, add module. **Plugins** is hidden until a plugin is registered. |

### Project Sound (blue square)

Tap the blue square in the transport bar to open **Project Sound**:

| Control | Range | Effect |
| --- | --- | --- |
| **PITCH** | ±24 semitones | Transposes all song playback |
| **MASTER** | 0–127 | Overall mix level for playback |
| **BUF** | 1–4× | AudioTrack buffer multiplier |
| **LAT** | 0–200 ms | Skip this many milliseconds at the start of a mic recording (compensation) |
| **VOX** | off/on | Light vocal FX (high-pass + compressor) on recorded takes |

These settings apply to tracker/sequencer playback and are saved with your project.

## Home Modules

The Home screen holds your working modules. **M → Layout** picks how they sit on screen:

| Layout | How it works |
| --- | --- |
| **Stack** | The original stacked view, using the same filled-title chrome as fullscreen. Drag the grey divider under a module to resize it. ▲ ▼ reorder; **−** removes (data is kept). |
| **Focus** | The working module fills the screen under the menu. Other modules show as **names in a strip** just under the transport. **Tap a name** to switch. |
| **Boxes** | Each module is a floating card. **Tap the title** to grow it, **drag the title** to move, **drag the bottom-right corner** to resize. |

**M → Layout → Reset** restores the default modules (Sampler, Synth, Tracker, Channel Rack), default heights, and box positions. It stays on the layout mode you are in.

Adding, removing, reordering, or changing layout **keeps** the Sampler/Synth sound already loaded and the Channel Rack pattern you were editing.

**Scrolling the page** in Stack (one-finger drags stay with knobs and pads):
- Drag the **thin bar on the far right edge** of the screen
- Or swipe **up or down with two fingers**

Each module header is compact: title, Rec/+, MONO/POLY, and layout controls.

| Module | Purpose |
| --- | --- |
| **SAMPLER** | Waveform view, slice pads, record audio |
| **SYNTH** | Filter, ADSR, waveform generator, live keyboard |
| **PADS** | 4×4 sample pad bank (add via **M → Add Module**) |
| **TRACKER** | 32-position song grid with 8 tracks |
| **CHANNEL RACK** | Step sequencer with mute, solo, vol, pan, mixer route, pattern length, swing |

Add removed modules back via **M → Add Module**.

See also: [Sampler Module](#sampler-module), [Channel Rack](#channel-rack), [Pad Bank](#pad-bank).

## Sampler Module

### Module toolbar

- **Record Audio** — capture from microphone (requires permission)
- **+** — import audio file
- **MONO/POLY** — when MONO, playing a new slice stops the previous preview

### Waveform & slices

- Adjust slice count with **−** and **+** (1–16 equal divisions)
- Colored pads **00–0F** preview each slice
- **Save Slices** exports slices to your sample library as separate WAV files
- **To Rack** saves those slices **and** assigns them to Channel Rack rows (empty rows first, then new rows up to 8). Extra slices stay in the library.
- **Warp BPM** time-stretches the loaded sample so its original tempo matches the project BPM.

### Sample list

Below the modules, your library entries appear as tappable rows.

- **Tap** — load into Sampler
- **Tap while Record armed + playing** — punch sample into the armed Tracker track at the current step
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

Tapping a waveform button loads that shape **and plays it**.

### Playing the sound

**Tap the wave display** to hear the current sound with your knob settings applied. Use it like an instrument pad while you dial in the filter.

Six knobs shape the sound, then four envelope knobs:

- **LOW CUT / HIGH CUT** — band limits
- **CUTOFF / RES / CRUNCH** — filter character
- **PITCH** — ±24 semitones
- **ATK / DEC / SUS / REL** — amplitude envelope (applied to preview, live keys, and **Apply**)

The **keyboard** plays the current sound at each pitch. **POLY** stacks notes; **MONO** cuts the previous note. Use **− / +** to change octave.

### Buttons

| Button | Action |
| --- | --- |
| **Preview** | Play the current sound (same as tapping the wave) |
| **Apply** | Bake the filter into the loaded WAV |
| **Add as Sample** | Save to the library, then choose a module to place it in |
| **Save to Library** | Save under the Synth category without placing it |

**Add as Sample** asks where the new sound should go:

- **Sampler** — loads it into the Sampler panel, ready to slice
- **Channel Rack** — pick a channel and it becomes that channel's instrument
- **Save only** — keep it in the library for later

Only modules currently on screen are offered.

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

Phrases store up to **32 steps** (older 16-step projects are padded). Playback uses each pattern's length (8, 16, or 32).

Long-press a row number to set a **loop range** (`Loop this row`, `Set loop start`, `Set loop end`). Transport **LOOP** shows `RNG` when a range is active. The looped rows get a faint green tint.

## Channel Rack

Each row is one channel:

| Control | Function |
| --- | --- |
| **Mute LED** | Green = audible; tap to mute that channel in playback |
| **Solo LED** | Yellow = soloed; if any row is soloed, only soloed rows play (mute still silences a soloed row) |
| **Vol knob** | Channel volume (applied on top of step volume during playback) |
| **Pan knob** | Stereo pan (0.5 = center; applied during playback) |
| **Mixer track** | Tap to cycle route `---` (master only) through mixer inserts `1`–`8` |
| **Channel button** | Tap to assign a sample; red = unassigned |
| **Step grid** | Square steps for this pattern's length; tap or drag to paint on/off |

### Toolbar

- **< / >** — change song pattern (position 0–31)
- **N steps** — tap to cycle this pattern's length **8 / 16 / 32**
- **Swing** — delay offbeat 16ths (0 = straight, 50 = shuffle, 100 = halfway to the next even step). Applied in playback and WAV mixdown
- **Loop** — cycle song / this pattern / selected tracker rows
- **...** — Duplicate pattern, Mute all, Unmute all, Solo none, Delete empty channels
- **− / +** — zoom row height
- **+ Add channel** — show more rows (up to 8)

**Duplicate pattern** copies this pattern's phrases into the next empty song row (or the following row if none are empty) so you can edit the copy without changing the original.

Open full-screen via **N → Channel Rack**. If the Channel Rack module isn't on Home, **E** also offers it.

During playback, the **current step square lights up** — yellow if that step is on, teal if it is empty — so you can see which note is playing. There is no extra bar of rectangles above the grid.

See also: [Tracker Module](#tracker-module), [Mixer](#mixer), [Phrase Editor](#phrase-editor).

## Mixer

**MX → Mixer**, **N → Mixer**, or the Mixer screen's **Export WAV** button.

Eight insert strips plus **MST** (master):

| Control | Function |
| --- | --- |
| **FX** | Live insert: Filter, Compressor, Reverb, Equalizer, or Stereo Shaper (or Off). Lit when active. Bypass keeps the slot without processing. |
| **Meter** | Peak from sounds routed to this strip |
| **Fader** | Strip (or master) level |
| **M** | Mute |
| **S** | Solo (inserts only; soloing one strip silences the others and unassigned channels) |

Channel Rack **Trk** `1`–`8` routes that row into the matching insert. `---` sums straight to the master bus (master insert still applies). Strip FX is heard on **Play** and in **Export WAV**; it does **not** rewrite the library sample. MX Filter/Compressor/… **Apply** still bakes into the sound.

**Stems** (mixer **Stems** or **P → Export Stems**) writes a zip with `trk-1.wav`…`trk-8.wav` plus `audio.wav` for playlist audio clips.

See also: [Effects (MX)](#effects-mx), [Channel Rack](#channel-rack), [How-tos](#how-tos).

## Playlist

**N → Playlist**. Empty playlist: playback walks the tracker song grid `00`–`1F` as usual.

- **+ PAT** — drop a pattern clip (song position) onto the timeline
- **+ AUD** — drop a library sample as an audio clip
- Tap a lane to add at the end of the timeline
- Tap a clip to mute, nudge, change length/lane, or delete
- **Clear** — remove clips and return to song-grid playback

Pattern clips **replace** sequential song walk while any unmuted pattern clip exists (overlapping clips layer). Audio clips always mix in at their start step. Playlist is stored in the project package.

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
| **INS** | Stable instrument ID from the library (not a sorted-list index) |
| **VOL** | Velocity 0–127 |
| **LEN** | Gate length in 16ths, or full sample |

Tap any cell value to edit it. **LEN** is gate length in 16th notes (`fl` = play the sample to the end). **M → Piano Roll** opens the note-based editor. The grid lists all 32 stored steps; playback only uses this pattern's length (8 / 16 / 32).

## Piano Roll

Vertical piano keys (two octaves at a time) with up to 32 horizontal step columns.

- **−8va / +8va** — move the visible note range
- **Vel** — velocity written onto new notes (50 / 80 / 100 / 127)
- **Len** — gate length for new notes (`full` plays the sample out, or 1 / 2 / 4 / 8 sixteenths)
- Pick an instrument first
- Tap or drag on a row to place a note at that step
- Longer notes light the cells they cover
- Only one note per step (placing a note clears others on that step)

## Pad Bank

Add via **M → Add Module → PADS**.

- 16 pads in a 4×4 grid
- **Tap** plays the assigned sample (**MONO** chokes the previous pad; **POLY** layers) and a short haptic
- **Long-press** assigns (or clears) a library sound
- Empty pads prompt you to pick a sample
- While Record is armed and the song is playing, a pad punch-in writes to the armed Tracker track like the sample list

Pad assignments are stored with the project package.

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

**Play** previews the chain. **Save** writes the edit back onto the same library sound (same ID). **Export** writes a new WAV file via the system picker. **Undo / Redo** restore Cut, Paste, and baked BPM warp. **Sync to Project BPM** time-stretches the tape to the project tempo.

## Effects (MX)

**MX → Mixer** opens the mixer. The other MX items, sample long-press → Mixer, and Sound Library long-press → Mixer open the effects chain.

| Effect | Controls |
| --- | --- |
| **Synth (Filter)** | LOW/HIGH CUT, CUTOFF, RES, CRUNCH, PITCH |
| **Compressor** | THRES, RATIO, ATT, REL, GAIN |
| **Reverb** | SIZE, DAMP, MIX |
| **Equalizer** | LOW CUT, 7 bands, MID CUT, HIGH CUT |
| **Stereo Shaper** | PAN, WIDTH, DEPTH |

Each dialog has **Preview** (one-shot), **Apply** (writes back), and **Close**. Apply on a library sound **replaces that entry** (same ID); it does not add a second copy.

For FX you want on a mixer strip (or master) without changing the sample, use the mixer **FX** button instead.

See also: [Mixer](#mixer), [Synth Module](#synth-module).

## Sound Library

**M → Sounds** opens the categorized browser.

Categories: Kicks, Snares, Hats, Percussion, Vocals, SFX, Synth, Samples.

- **Tap** — preview
- **Long-press → Move to Category**
- **Long-press → Mixer** — effects
- **Long-press → Tags** — free-text tags (searchable)
- Search box filters name, category, and tags

## Project Save & Load

Your project **auto-saves** continuously (BPM, name, song, phrases, undo stack).

Manual export/import via **P → Save** / **P → Load**:

- **Save** writes a **project package** (`.sai.zip`) with the song, sample library audio, Channel Rack, mixer, module layout, theme, pad bank, and playlist
- **Load** accepts that package, or an older JSON-only song file
- **P → Export WAV** — stereo mixdown of the song through the mixer (includes playlist)
- **P → Export Stems** — zip of per-track WAVs plus playlist audio
- **P → New** — pick Empty, 16-step grid, 32-step grid, or Beat (places an empty phrase on song row 00)

**Undo / Redo** is available from **P → Project** on Home and Phrase Editor.

## Theme & Appearance

**M → Theme** (also in Phrase Editor):

S.Ai's default look is a dark navy workspace with a sparse teal accent (FL Mobile-style). You can still override everything:

- **Color wheel** — pick a color
- **Set as Background** — solid color layer
- **Set as Accent Color** — titles, knobs, highlights
- **Choose Picture / Video** — custom background media
- **Mirror** — flip background horizontally
- **Background Opacity** — dark scrim over media (0–100%)
- **Window / Button Opacity** — panel and pill transparency
- **Reset to Default** — navy canvas + teal accent

## Live Recording

1. Tap the **red Record circle**
2. Choose **Punch track 1–8** or **Playlist tape (mic)**
3. For punch: start **Play**, then tap samples (or pads) to write hits at the current step
4. For tape: start **Play** — the mic records until you stop Play or disarm Record. The take is saved as a Vocal library sound and dropped on playlist lane 8 at the start step. **LAT** and **VOX** in Project Sound apply.

Sampler **Record Audio** still loads a take into the Sampler (also using LAT / VOX).

Disarm Record when finished.

See also: [Playlist](#playlist), [Project Save & Load](#project-save--load).

## How-tos

### Chop a sample into a beat

1. **M → Samples** and pick a WAV (or **Sampler → +**)
2. Set slice count, preview pads `00`–`0F`
3. **To Rack** — slices land on Channel Rack rows
4. Paint steps, tap **Play**
5. Route rows with **Trk** `1`–`8` if you want mixer FX

### Put reverb on a rack row without baking the sample

1. On the Channel Rack, tap **Trk** until the row shows the insert number you want
2. **N → Mixer** (or **MX → Mixer**)
3. Tap **FX** on that strip → **Reverb** → set SIZE / DAMP / MIX → **Set**
4. Press **Play** — the insert is live. Export WAV uses it too.

### Record a vocal onto the playlist

1. Arm the red **Record** circle → **Playlist tape (mic)**
2. Press **Play**. Sing. Stop Play or disarm Record
3. The take is a Vocal library sound on playlist lane 8
4. **LAT** / **VOX** in Project Sound (blue square) apply to the take

### Export a stereo mix vs stems

- **P → Export WAV** or mixer **Export WAV** — one stereo file through rack + mixer + playlist
- **P → Export Stems** or mixer **Stems** — zip of eight tracker tracks plus `audio.wav`

### Keep a project portable

**P → Save** writes a `.sai.zip` with audio, rack, mixer (including live FX), pads, playlist, and layout. **P → Load** reads that package, or an older JSON-only song.

## Glossary

| Term | Meaning |
| --- | --- |
| **Phrase** | A pattern of steps (note, instrument, velocity, gate) referenced from the tracker grid |
| **Channel Rack** | FL-style step rows that share the tracker song; mute / solo / vol / pan / mixer route |
| **Insert / Trk** | Mixer strip `1`–`8`. Rack **Trk** sends that row into the strip. `---` is master only |
| **Live FX** | Mixer **FX** slot heard on play and mixdown; does not rewrite the library sample |
| **MX Apply** | Bakes Filter / Comp / Reverb / EQ / Stereo into the current sample |
| **Choke / MONO** | New hit on the same group cuts the previous one |
| **Playlist** | Timeline of pattern clips and audio clips; empty playlist walks the tracker song |
| **Punch** | Record-armed play: tapping a sample or pad writes a step on the armed track |
| **Tape** | Mic recording dropped on the playlist as an audio clip |
| **Layout** | Stack, Focus, or Boxes home arrangement; **Reset** restores the default modules and sizes |

See also: [Tips & Shortcuts](#tips--shortcuts).

## Tips & Shortcuts

- **Long-press Home screen** — open Navigate (N) menu
- **Drag BPM label vertically** — scrub tempo without opening a dialog
- **E button** — quickly full-screen any module; other modules sit as names under the menu
- **MONO mode** — use on Sampler/Synth when you want choke-style one-shot playback
- **Channel Rack + Tracker** share the same song data and playback engine
- **Channel Rack Trk** numbers are mixer inserts 1–8
- Mixer **FX** is live (play + mixdown); MX **Apply** rewrites the sample
- **M → Manual** is this wiki: section list on the left, search at the top
- **M → Layout** — Stack, Focus (tap a name under the menu to switch), or Boxes (move / grow / resize cards). **Reset** restores the default arrangement.

---

*S.Ai — workflow-first mobile music production. No bundled samples; your sounds, your songs.*

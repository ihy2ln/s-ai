# Channel Rack vs mixer inserts

Two different places. Mixing them up is the usual snag.

| Place | Holds | How you get there |
| --- | --- | --- |
| **Channel Rack** | Instrument *rows* (steps, vol, pan, mute) | Instrument tile → **Add to Channel Rack**, or Rack **Plug** |
| **Mixer inserts** | Live *effects* on a strip or Master | Effect tile → **Insert on Mixer**, or strip **FX** |
| **Home** | Playable / editor modules | Tile → **Add to Home** |

## Instruments → Rack (or Home)

Tap an instrument tile:

- **Add to Channel Rack** — renders a note into the library and parks it on an empty row. Paint steps like any other sample.
- **Add to Home** — a module with knobs and a keyboard, same chrome as Synth.

Channel Rack **Plug** (and **… → Add plugin**) opens the same browser, already on Instruments.

## Effects → mixer (live)

Tap an effect tile:

- **Insert on Mixer** — pick strip `1`–`8` or **Master**, set knobs, **Set**
- **Add to Home** — Delay / Distort / Chorus / Limiter have compact editors; **Insert on Mixer** from the panel copies those knobs onto a strip

Mixer **FX** opens the same browser (includes **Off** to clear the slot).

The insert is **live** on play and WAV export. It does **not** rewrite the library sample. (MX **Apply** still bakes FX into a sample if you want that.)

## Rack rows meet mixer strips

On a Channel Rack row, **Trk** `1`–`8` sends that row into mixer insert `1`–`8`. Put Reverb on insert 3, then set the vocal row’s **Trk** to `3`.

`---` on **Trk** goes to master only.

## See also

- [Instruments vs Effects vs Home](instruments-effects-home.md)
- [Add a module](add-modules.md)
- [Start here](index.md)

# Not a .vst3 host

> S.Ai is **not** a native VST host. It cannot load `.vst3`, `.dll`, `.so`, or `.vst` binaries from disk.

**VST2** and **VST3** on a tile are *catalog labels* — in-app instruments and effects that share the same audio engine as Sampler and Synth. They exist so the browser feels like FL Mobile / BandLab, not so desktop plugins can be installed.

## What you can do

- Add catalog instruments to Home or the [Channel Rack](rack-vs-mixer.md)
- Insert catalog effects on mixer strips
- Enable / disable catalog entries in **M → Plugins**
- Keep using [built-in modules](built-in-modules.md)

## What you cannot do

- Point S.Ai at a Steinberg / iLok / vendor `.vst3` folder
- Run desktop VSTs, AU, or LV2
- Import a plugin binary from Files or USB

New modules are more catalog rows + engines inside the app, not files you download into a VST folder.

## See also

- [Add a module](add-modules.md)
- [Instruments vs Effects vs Home](instruments-effects-home.md)
- [Start here](index.md)

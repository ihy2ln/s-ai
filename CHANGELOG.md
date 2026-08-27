# Changelog

## 0.40.0 — VST-style modules + in-app guide

### In-app Guide (new-user wiki)
- Short linked topics inside the app, not a wall of text.
- Open from **?** on Home, **M → Guide**, **N → Guide**, or **?** in Add Module.
- Topics: adding modules, Instruments vs Effects vs Home, Channel Rack vs mixer inserts, built-in modules stay, vocals/guitar/drums/mix, and that this is not a native `.vst3` host.
- Copy lives in `app/src/main/assets/wiki/` and is mirrored under `docs/wiki/`.

### VST2/VST3-style module catalog
- Add Module browser with search, job chips, Instruments / Effects / Home tabs, format and category chips, visual tiles.
- First-class in-app instruments and effects (VST2/VST3 *labels*, not desktop binaries).
- Insert into Home, Channel Rack, or mixer strips.
- Built-in Sampler, Synth, Tracker, Channel Rack, and Pads stay. Layout → Reset restores them.
- **M → Plugins** enables or disables catalog entries; built-in Home modules cannot be turned off.

This release does **not** host native `.dll` / `.vst3` / `.so` files.

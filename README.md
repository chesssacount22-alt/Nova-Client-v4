# Nova Client (placeholder name)

Fabric 1.21.11 client-side mod. ONE mod (shows as a single entry in Mod Menu) that contains:
- Fast Crystal 2.0.0 (by TheGamingAgentXd, MIT) - merged into this jar (credit: TheGamingAgentXd)
- Name Scrambler 1.6.1 (by TheGamingAgentXd, MIT) - merged; its chat commands are not registered, use the menu (Visual tab). Modes: Scramble / Custom name / Random name (Custom + Random are Nova's own mixins in com.novaclient.mixin)
- Safe Anchor - merged into this jar

## Menu
- Search box (top right of the mod grid) filters mods by name, description or category.
- Open/close: **Right Shift** (change it in Settings or Controls).
- Mod config page is grouped: Mode / Delay / Additional settings / KEYBINDS (toggle key + the mod's own keys).
- Mod cards: NAME / description / smooth ON-OFF bar. Click a card for its config page.
- **Settings** (bottom of the sidebar): GUI colour (presets + RGB sliders), background blur on/off,
  keybinds for every mod (keyboard or mouse buttons, ESC = none).
- Background blur uses Minecraft's own "Menu Background Blurriness" (Video Settings).

## Build (GitHub)
1. Push this folder to a repo, run the "build" workflow, download artifact `novaclient`.
2. Put ONLY that jar (plus Fabric API) in `mods/`. Remove the standalone fastcrystal / safeanchor jars.

## Add a new mod (always gets a toggle + keybind automatically)
1. Create a class extending `StoredModule` (on/off saved by Nova) - see FastCrystalModule for settings.
2. Add one line in `ModuleManager.init()`: `register(new MyModule());`
3. (Optional) add `"key.novaclient.toggle.<mod_id>": "Toggle My Mod"` to `assets/novaclient/lang/en_us.json`.

## Safe Anchor on/off
Safe Anchor only runs from its own keybinds, so OFF unbinds its run/cycle keys and ON restores them.

## Grouping options in a mod page
Use `.in("Section name")` on any Setting, e.g. `new Setting.Slider(...).in("Delay")`. Extra keys: `keys.add(new KeyOption(...))`.

## Adding another mod WITHOUT compiling (jar patching)
Put a descriptor at `src/main/resources/novaclient/modules/<name>.json`, copy the mod's classes into
`src/main/resources/`, and add its entrypoint class to `fabric.mod.json` -> `entrypoints.client`.
Descriptor example:
```json
{
  "name": "Name Scrambler", "description": "Scrambles player names.", "category": "UTILITY",
  "author": "TheGamingAgentXd",
  "config": "com.example.namescrambler.Config",
  "enabledField": "enabled",
  "saveMethod": "save",
  "settings": [
    { "type": "toggle", "name": "Hide own name", "field": "hideSelf", "section": "Settings" },
    { "type": "slider", "name": "Delay", "field": "delay", "min": 0, "max": 20, "unit": " ticks", "section": "Delay" },
    { "type": "choice", "name": "Mode", "field": "mode", "options": ["Random", "Fixed"], "section": "Mode" }
  ]
}
```
The mod's Config needs public static boolean/int fields. If it has no enabled flag, Nova can't truly switch it off.

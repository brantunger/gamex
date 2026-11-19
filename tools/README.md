# `tools` — 3D Model & Audio Asset Generators & Live Previewer

This Gradle subproject (`:tools`) builds all `.obj` model assets in `../assets/models/` (the Warden's `enemy-warden.glb` is not generated here) and all 16-bit mono PCM `.wav` sound effects in `../assets/sounds/` directly against `:core` constants, materials, dimensions, and sound cues, and provides an interactive LWJGL3 model previewer.

## Quick Commands

Regenerate every `.obj` model in `assets/models/`:

```sh
./gradlew tools:generateModels
```

Regenerate every `.wav` sound effect in `assets/sounds/`:

```sh
./gradlew tools:generateSounds
```

Verify committed `.obj` and `.wav` files match the generators byte-for-byte (also runs in `./gradlew build` and CI):

```sh
./gradlew tools:test
```

Launch the interactive 3D previewer window:

```sh
./gradlew tools:preview
```

### Previewer Controls
- **Left/Right or `[`/`]`**: switch between the 22 entries: the 3 first-person viewmodels (pistol, scattergun and rail lance, each with the hand) and 19 world models (the `.obj` enemies, bulkhead door, shock grate, security console, pickups, targets, blast orb, rail beam and enemy bolt)
- **Mouse Drag / Scroll**: orbit camera and zoom
- **`Space`**: toggle live hierarchical joint animation (Guard leg/arm walk cycle, Turret scan & pitch, Door lift, Pistol slide kick, Scattergun pump, Rail Lance bolt)
- **`C`**: cycle accent colors (`door-bulkhead`, `pickup-keycard`, `security-console`, and the rail lance viewmodel's ready/recharging glow)
- **`G`**: toggle collision cylinder & `# @socket` gizmos
- **`W`**: toggle wireframe mode
- **`R`**: regenerate all `.obj` models via `GenerateAllModels` and hot-reload GPU meshes in place
- **`Esc`**: quit the previewer

## Adding a Sound

Every sound cue is one `SoundEvent.Type` constant in `:core`, and its file name follows from the constant: `PLAYER_JUMP` is `assets/sounds/player-jump.wav`.

1. Add the constant to `SoundEvent.Type` (`core/.../audio/SoundEvent.java`).
2. Add a `case` for it in `SoundSynth.synthesize` (`tools/.../SoundSynth.java`). A type without one throws `IllegalArgumentException: Unhandled sound event type`. Use `StrictMath` for `sin`, `exp`, `tanh` and the like, as the rest of the file does, so every JDK and CPU writes the same bytes.
3. Optionally add a `case` for it in `SoundManager.baseGain` to set its playback volume; a type without one plays at `0.70`.
4. Run `./gradlew tools:generateSounds` and commit the new `.wav`. `GenerateAllSounds` walks every `SoundEvent.Type`, so there is no list to update.
5. Emit the event from the simulation or the menu (`SoundEvent.player`, `SoundEvent.world` or `SoundEvent.ui`).

`GeneratedSoundsTest` (in `./gradlew tools:test`) fails if a committed `.wav` differs from what the generator writes (`<name> is out of date; re-run ./gradlew tools:generateSounds`), or if `assets/sounds/` holds a `.wav` the generators don't know or lacks one they do. In the game, a sound file that is missing or can't be decoded silences only that cue; the others still play.

## `.obj` Rig & Shader Directives

Standard `#` comments encode optional metadata parsed by `ObjModel` (fully ignored by external OBJ viewers):

- `# @accent <material>`: binds `a_accent = 1.0` so renderers can tint surfaces dynamically via `u_accentColour` without duplicating VBOs.
- `# @part <name> parent=<parent|root> pivot=<x>,<y>,<z>`: declares a local-pivot node hierarchy evaluated by `ModelRig`.
- `# @socket <name> part=<part> pos=<x>,<y>,<z>`: attaches named muzzle/telegraph/FX sockets synchronized with enemy AI and first-person firing.

# `tools` — 3D Model, Audio & Texture Asset Generators & Live Previewer

This Gradle subproject (`:tools`) builds all `.obj` model assets in `../assets/models/` (the Warden's `enemy-warden.glb` is not generated here) and all 16-bit mono PCM `.wav` sound effects in `../assets/sounds/` directly against `:core` constants, materials, dimensions, and sound cues, paints the `.png` textures in `../assets/textures/`, and provides an interactive LWJGL3 model previewer, which draws with the game's model shader (`assets/shaders/model.vert.glsl` and `model.frag.glsl`).

## Quick Commands

Regenerate every `.obj` model in `assets/models/`:

```sh
./gradlew tools:generateModels
```

Regenerate every `.wav` sound effect in `assets/sounds/`:

```sh
./gradlew tools:generateSounds
```

Regenerate every `.png` texture in `assets/textures/`:

```sh
./gradlew tools:generateTextures
```

Verify committed `.obj` and `.wav` files match the generators byte-for-byte, and committed `.png` files pixel for pixel (also runs in `./gradlew build` and CI):

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
3. Add a line for it to `assets/data/sound-volumes.txt` to set its playback volume, keyed by the file name without `.wav` (`player-jump: 0.56`). The line is required: the game stops at start-up on a cue without one (`Missing 'player-jump' key in data/sound-volumes.txt`), and on a key that names no cue. For now, also add the cue and its gain to `gainsBeforeTheMove()` in `SoundVolumesTest` (`core/.../audio/SoundVolumesTest.java`), which pins the shipped table value for value; changing the volume of an existing cue needs the same edit.
4. Run `./gradlew tools:generateSounds` and commit the new `.wav`. `GenerateAllSounds` walks every `SoundEvent.Type`, so there is no list to update.
5. Emit the event from the simulation or the menu (`SoundEvent.player`, `SoundEvent.world` or `SoundEvent.ui`).

`GeneratedSoundsTest` (in `./gradlew tools:test`) fails if a committed `.wav` differs from what the generator writes (`<name> is out of date; re-run ./gradlew tools:generateSounds`), or if `assets/sounds/` holds a `.wav` the generators don't know or lacks one they do. In the game, a sound file that is missing or can't be decoded silences only that cue; the others still play.

## Textures

`./gradlew tools:generateTextures` paints the 11 `.png` textures in `assets/textures/` (three walls, floor, ceiling and six effects) with `TexturePainter` and writes them as 8-bit RGBA with no gamma or colour-profile chunk. The game reads the files at start-up; only the one-pixel `white` texture is still made in code. To try a hand-edited `.png`, replace the file and relaunch with `./gradlew run`: no rebuild is needed. To keep it, change the painter to match, because the test below compares the committed files with the painter. For now, also update the texture's entry in `PIXELS_PAINTED_IN_CODE` in `GeneratedTexturesTest`, which pins the size and the SHA-256 of the pixels each texture had when the game painted it in code.

`GeneratedTexturesTest` (in `./gradlew tools:test`) fails if the pixels of a committed `.png` differ from what the generator paints (`<name> is out of date; re-run ./gradlew tools:generateTextures`), or if `assets/textures/` holds a `.png` the generators don't know or lacks one they do. It compares decoded pixels, not file bytes, because the compressed bytes of a `.png` can differ between Java versions. In the game, a texture file that is missing or can't be decoded stops the start-up with an error naming the file.

### Adding a Texture

1. Add a `paint...` method to `TexturePainter` (`tools/.../TexturePainter.java`). Use `StrictMath` for `sin`, `cos`, `atan2`, `pow` and the like, as the rest of the file does, so every JDK and CPU paints the same pixels.
2. Register it under its file name in `GenerateAllTextures`.
3. Run `./gradlew tools:generateTextures` and commit the new `.png`.
4. For any other texture, add a file-name constant and a `Texture` field to `ProceduralTextures` (`core/.../render/ProceduralTextures.java`) and load it in the constructor. Textures are drawn with nearest-neighbour filtering unless the constructor asks for another filter, as it does for `orb.png`.
5. For a wall texture, name the file in `assets/data/walls.txt`, which gives every wall character of a map layout its texture (`#: brick.png`). The file must hold exactly one line for each character of `GameMap.WALL_TYPES`; a wall character without a line, or a line for any other character, stops the start-up with an error naming the file and the character. There is no fallback texture. A new wall type also needs its character added to `GameMap.WALL_TYPES`. For now, also update `WallTexturesTest` (`core/.../render/WallTexturesTest.java`), which pins the shipped table and hardcodes the `#%&` set of wall characters.
6. For now, add the file to `PIXELS_PAINTED_IN_CODE` in `GeneratedTexturesTest` (`tools/.../GeneratedTexturesTest.java`) with its size and the SHA-256 of its pixels, and, unless it is a wall texture named in `walls.txt`, to the list of `ProceduralTextures` file constants in `theGameLoadsExactlyTheGeneratedTextures` in the same test, which checks that the game loads every generated texture and no other.

## `.obj` Rig & Shader Directives

Standard `#` comments encode optional metadata parsed by `ObjModel` (fully ignored by external OBJ viewers):

- `# @accent <material>`: binds `a_accent = 1.0` so renderers can tint surfaces dynamically via `u_accentColour` without duplicating VBOs.
- `# @part <name> parent=<parent|root> pivot=<x>,<y>,<z>`: declares a local-pivot node hierarchy evaluated by `ModelRig`.
- `# @socket <name> part=<part> pos=<x>,<y>,<z>`: attaches named muzzle/telegraph/FX sockets synchronized with enemy AI and first-person firing.

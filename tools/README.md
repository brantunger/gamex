# `tools` — 3D Model & Audio Asset Generators & Live Previewer

This Gradle subproject (`:tools`) builds all `.obj` model assets in `../assets/models/` and all 16-bit mono PCM `.wav` sound effects in `../assets/sounds/` directly against `:core` constants, materials, dimensions, and sound cues, and provides an interactive LWJGL3 model previewer.

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
- **Left/Right or `[`/`]`**: switch between all 15 world props, enemies, doors, consoles, keycards, and the combined first-person pistol + hand viewmodel
- **Mouse Drag / Scroll**: orbit camera and zoom
- **`Space`**: toggle live hierarchical joint animation (Guard leg/arm walk cycle, Turret scan & pitch, Door lift, Pistol slide kick)
- **`C`**: cycle accent colors (`door-bulkhead`, `pickup-keycard`, `security-console`)
- **`G`**: toggle collision cylinder & `# @socket` gizmos
- **`W`**: toggle wireframe mode
- **`R`**: regenerate all `.obj` models via `GenerateAllModels` and hot-reload GPU meshes in place

## `.obj` Rig & Shader Directives

Standard `#` comments encode optional metadata parsed by `ObjModel` (fully ignored by external OBJ viewers):

- `# @accent <material>`: binds `a_accent = 1.0` so renderers can tint surfaces dynamically via `u_accentColour` without duplicating VBOs.
- `# @part <name> parent=<parent|root> pivot=<x>,<y>,<z>`: declares a local-pivot node hierarchy evaluated by `ModelRig`.
- `# @socket <name> part=<part> pos=<x>,<y>,<z>`: attaches named muzzle/telegraph/FX sockets synchronized with enemy AI and first-person firing.

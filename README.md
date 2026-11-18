# gamex

An original 3D first-person shooter, built with [libGDX](https://libgdx.com/) 1.14.2 on the LWJGL3 desktop backend.

This is an active campaign & range build. You are "gamerx", equipped with an infinite-ammo pistol, a charged energy
blast, a pump-action **Combat Scattergun** with 2-shell double-blast alt-fire, a piercing **Rail Lance** with 1.8x
tactical zoom optic, a free physical jump, and a horizontal energy dash. Battle four hostile robotic archetypes (**Rusher Drones**, **Sentry Turrets**,
**Guard Bots**, and the two-phase **Warden Mech** boss), jump or dash over live **Shock Grates**, chain explosive coolant canisters, breach sliding bulkhead doors, collect Amber and Cyan
keycards, override Security Consoles, collect 3D health, armor, energy, weapon, shell and slug pickups, and clear the 6-stage campaign
(**01: Outer Breach**, **02: Coolant Core**, **03: Security Hub**, **04: Lockdown Wing**, **05: Command Vault**, **06: Warden Core**) across
Easy, Normal or Hard difficulty — or unlock and replay stages from **Select Stage** and practise in the **Training
Range**. Surface and effect textures are synthesised in code at start-up, while every map and campaign stage is loaded from
`assets/levels/`, every weapon, combat and menu sound is loaded from a 16-bit mono PCM `.wav` file in `assets/sounds/`, and
every 3D graphical object — the pistol, combat scattergun, rail lance, gloved hand holding them, targets, enemies, projectiles, beam trails,
pickups, bulkheads, shock grates, keycards and security consoles — is loaded from a Wavefront `.obj` model in `assets/models/`.

## How to run

```sh
./gradlew desktop:run
```

On Windows, use `gradlew.bat desktop:run` (`.\gradlew.bat desktop:run` in PowerShell).

On macOS (Apple silicon or Intel), the same command works. LWJGL3 must run on the process's first thread there, which
needs the JVM option `-XstartOnFirstThread`: the `run` task passes it, the Construo `.app` bundle
(`desktop:packageMacM1` or `desktop:packageMacX64`) sets it, and `java -jar` on a jar from `desktop:jar` or
`desktop:jarMac` gets it from `StartupHelper`, which relaunches the JVM with it.

The build compiles and runs with a Java 21 toolchain. If Gradle finds no Java 21 on the machine, the first build
downloads one, so it can take a while.

The game opens in a 1280x720 window, on the Title screen, with the mouse cursor free. If fullscreen was switched
on in the settings on an earlier run, it opens fullscreen instead. The cursor is captured while you play and is
freed whenever a menu is up.

## Controls

While playing:

| Input | Action |
| --- | --- |
| `W` `A` `S` `D` | Move forward, left, back, right |
| `Space` | Vertical jump (`0` energy, full air control, pre-landing buffer; keeps weapons & zoom active) |
| `Shift` | Horizontal energy dash (`25` energy; chains cleanly in mid-air) |
| Mouse | Look around |
| Left mouse button | Fire the active weapon (Pistol shot, 1-shell Scattergun spread, or piercing Rail Lance beam) |
| Right mouse button | Pistol: charge Energy Blast / Scattergun: 2-shell Double-Blast / Rail Lance: 1.8x Tactical Zoom Optic |
| `1` / `2` / `3` / `Q` / Scroll | Select Pistol (`1`), Combat Scattergun (`2`), Rail Lance (`3`), quick-swap last weapon (`Q`), or cycle owned weapons (`Mouse Wheel`) |
| `E` | Open unlocked bulkheads, unlock matching keycard doors, or override Security Consoles |
| `R` | Quick-restart the current stage from your entry state |
| `Esc` | Open the pause menu and free the cursor; `Esc` again resumes |

In the menus:

| Input | Action |
| --- | --- |
| `Up` / `Down` or `W` / `S` | Move the highlight; it wraps at both ends |
| `Enter` / `Space` | Choose the highlighted row; on a setting, step it up or flip it |
| Mouse | Highlight the row under the cursor; a left click chooses it |
| `Left` / `Right` or `A` / `D` | Change the highlighted setting on the Settings screen |
| `Esc` | Go back: Pause resumes play, Settings returns to where it was opened, others return to the Title |
| `R` | Retry the stage on the death screen |
| `Q` | Quit while on the Title or Pause screen |

On the Settings screen a click on the left half of a setting's row steps it down and a click on the right half
steps it up. An on/off setting flips either way.

The game also pauses and frees the cursor when the window loses the focus or is minimised during play. It stays
paused until you press `Esc` or choose `RESUME`; a click anywhere else does not resume it. Pausing cancels a blast
charge that is building, at no energy cost. A mouse button that is still down when play starts or resumes does
nothing until it is released, so the click that chose `RESUME` or `DEPLOY` does not fire a shot.

## Gameplay basics

**Menus and stages.** The Title screen offers `START CAMPAIGN`, `SELECT STAGE`, `TRAINING RANGE`, `SETTINGS` and
`QUIT`, and `CONTINUE CAMPAIGN` when a campaign run is saved. Starting a campaign asks for a difficulty and saves the
run, replacing any saved one; a briefing follows, and `DEPLOY` starts the stage. `SELECT STAGE` lists unlocked
campaign stages with their best rank and lets you start a fresh run from any unlocked stage.

- **01: Outer Breach** introduces Rusher Drones and Sentry Turrets and ends at the extraction pad.
- **02: Coolant Core** adds mobile Guard Bots across multi-lane reactor conduits.
- **03: Security Hub** combines coordinated squads of all three archetypes and unlocks the **Rail Lance** (`R` / `r`) ahead of its long combat corridors.
- **04: Lockdown Wing** introduces sliding bulkhead doors (`=`), Amber keycard wing gating (`k` -> `A`), and a Security Console lockdown override (`S` -> `L`) protecting extraction and ambush bays.
- **05: Command Vault** chains Amber and Cyan keycard clearances (`k` -> `A`, `c` -> `C`) to a vault Security Console override and flanking ambush.
- **06: Warden Core** introduces live **Shock Grate** dash trenches (`~`) and culminates in the two-phase **Warden Mech** boss (`B`) sealing the `M` extraction bulkhead before `CAMPAIGN COMPLETE`.
- The **Training Range** includes practice targets, a Shock Grate dash trench, and both the Scattergun and Rail Lance racks, ending when every target is destroyed (always played on Normal).
- A cleared stage shows a tally: clear time against par, hostiles cleared on combat stages (or targets cleared in the
  Training Range), damage taken and a rank from `S` down to `C`. The best time and rank for each stage and difficulty
  are saved.
- The settings are mouse sensitivity, inverted look, field of view, view bob, master volume and fullscreen. They are
  saved between runs.

**Health and armor.** You start a campaign with 100 health and no armor; both hold at most 100. While armor is
left it absorbs half of each hit and the rest comes off health. Enemy attacks, close-range blasts and detonating
canisters all deal directional damage. At zero health the view drops to the floor and the death screen follows;
retrying, like `R`, restarts the stage with the health and armor you entered it with.

**Difficulty.** Easy scales the damage you take to 70%, pickups to 125%, and enemy attack speed/bolt speed to 85%;
Hard scales damage taken to 140%, pickups to 80%, and enemy attack speed/bolt speed to 120% while keeping enemy hit
points constant across difficulties.

**Jump & Dash.** `Space` performs a free physical jump (`0` energy, `~0.32u` apex below the `1.0u` ceiling, `~0.48s` hang time) with full `WASD` air control and a `0.10s` pre-landing jump buffer (press-per-jump, no double-jump). Jumping elevates your 3D camera, weapon aim origin, and vertical hitbox (`[feetY, topY]`), clears floor-level **Shock Grates** (`~`) while airborne, and leaves Pistol Blast charging and Rail Lance `1.8x` zoom active for jump-shots. `Shift` triggers a horizontal **Energy Dash** (`0.14s` at `11.5 u/s`, ~`1.6u`) along your movement keys (or straight ahead when standing still): it costs `25` energy on a `0.35s` cooldown, cancels active Blast charges or Rail zoom, grants `100%` Shock Grate immunity, and suspends downward fall when chained mid-air to vault wide trenches. With less than `25` energy a dash is refused and the HUD shows `LOW ENERGY`.

**Pistol, Combat Scattergun & Rail Lance.** All three weapons share the lower-right 3D viewmodel pass and gloved hand so walls never clip them. Switch cleanly at any time with `1`, `2`, `3`, `Q` (quick-swap last weapon) or the mouse wheel (cycle owned weapons):

- **Pistol (`1`)**: Instant hitscan (`20` damage, `0.3s` interval, up to `60` world units away, infinite ammo). Right mouse button charges the violet **Energy Blast** orb.
- **Combat Scattergun (`2`)**: Unlocked in **01: Outer Breach**, **06: Warden Core**, and the **Training Range** (and equipped directly when deploying to Stages `02`–`06` from Select Stage). Draws from a carried `24`-shell pool fed by Scattergun (`+8`) and Shell Caddy (`+6`) pickups scaled by difficulty:
  - **Primary fire (LMB, `1` shell)**: `8` deterministic pellets across a `4.2°` half-angle cone (`12` base damage each, linear falloff from `7u` to `26u` max range, `0.75s` pump cycle).
  - **Double-Blast alt-fire (RMB, `2` shells)**: `16` deterministic pellets across a `6.8°` cone (`1.15s` pump recovery; fires a `1`-shell blast if only `1` shell remains).
  - Pulling the trigger at `0` shells emits a dry-fire click and smart-swaps to a loaded weapon or the Pistol automatically.
- **Rail Lance (`3`)**: Unlocked in **03: Security Hub**, **06: Warden Core**, and the **Training Range** (and unlocked directly in Slot 3 when deploying to Stages `04`–`06` from Select Stage). Draws from a carried `15`-slug pool fed by Rail Lance (`+5`) and Slug Pack (`+3`) pickups scaled by difficulty:
  - **Primary fire (LMB, `1` slug)**: Instant `60u` piercing hitscan beam (`1.10s` capacitor recharge, `0` distance falloff) that penetrates every live target and enemy in line (`120` damage; `90` vs Warden Mech boss plating), stopping only at solid walls, closed bulkheads, or Security Consoles and leaving a fading 3D beam wake.
  - **Tactical Zoom Optic (hold RMB, `0` cost)**: Smooth `1.8x` FOV magnification with proportional look-sensitivity scaling, centered low-ADS framing, rangefinder HUD brackets, full arena move speed, and instant zoom cancel on Dash or weapon swap.
  - Pulling the trigger at `0` slugs emits a dry-fire click and smart-swaps to your previous loaded weapon or the Pistol automatically.

**Blast.** A glowing gyroscope orb fired from the pistol muzzle toward the crosshair target that explodes on the first
wall, floor, ceiling or live target/enemy it touches and damages every unobstructed target and enemy in its radius. Solid
walls shield targets, enemies and the player from explosions, while close-range blasts within line of sight deal
50%-scaled splash damage to the player.

- Holding the button builds a charge from 0 to 1 over 1.2 seconds.
- Releasing below a charge of 0.25 (about 0.3 seconds of holding) fires nothing and costs nothing.
- A blast costs 20 energy at the minimum charge and 60 at full charge, rising in a straight line between them.
- The explosion grows with the charge: its radius is 1.5 to 3 units, and its damage at the centre is 55 to 130,
  falling in a straight line to nothing at the edge.

**Energy.** The meter holds 100 and starts full.

- It refills at 14 per second, but not while a charge is building.
- A charge stops growing at what the meter can pay for.
- With less than 20 energy a charge cannot start. The HUD shows `LOW ENERGY`, and the meter keeps refilling even
  while the button is held.
- Blasts and dashes are paid for from the same meter.

**Targets, Enemies & Pickups.** Levels contain coolant canisters (50 health, three pistol shots) that detonate in
chain-reacting area explosions when destroyed, ring boards (120 health, six pistol shots), and four robotic hostiles:

- **Rusher Drone** (40 health): closes distance around cover via grid pathfinding and telegraphs a shock lunge that
  can be sidestepped with Energy Dash.
- **Sentry Turret** (80 health): anchored base with a swiveling dual-barrel head that winds up and fires 3-bolt
  bursts.
- **Guard Bot** (100 health): mobile skirmisher that avoids Shock Grates, strafes at medium range and fires single aimed plasma bolts.
- **Warden Mech** (600 health): two-phase sanctum boss tracked by a top-center health bar. Fires twin-arm 3-bolt fan volleys and telegraphed hydraulic charge slams in Phase 1, shifting at `<= 50%` HP into **Overdrive** (electric violet-cyan reactor, `30%` faster stride/cadence, and 5-bolt fan barrages).

**Shock Grates, Bulkheads, Keycards & Consoles.** Live Shock Grate floor tiles (`~`) deal periodic arc ticks while walked on, while **jumping (`Space`) clears floor grates while airborne** and **Energy Dash (`Shift`) grants 100% immunity** (`0` damage) to cross wider conduit trenches unscathed. Guard Bots detour around `~` grates while Drones and the Warden Mech traverse them freely. Sliding steel bulkhead doors (`=`) open permanently with `E`; Amber (`k`) and Cyan (`c`) keycards unlock matching `A` / `C` bulkheads; `S` Security Consoles override `L` lockdown bulkheads; and defeating every **Warden Mech** automatically unseals all `M` boss extraction bulkheads.

## Debug screenshot mode

For checking a launch with nobody at the keyboard, the game can save one frame as a PNG and quit. It is switched
on by environment variables:

| Variable | Meaning |
| --- | --- |
| `GAMEX_SCREENSHOT` | Path of the PNG to write. Switches the mode on; without it the game runs normally. |
| `GAMEX_SCREENSHOT_FRAME` | The frame to save, counted from 1. Default 60; a non-number prints a warning and uses 60. |
| `GAMEX_DEMO` | Optional script: `action`, `targets`, `breach`, `title`, `lockdown`, `stages`, `scattergun`, `raillance`, `warden` or `jump`. Without it the player stands still. |
| `GAMEX_VIEWMODEL_ORBIT` | Optional: `yaw` or `yaw,pitch` in degrees. Draws only the pistol and hand, from outside. |

- `action`: the player turns towards a pillar, charges and releases a blast, then fires the pistol.
- `targets`: the player turns towards the nearest canister and fires the pistol at it from frame 30. The third
  shot, around frame 66, destroys it, so a later frame shows it gone.
- `breach`: on the Outer Breach stage, the player rushes and dashes east through the breach doorway, strafes past
  turret fire, turns toward the room's Drone and Turret and fires the pistol.
- `title`: the saved frame shows the Title screen over its moving view of the Outer Breach stage. Nothing is
  played, and `GAMEX_VIEWMODEL_ORBIT` is ignored.

`action`, `targets` and a run without a script start in play on the original first level, which has no pickups and
no exit. The script name is not case-sensitive, and an unknown name is the same as no script.

With `GAMEX_VIEWMODEL_ORBIT` the saved frame shows the pistol and hand alone, at rest, on a plain grey background,
without the world or the HUD. A yaw of 0 shows them from behind, 90 from the left side and 180 from the front; a
pitch of 90 shows them from above, and the pitch is 0 if left out. A value that is not a number prints a warning
and the normal picture is saved.

`GAMEX_SCREENSHOT_FRAME`, `GAMEX_DEMO` and `GAMEX_VIEWMODEL_ORBIT` do nothing unless `GAMEX_SCREENSHOT` is set.
During such a run the keyboard and mouse are ignored, the cursor stays free, sound output is muted, and every frame
advances the game by 1/60 of a second, so the saved frame is the same on every run. Saved settings and campaign
progress are neither read nor written: the run uses the default settings and Normal difficulty.

Use an absolute path. The `desktop:run` task runs in the `assets` folder, so a relative path would write the PNG
there.

One more variable works on any run, with or without `GAMEX_SCREENSHOT`: `GAMEX_WINDOW` sets the window size as
`WIDTHxHEIGHT`, for example `800x600`. A value that is malformed, smaller than 160x120 or larger than 7680x4320 prints a
warning and the window opens at 1280x720.

PowerShell:

```powershell
$env:GAMEX_SCREENSHOT = "$env:TEMP\gamex.png"; $env:GAMEX_DEMO = "action"
.\gradlew.bat desktop:run
Remove-Item Env:GAMEX_SCREENSHOT, Env:GAMEX_DEMO
```

The variables last for the PowerShell session, so remove them afterwards, or the next run will also save a frame
and quit.

bash:

```sh
GAMEX_SCREENSHOT=/tmp/gamex.png GAMEX_DEMO=targets GAMEX_SCREENSHOT_FRAME=120 ./gradlew desktop:run
```

## Project layout

- `core`: the game, shared by all platforms, under `io.github.brantunger.gamex`.
  - `MainGame`: the libGDX entry point; it reads input, advances the simulation and draws each frame.
  - `GameSimulation`: the whole game state and its rules. It uses neither graphics nor audio types, so it runs without
    a window.
  - `audio`: headless sound-event cues (`SoundEvent`) and internal `.wav` asset loading with 3D distance/pan OpenAL
    playback (`SoundManager`).
  - `world`: the level grid (`GameMap`), `.map` and `campaign.txt` level/stage asset loading (`Levels`), collision with walls, targets and enemies,
    hitscan ray casting, grid BFS pathfinding (`Pathfinder`), map geometry, targets, hostile enemies (`Enemy`), pickups
    (`Pickup`), world object models (`WorldModels`) and effects.
  - `player`: the player's position, view angles, movement and dash, health and armor, the per-frame input record,
    the difficulty levels (`Difficulty`) and the health and armor a stage is entered with (`PlayerLoadout`).
  - `weapon`: the pistol, the combat scattergun (`Scattergun`), the rail lance (`RailLance`), weapon slot switching (`WeaponSlot`), the energy meter, the blast's charge logic, player and enemy projectiles (`BlastProjectile`,
    `EnemyProjectile`), explosion damage, the pistol, scattergun & rail lance 3D models (`PistolModel`, `ScattergunModel`, `RailLanceModel`), the colours for the
    hand's mesh (`HandModel`) and the numbers that animate them.
  - `model`: a parser for Wavefront OBJ model files including `# @part` / `# @socket` / `# @accent` directives (`ObjModel`),
    a headless forward-kinematics rig evaluator (`ModelRig`), and vertex packing (`ModelVertices`, including 16-bit
    indexed deduplication). None use graphics types.
  - `render`: the 3D view (`WorldRenderer` with `u_normalMatrix` and `u_accentColour` shader uniforms), the pass that draws the pistol and hand over it (`ViewmodelRenderer`), and the textures painted in code.
  - `hud`: the 2D overlay (crosshair and hit markers, health, armor and energy bars, charge bar, status texts and
    the menu screens), the screen and menu state machine behind those screens (`MenuState`, which uses no graphics
    or input types) and the built-in pixel font.
  - `settings`: the player's settings (`GameSettings`), the saved campaign run and best records (`SaveData`), and
    the key-value store they are kept in: libGDX preferences in the game, a map in tests and debug runs.
  - `input`: reads the keyboard and mouse for play and for the menus, adds up mouse movement for looking around
    (`LookAccumulator`), and handles pause and cursor capture.
  - `debug`: the debug screenshot mode.
- `desktop`: the LWJGL3 launcher (`DesktopLauncher`), the window settings and the window icons.
- `tools`: the Gradle model and audio authoring and inspection subproject (see [tools/README.md](tools/README.md)): `MeshBuilder`
  DSL, `PropSculpt`, `EnemySculpt`, `PistolSculpt`, `ScattergunSculpt`, `RailLanceSculpt`, `HandSculpt`, `GenerateAllModels`,
  `SoundSynth`, `GenerateAllSounds`, and the interactive LWJGL3 previewer (`ModelPreviewApp`).
- `assets`: the files the game loads at start-up:
  - `levels/*.map`, `levels/campaign.txt`: plain-text map layouts, stage metadata/briefings, and ordered campaign manifest.
  - `sounds/*.wav`: 16-bit mono PCM `.wav` files for every weapon, combat, world, pickup and menu cue plus the seamless `charge-hum.wav` blast-charge loop.
  - `models/viewmodel-hand.obj`: the mesh of the hand, wrist, cuff and sleeve.
  - `models/viewmodel-pistol.obj`, `models/viewmodel-scattergun.obj`, `models/viewmodel-raillance.obj`: the meshes of the first-person pistol (body and slide), shortened double-barrel drum combat scattergun (body and lever forend), and rail lance (body and magnetic bolt).
  - `models/canister.obj`, `models/ring-board.obj`, `models/blast-orb.obj`, `models/rail-beam.obj`, `models/pickup-*.obj`: 3D meshes for
    targets, pickups (medkit, armor, energy cell, keycards, scattergun, shells, rail lance, slugs), blast projectiles and rail beam trails.
  - `models/door-bulkhead.obj`, `models/security-console.obj`: 3D meshes for sliding bulkhead doors and security consoles.
  - `models/enemy-turret.obj`, `models/enemy-drone.obj`, `models/enemy-guard.obj`, `models/enemy-bolt.obj`: 3D meshes
    for articulated enemies and enemy plasma bolts.

## Tests

```sh
./gradlew test
```

The tests are JUnit Jupiter tests in `core/src/test`, `desktop/src/test`, and `tools/src/test`. They need no window or
graphics context. `HandModelTest`, `PistolModelTest`, `ScattergunModelTest`, `RailLanceModelTest`, `WorldModelsTest`, `SoundManagerTest` and `LevelsTest` read `.obj` models, `.wav` sounds and `.map` levels from `assets/models`, `assets/sounds` and `assets/levels` and
track asset changes in `core/build.gradle`; `:tools:test` (`GeneratedModelsTest` and `GeneratedSoundsTest`) invokes `GenerateAllModels` and `GenerateAllSounds` directly
to verify byte-for-byte reproduction of every shipped `.obj` and `.wav` asset and indexed vertex deduplication. They cover:

- `GameSimulation`: pistol hits, a charged blast from release to explosion, muzzle launch and crosshair convergence,
  a pause dropping a charge, targets blocking the player, the clamp on long frames, canister explosions chaining and
  hurting the player, a dash dropping a charge and collecting pickups, enemy alerting/damage/kills, headless sound
  events (weapon, target, enemy, pickup, charge-audio and edge-triggered `LOW ENERGY`), and a stage ending when its
  last target breaks.
- `world`: layout and `.map`/manifest parsing (`GameMapTest`, `LevelsTest`, including doors, keycards, consoles, pickups, enemies, hazards and exit), the first level (the spawn is clear of walls and every target can be reached), the Training Range and all six campaign stages (progression-aware reachability of targets, enemies, pickups, consoles and exit), collision, standard & piercing hitscan (`HitscanTest`), grid BFS pathfinding (`PathfinderTest`), enemy AI (`EnemyTest`), map geometry, targets
  and the shipped world/enemy `.obj` models (closed outward manifolds matching collision dimensions; pickups stand on
  the floor and fit their pickup radius).
- `player`: view angles and movement, armor absorbing damage, death, and the dash (its cost, distance, cooldown
  and refusal on low energy).
- `hud`: the campaign's path through the menu screens, stage select and menu audio events (`MenuStateTest`), and the
  pixel font's glyphs (`PixelFontTest`).
- `settings`: the settings' ranges (including `masterVolume`) and their round trip through a store, and the saved run,
  best times and ranks (`GameSettingsAndSaveTest`).
- `audio`: shipped `.wav` asset coverage/format verification, loop-seam continuity, muted headless draining and 3D
  spatial attenuation/panning math (`SoundManagerTest`).
- `input`: adding up mouse movement for looking around (`LookAccumulator`).
- `weapon`: the pistol, combat scattergun (`ScattergunTest`: deterministic 8/16 pellet cones, range falloff, 1-shell fallback, pump curve), and rail lance (`RailLanceTest`: cooldown, bolt cycle, smooth 1.8x zoom optic & cancel latch), the energy meter, the blast's charge rules, the blast projectile (including impact
  contact bisection) and explosion damage, enemy bolts (`EnemyProjectileTest`), the shipped pistol, scattergun & rail lance models (`PistolModelTest`, `ScattergunModelTest`, `RailLanceModelTest`: closed convex outward solids and zero hand-glove penetration at rest and full slide/pump/bolt travel), the shipped hand mesh (every part is closed and faces outwards, no quad folds, unit
  normals, bands of the fingers and palm lie on the grip's surface, the fingers wrap the grip, no glove vertex is inside
  the pistol, and `HandSculpt`'s copied outlines match `PistolModel`), and the animation (recoil, slide, walking bob and turn lag).
- `model`: the OBJ parser (including files it must reject), byte-for-byte generator freshness for every `.obj` in
  `assets/models/` (`GeneratedModelsTest`), and model-file error reporting (`ModelVertices.read`).
- `desktop`: parsing `GAMEX_WINDOW` (including its 160x120 to 7680x4320 bounds), the packaging icons, and when and
  how `StartupHelper` relaunches the JVM on macOS.

Rendering, the drawing of the HUD (`HudRenderer`), OpenAL audio hardware playback (`SoundManager` runtime), the rest
of input handling (`InputController`) and the debug screenshot mode have no tests, apart from the parsing of
`GAMEX_VIEWMODEL_ORBIT` (`DemoRunTest`) and spatial attenuation/panning math (`SoundManagerTest`).

A GitHub Actions workflow (`.github/workflows/build.yml`) runs `./gradlew build` with Java 21 on Linux, Windows and
macOS on every pull request and push to `main`.

## Gradle

The project uses the [Gradle](https://gradle.org/) wrapper: run tasks with `./gradlew`, or `gradlew.bat` on
Windows.

- `desktop:run`: starts the game.
- `tools:generateModels`: regenerates every sculpted `.obj` model in `assets/models/`.
- `tools:generateSounds`: regenerates every synthesized `.wav` sound effect in `assets/sounds/`.
- `tools:preview`: launches the interactive 3D OBJ model previewer (`R` rebuilds & reloads live).
- `test`: runs the unit tests across `core`, `desktop`, and `tools`.
- `build`: builds and tests every project.
- `desktop:jar`: builds a runnable jar in `desktop/build/libs`.
- `clean`: removes the `build` folders.
- `idea`: generates IntelliJ project data; `cleanIdea` removes it.

A task can be run for one project with a `name:` prefix: `core:clean` removes the `build` folder of `core` only.

Useful flags:

- `--offline`: uses cached dependencies only.
- `--refresh-dependencies`: checks every dependency again and downloads any that changed.
- `--continue`: keeps running tasks after one fails.

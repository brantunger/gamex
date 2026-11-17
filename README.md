# gamex

An original 3D first-person shooter, built with [libGDX](https://libgdx.com/) 1.14.2 on the LWJGL3 desktop backend.

This is an active campaign & range build. You are "gamerx", equipped with an infinite-ammo pistol, a charged energy
blast, and a horizontal energy dash. Battle three hostile robotic archetypes (**Rusher Drones**, **Sentry Turrets**,
and **Guard Bots**), chain explosive coolant canisters, breach sliding bulkhead doors, collect Amber and Cyan
keycards, override Security Consoles, collect 3D health, armor and energy pickups, and clear the 5-stage campaign
(**01: Outer Breach**, **02: Coolant Core**, **03: Security Hub**, **04: Lockdown Wing**, **05: Command Vault**) across
Easy, Normal or Hard difficulty — or unlock and replay stages from **Select Stage** and practise in the **Training
Range**. Surface and effect textures as well as every weapon, combat and menu sound are synthesised in code at
start-up, while every 3D graphical object — the pistol, the gloved hand holding it, targets, enemies, projectiles,
pickups, bulkheads, keycards and security consoles — is loaded from a Wavefront `.obj` model in `assets/models/`.

## How to run

```sh
./gradlew desktop:run
```

On Windows, use `gradlew.bat desktop:run` (`.\gradlew.bat desktop:run` in PowerShell).

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
| `Shift` / `Space` | Horizontal energy dash (`25` energy) |
| Mouse | Look around |
| Left mouse button | Fire the pistol; hold to keep firing |
| Right mouse button | Hold to charge the blast, release to fire it |
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
- **03: Security Hub** combines coordinated squads of all three archetypes before extraction.
- **04: Lockdown Wing** introduces sliding bulkhead doors (`=`), Amber keycard wing gating (`k` -> `A`), and a Security Console lockdown override (`S` -> `L`) protecting extraction and ambush bays.
- **05: Command Vault** chains Amber and Cyan keycard clearances (`k` -> `A`, `c` -> `C`) to a vault Security Console override and flanking ambush before final extraction (`CAMPAIGN COMPLETE`).
- The **Training Range** ends when every target is destroyed. It is always played on Normal.
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

**Dash.** A short burst, 0.14 seconds at 11.5 units per second (about 1.6 units), in the direction of the movement
keys, or straight ahead when none is held. It costs 25 energy and the next dash can start 0.35 seconds after the
last one started. Pressing dash cancels a blast charge that is building, and a charge cannot build during a dash.
With less than 25 energy the dash is refused and the HUD shows `LOW ENERGY`.

**Pistol.** A hitscan weapon: a shot hits instantly, up to 60 world units away (one map cell is one unit). It has
no ammo limit. With the button held it fires once every 0.3 seconds, at any frame rate, and a shot does 20 damage.

On screen the pistol is a 3D model in a gloved hand at the lower right, drawn in a pass of its own so that a wall
the player stands against never cuts it off. It kicks back on each shot with a flash at the muzzle, bobs while the
player walks, lags a little behind turns, and glows violet towards the muzzle while a blast is charging.

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
chain-reacting area explosions when destroyed, ring boards (120 health, six pistol shots), and three robotic hostiles:

- **Rusher Drone** (40 health): closes distance around cover via grid pathfinding and telegraphs a shock lunge that
  can be sidestepped with Energy Dash.
- **Sentry Turret** (80 health): anchored base with a swiveling dual-barrel head that winds up and fires 3-bolt
  bursts.
- **Guard Bot** (100 health): mobile skirmisher that strafes at medium range and fires single aimed plasma bolts.

Dodged enemy bolts detonate coolant canisters on impact while stopping harmlessly on squadmates. Medkits, armor vests
(absorbing 50% of incoming damage) and energy cells hover on the floor and scale with the chosen difficulty. On Normal a
medkit restores 25 health, a vest adds 25 armor and a cell adds 35 energy. Walk over a pickup to take it; one whose meter
is already full stays where it is.

**Bulkheads, Keycards & Consoles.** Sliding steel bulkhead doors (`=`) open permanently with `E` once breached,
unlocking sightlines and enemy pathfinding routes. Amber (`k`) and Cyan (`c`) keycards unlock matching `A` / `C`
bulkheads in the stage and appear on the HUD while held; `S` Security Consoles act as indestructible cover until
activated with `E`, simultaneously unsealing all `L` lockdown bulkheads and alerting nearby hostiles.

## Debug screenshot mode

For checking a launch with nobody at the keyboard, the game can save one frame as a PNG and quit. It is switched
on by environment variables:

| Variable | Meaning |
| --- | --- |
| `GAMEX_SCREENSHOT` | Path of the PNG to write. Switches the mode on; without it the game runs normally. |
| `GAMEX_SCREENSHOT_FRAME` | The frame to save, counted from 1. Default 60; a non-number prints a warning and uses 60. |
| `GAMEX_DEMO` | Optional script: `action`, `targets`, `breach`, `title`, `lockdown` or `stages`. Without it the player stands still. |
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
  - `audio`: procedural 16-bit mono WAV synthesis (`ProceduralSounds`), headless sound-event cues (`SoundEvent`) and
    3D distance/pan OpenAL playback (`SoundManager`).
  - `world`: the level grid, its layouts and the stage definitions (`Levels`), collision with walls, targets and enemies,
    hitscan ray casting, grid BFS pathfinding (`Pathfinder`), map geometry, targets, hostile enemies (`Enemy`), pickups
    (`Pickup`), world object models (`WorldModels`) and effects.
  - `player`: the player's position, view angles, movement and dash, health and armor, the per-frame input record,
    the difficulty levels (`Difficulty`) and the health and armor a stage is entered with (`PlayerLoadout`).
  - `weapon`: the pistol, the energy meter, the blast's charge logic, player and enemy projectiles (`BlastProjectile`,
    `EnemyProjectile`), explosion damage, the pistol's 3D model and materials (`PistolModel`), the colours for the
    hand's mesh (`HandModel`) and the numbers that animate both.
  - `model`: a parser for Wavefront OBJ model files (`ObjModel`) and vertex packing (`ModelVertices`). Neither uses
    graphics types.
  - `render`: the 3D view, the pass that draws the pistol and hand over it, and the textures painted in code.
  - `hud`: the 2D overlay (crosshair and hit markers, health, armor and energy bars, charge bar, status texts and
    the menu screens), the screen and menu state machine behind those screens (`MenuState`, which uses no graphics
    or input types) and the built-in pixel font.
  - `settings`: the player's settings (`GameSettings`), the saved campaign run and best records (`SaveData`), and
    the key-value store they are kept in: libGDX preferences in the game, a map in tests and debug runs.
  - `input`: reads the keyboard and mouse for play and for the menus, adds up mouse movement for looking around
    (`LookAccumulator`), and handles pause and cursor capture.
  - `debug`: the debug screenshot mode.
- `desktop`: the LWJGL3 launcher (`DesktopLauncher`), the window settings and the window icons.
- `assets`: the files the game loads at start-up:
  - `models/viewmodel-hand.obj`: the mesh of the hand, wrist, cuff and sleeve.
  - `models/viewmodel-pistol.obj`: the mesh of the first-person pistol (body and animated slide).
  - `models/canister.obj`, `models/ring-board.obj`, `models/blast-orb.obj`, `models/pickup-*.obj`: 3D meshes for
    targets, pickups, keycards and blast projectiles.
  - `models/door-bulkhead.obj`, `models/security-console.obj`: 3D meshes for sliding bulkhead doors and security consoles.
  - `models/enemy-turret.obj`, `models/enemy-drone.obj`, `models/enemy-guard.obj`, `models/enemy-bolt.obj`: 3D meshes
    for articulated enemies and enemy plasma bolts.
- `tools`: standalone single-file Java generators that build the `.obj` models:
  - `tools/viewmodel/HandSculpt.java` and `tools/viewmodel/PistolSculpt.java` (see
    [tools/viewmodel/README.md](tools/viewmodel/README.md)).
  - `tools/world/WorldSculpt.java` (`java tools/world/WorldSculpt.java assets/models`).

## Tests

```sh
./gradlew test
```

The tests are JUnit Jupiter tests in `core/src/test` and `desktop/src/test`. They need no window or graphics context.
`GeneratedModelsTest`, `HandModelTest`, `PistolModelTest` and `WorldModelsTest` read `.obj` models from `assets` and
run the standalone generators in `tools/`; the `core` test task tracks both folders and reruns when any model or
generator changes. They cover:

- `GameSimulation`: pistol hits, a charged blast from release to explosion, muzzle launch and crosshair convergence,
  a pause dropping a charge, targets blocking the player, the clamp on long frames, canister explosions chaining and
  hurting the player, a dash dropping a charge and collecting pickups, enemy alerting/damage/kills, headless sound
  events (weapon, target, enemy, pickup, charge-audio and edge-triggered `LOW ENERGY`), and a stage ending when its
  last target breaks.
- `world`: layout parsing (including doors, keycards, consoles, pickups, enemies and exit), the first level (the spawn is clear of walls and every target can be reached), the Training Range and all five campaign stages (progression-aware reachability of targets, enemies, pickups, consoles and exit), collision, hitscan, grid BFS pathfinding (`PathfinderTest`), enemy AI (`EnemyTest`), map geometry, targets
  and the shipped world/enemy `.obj` models (closed outward manifolds matching collision dimensions; pickups stand on
  the floor and fit their pickup radius).
- `player`: view angles and movement, armor absorbing damage, death, and the dash (its cost, distance, cooldown
  and refusal on low energy).
- `hud`: the campaign's path through the menu screens, stage select and menu audio events (`MenuStateTest`), and the
  pixel font's glyphs (`PixelFontTest`).
- `settings`: the settings' ranges (including `masterVolume`) and their round trip through a store, and the saved run,
  best times and ranks (`GameSettingsAndSaveTest`).
- `audio`: procedural WAV header/sample verification, loop-seam continuity and 3D spatial attenuation/panning math
  (`ProceduralSoundsTest`).
- `input`: adding up mouse movement for looking around (`LookAccumulator`).
- `weapon`: the pistol's fire rate, the energy meter, the blast's charge rules, the blast projectile (including impact
  contact bisection) and explosion damage, enemy bolts (`EnemyProjectileTest`), the shipped pistol model (every solid is
  closed, convex and faces outwards), the shipped hand mesh (every part is closed and faces outwards, no quad folds, unit
  normals, bands of the fingers and palm lie on the grip's surface, the fingers wrap the grip, no glove vertex is inside
  the pistol, and `HandSculpt`'s copied outlines match `PistolModel`), and the animation (recoil, slide, walking bob and turn lag).
- `model`: the OBJ parser (including files it must reject), byte-for-byte generator freshness for every `.obj` in
  `assets/models/` (`GeneratedModelsTest`), and model-file error reporting (`ModelVertices.read`).
- `desktop`: parsing `GAMEX_WINDOW` (including its 160x120 to 7680x4320 bounds) and the packaging icons.

Rendering, the drawing of the HUD (`HudRenderer`), OpenAL audio hardware playback (`SoundManager` runtime), the rest
of input handling (`InputController`) and the debug screenshot mode have no tests, apart from the parsing of
`GAMEX_VIEWMODEL_ORBIT` (`DemoRunTest`) and spatial attenuation/panning math (`ProceduralSoundsTest`).

A GitHub Actions workflow (`.github/workflows/build.yml`) runs `./gradlew build` with Java 21 on Linux and Windows on
every pull request and push to `main`.

## Gradle

The project uses the [Gradle](https://gradle.org/) wrapper: run tasks with `./gradlew`, or `gradlew.bat` on
Windows.

- `desktop:run`: starts the game.
- `test`: runs the unit tests.
- `build`: builds and tests every project.
- `desktop:jar`: builds a runnable jar in `desktop/build/libs`.
- `clean`: removes the `build` folders.
- `idea`: generates IntelliJ project data; `cleanIdea` removes it.

A task can be run for one project with a `name:` prefix: `core:clean` removes the `build` folder of `core` only.

Useful flags:

- `--offline`: uses cached dependencies only.
- `--refresh-dependencies`: checks every dependency again and downloads any that changed.
- `--continue`: keeps running tasks after one fails.

# gamex

An original 3D first-person shooter, built with [libGDX](https://libgdx.com/) 1.14.2 on the LWJGL3 desktop backend.

This is the first playable version. You are "gamerx", in a level of four rooms joined in a loop, with a pistol, a
charged blast and 11 destructible targets to practise on. There are no enemies yet, and no win or restart state:
the HUD only counts the targets left. Every texture is painted in code at start-up; the game loads no art files
apart from the window icons.

## How to run

```sh
./gradlew desktop:run
```

On Windows, use `gradlew.bat desktop:run` (`.\gradlew.bat desktop:run` in PowerShell).

The build compiles and runs with a Java 21 toolchain. If Gradle finds no Java 21 on the machine, the first build
downloads one, so it can take a while.

The game opens in a 1280x720 window and starts with the mouse cursor captured.

## Controls

| Input | Action |
| --- | --- |
| `W` `A` `S` `D` | Move forward, left, back, right |
| Mouse | Look around |
| Left mouse button | Fire the pistol; hold to keep firing |
| Right mouse button | Hold to charge the blast, release to fire it |
| `Esc` | Pause and free the cursor; `Esc` again resumes |
| Click in the window | Resume while paused (that click neither fires the pistol nor starts a charge) |
| `Q` | Quit, only while paused |

The game also pauses and frees the cursor when the window loses the focus or is minimised. It stays paused until
you click or press `Esc`. Pausing cancels a blast charge that is building, at no energy cost.

## Gameplay basics

**Pistol.** A hitscan weapon: a shot hits instantly, up to 60 world units away (one map cell is one unit). It has
no ammo limit. With the button held it fires once every 0.3 seconds, at any frame rate, and a shot does 20 damage.

**Blast.** A slow glowing ball that explodes on the first wall, floor, ceiling or target it touches and damages
every target in its radius. Walls do not shield a target from the explosion.

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

**Targets.** The level has 7 canisters (50 health, three pistol shots) and 4 ring boards (120 health, six pistol
shots). A target blocks the player's way until it is destroyed. A destroyed target no longer stops shots, blasts
or the player.

## Debug screenshot mode

For checking a launch with nobody at the keyboard, the game can save one frame as a PNG and quit. It is switched
on by environment variables:

| Variable | Meaning |
| --- | --- |
| `GAMEX_SCREENSHOT` | Path of the PNG to write. Switches the mode on; without it the game runs normally. |
| `GAMEX_SCREENSHOT_FRAME` | The frame to save, counted from 1. Default 60; a non-number prints a warning and uses 60. |
| `GAMEX_DEMO` | Optional script: `action` or `targets`. Without it the player stands still. |

- `action`: the player turns towards a pillar, charges and releases a blast, then fires the pistol.
- `targets`: the player turns towards the nearest canister and fires the pistol at it from frame 30. The third
  shot, around frame 66, destroys it, so a later frame shows it gone.

`GAMEX_SCREENSHOT_FRAME` and `GAMEX_DEMO` do nothing unless `GAMEX_SCREENSHOT` is set. During such a run the
keyboard and mouse are ignored, the cursor stays free, and every frame advances the game by 1/60 of a second, so
the saved frame is the same on every run.

Use an absolute path. The `desktop:run` task runs in the `assets` folder, so a relative path would write the PNG
there.

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
  - `GameSimulation`: the whole game state and its rules. It uses no graphics types, so it runs without a window.
  - `world`: the level grid and its layouts, collision with walls and targets, hitscan ray casting, map
    geometry, targets and effects.
  - `player`: the player's position, view angles and movement, and the per-frame input record.
  - `weapon`: the pistol, the energy meter, the blast's charge logic, its projectile and its explosion damage.
  - `render`: the 3D view and the textures painted in code.
  - `hud`: the 2D overlay (pistol, crosshair, energy meter, charge bar, pause screen) and its built-in pixel font.
  - `input`: reads the keyboard and mouse, adds up mouse movement for looking around (`LookAccumulator`), and
    handles pause and cursor capture.
  - `debug`: the debug screenshot mode.
- `desktop`: the LWJGL3 launcher (`DesktopLauncher`), the window settings and the window icons.

## Tests

```sh
./gradlew test
```

The tests are JUnit Jupiter tests in `core/src/test`. They need no window or graphics context. They cover:

- `GameSimulation`: pistol hits, a charged blast from release to explosion, a pause dropping a charge, targets
  blocking the player, and the clamp on long frames.
- `world`: layout parsing, the first level (the spawn is clear of walls and every target can be reached),
  collision, hitscan, map geometry and targets.
- `player`: view angles and movement.
- `input`: adding up mouse movement for looking around (`LookAccumulator`).
- `weapon`: the pistol's fire rate, the energy meter, the blast's charge rules, and the projectile and its
  explosion damage.

Rendering, the HUD, the rest of input handling (`InputController`) and the debug screenshot mode have no tests.

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

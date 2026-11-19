# gamex

An original 3D first-person shooter, built with [libGDX](https://libgdx.com/) 1.14.2 on the LWJGL3 desktop backend.

This is an active campaign & range build. You are "gamerx", equipped with an infinite-ammo pistol, a charged energy
blast, a pump-action **Combat Scattergun** with 2-shell double-blast alt-fire, a piercing **Rail Lance** with 1.8x
tactical zoom optic, a free physical jump, and a horizontal energy dash. Battle four hostile archetypes (three robots, **Rusher Drones**, **Sentry Turrets** and
**Guard Bots**, and the two-phase melee boss **the Warden**), jump or dash over live **Shock Grates**, chain explosive coolant canisters, breach sliding bulkhead doors, collect Amber and Cyan
keycards, override Security Consoles, collect 3D health, armor, energy, weapon, shell and slug pickups, and clear the 6-stage campaign
(**01: Outer Breach**, **02: Coolant Core**, **03: Security Hub**, **04: Lockdown Wing**, **05: Command Vault**, **06: Warden Core**) across
Easy, Normal or Hard difficulty — or unlock and replay stages from **Select Stage** and practise in the **Training
Range**. Surface and effect textures are synthesised in code at start-up, while every map and campaign stage is loaded from
`assets/levels/`, every weapon, combat and menu sound is loaded from a 16-bit mono PCM `.wav` file in `assets/sounds/`, and
every 3D graphical object — the pistol, combat scattergun, rail lance, gloved hand holding them, targets, enemies, projectiles, beam trails,
pickups, bulkheads, shock grates, keycards and security consoles — is loaded from a Wavefront `.obj` model in `assets/models/`. The Warden is the exception: a skinned, animated binary glTF model (`enemy-warden.glb`, stored with Git LFS) drawn with [gdx-gltf](https://github.com/mgsx-dev/gdx-gltf); it is loaded the first time a stage with a Warden is shown.

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

The Warden's model, `assets/models/enemy-warden.glb`, is a [Git LFS](https://git-lfs.com/) file. Install Git LFS and
run `git lfs install` before cloning, or `git lfs pull` afterwards. Without it the file is a small pointer file: the
game stops as it starts, on any stage, with the error `The warden model models/enemy-warden.glb is a Git LFS pointer
file, not the model itself. Install Git LFS, then run 'git lfs install' and 'git lfs pull' in the repository; in CI,
check out with 'lfs: true'.`, and `./gradlew test` fails with the same reason in every test of `WardenModelTest` that reads the model.

The game opens in a 1280x720 window, on the Title screen, with the mouse cursor free. If fullscreen was switched
on in the settings on an earlier run, it opens fullscreen instead. Two environment variables keep it in a window
whatever the saved setting says, and leave that setting as it is: `GAMEX_WINDOW`, which sets the window size, and
`GAMEX_SCREENSHOT` (both under [Debug screenshot mode](#debug-screenshot-mode)). The cursor is captured while you
play and is freed whenever a menu is up.

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
| `Esc` | Go back: Pause resumes play, Settings and the difficulty screen return to where they were opened, others return to the Title |
| `R` | Restart the stage on the pause menu or the death screen |
| `Q` | Quit from the Title or Pause screen: the first press shows `PRESS Q AGAIN TO QUIT`, a second press within about 2 seconds quits |

On the Settings screen a click on the left half of a setting's row steps it down and a click on the right half
steps it up. An on/off setting flips either way. A key press or a click acts once, however long it is held. The
`QUIT` row quits at once, without the second press that `Q` needs.

The game also pauses and frees the cursor when the window loses the focus or is minimised during play. It stays
paused until you press `Esc` or choose `RESUME`; a click anywhere else does not resume it. Pausing cancels a blast
charge that is building, at no energy cost, and drops the Rail Lance zoom. A mouse button that is still down when
play starts or resumes does nothing until it is released, so the click that chose `RESUME` or `DEPLOY` does not fire
a shot. A `Space` still held from choosing a row is ignored the same way, so it does not jump.

## Gameplay basics

**Menus and stages.** The Title screen offers `START CAMPAIGN`, `SELECT STAGE`, `TRAINING RANGE`, `SETTINGS` and
`QUIT`, and `CONTINUE CAMPAIGN` when a campaign run is saved. `START CAMPAIGN` asks for a difficulty and shows the
briefing of the first stage; `DEPLOY` starts it. `SELECT STAGE` lists all six campaign stages and starts a fresh run,
the same way, from any unlocked one:

- A stage you have not reached yet reads `[LOCKED]`; an unlocked one reads `[READY]` until you clear it.
- A cleared stage shows its best rank and its fastest clear, each taken over all three difficulties, with the
  difficulty the fastest clear was made on: `[BEST: S] 1:42 (HARD)`.
- Under the list, the highlighted stage shows how many medals it has on each difficulty, once it has any:
  `MEDALS: EASY 2/4  NORMAL 4/4  HARD 0/4`.

**The saved run.** `CONTINUE CAMPAIGN` resumes the one saved campaign run. It starts the saved stage straight away,
without the briefing.

| When | What happens to the saved run |
| --- | --- |
| You pick a difficulty | Nothing. Backing out of the briefing keeps the run that was saved before. |
| You choose `DEPLOY` on a campaign briefing | The stage, the difficulty and the health, armor, weapons and ammo you enter with are saved, replacing any saved run. |
| You clear a stage other than the last | The next stage is saved at once, with the health, armor, weapons and ammo you finished with. Leaving the results screen without `NEXT STAGE` loses nothing. |
| You choose `PLAY AGAIN` on the results screen | Nothing. The stage is replayed with what you entered it with. Clearing the replay saves the next stage again, with what you finished the replay with. |
| You die, restart or return to the Title | Nothing. |
| You clear the final stage | The saved run is removed. |
| You play the Training Range | Nothing. |

Where the run is stored, and how to reset it, is under [Saved settings and progress](#saved-settings-and-progress).

**Stages.**

- **01: Outer Breach** introduces Rusher Drones and Sentry Turrets and ends at the extraction pad.
- **02: Coolant Core** adds mobile Guard Bots across multi-lane reactor conduits.
- **03: Security Hub** combines coordinated squads of all three archetypes and unlocks the **Rail Lance** (`R` / `r`) ahead of its long combat corridors.
- **04: Lockdown Wing** introduces sliding bulkhead doors (`=`), Amber keycard wing gating (`k` -> `A`), and a Security Console lockdown override (`S` -> `L`) protecting extraction and ambush bays.
- **05: Command Vault** chains Amber and Cyan keycard clearances (`k` -> `A`, `c` -> `C`) to a vault Security Console override and flanking ambush.
- **06: Warden Core** introduces live two-cell **Shock Grates** (`~`), adds an Amber keycard door (`k` -> `A`) and a Security Console that overrides two lockdown doors (`S` -> `L`), and culminates in the two-phase boss **the Warden** (`B`) sealing the `M` extraction bulkhead before `CAMPAIGN COMPLETE`.
- The **Training Range** includes practice targets, a one-cell Shock Grate that a plain jump clears, and both the Scattergun and Rail Lance racks, ending when every target is destroyed (always played on Normal).

**Objective.** A line near the top left of the HUD names the next step towards the exit and follows your progress:
`FIND THE AMBER KEYCARD` or `FIND THE CYAN KEYCARD`, `OVERRIDE THE SECURITY CONSOLE`, `DESTROY THE WARDEN`, then
`REACH EXIT PAD`. When more than one step is open, the one you can walk to comes first. The Training Range shows
`DESTROY ALL TARGETS`. On a window narrower than about 16:9 the line sits a little lower, so that it clears the boss
bar and the pickup messages.

**Results.** A cleared stage shows:

- the clear time against par;
- the best time for the difficulty just played, as in `BEST (NORMAL): 01:42.3`, with `*NEW BEST!*` when this run
  set it;
- hostiles cleared on combat stages, or targets cleared in the Training Range;
- damage taken (rounded to the nearest whole number, and never shown as `0` once you took any), pickups collected
  out of the stage's total, and accuracy (rounded down; `--` if you never fired);
- a rank from `S` down to `C`, beside your best rank on that difficulty;
- the medals the run earned, with the ones earned for the first time between asterisks: `MEDALS: *UNTOUCHED*  SWEEP`.

The best time, best rank and medals are saved for each stage and difficulty.

**Medals.** Each campaign stage has four medals on each difficulty. They are separate from the rank, and a medal
once earned is kept, whatever later runs do.

| Medal | Earned by clearing the stage |
| --- | --- |
| `UNTOUCHED` | Without losing any health or armor |
| `SWEEP` | With every hostile destroyed |
| `UNDER PAR` | In less than the par time; a clear exactly on par does not count |
| `SHARPSHOOTER` | With at least 60% of your shots hitting |

A shot is one trigger pull: a pistol shot, a charged blast, a Scattergun blast (one shell or two) or a rail beam. It
is a hit if it damages at least one hostile, ring board or canister, however many pellets land and however many
targets a beam pierces. A charged blast becomes a hit only when its explosion damages one of them. The Training Range awards
no medals.

**Settings.** The settings are mouse sensitivity, inverted look, field of view, view bob, master volume and
fullscreen. They are saved between runs. Opened from the Title, the Settings screen keeps the Title's moving
backdrop; opened from the pause menu, it stays over the paused game.

**Health and armor.** You start a campaign with 100 health and no armor; both hold at most 100. While armor is
left it absorbs half of each hit and the rest comes off health. Enemy attacks, close-range blasts and detonating
canisters all deal directional damage. At zero health the view drops to the floor and the death screen follows;
retrying, like `R`, restarts the stage with the health, armor, weapons and ammo you entered it with. Dying on the
very frame you reach the exit, or break the last target, is a death, not a clear.

**Difficulty.** Easy scales the damage you take to 70%, pickups to 125%, and enemy attack speed/bolt speed to 85%;
Hard scales damage taken to 140%, pickups to 80%, and enemy attack speed/bolt speed to 120% while keeping enemy hit
points constant across difficulties.

**Jump & Dash.** `Space` performs a free physical jump (`0` energy, `~0.32u` apex below the `1.0u` ceiling, `~0.48s` hang time) with full `WASD` air control and a `0.10s` pre-landing jump buffer that is the same at any frame rate (press-per-jump, no double-jump). Jumping elevates your 3D camera, weapon aim origin, and vertical hitbox (`[feetY, topY]`), clears floor-level **Shock Grates** (`~`) while airborne, and leaves Pistol Blast charging and Rail Lance `1.8x` zoom active for jump-shots. `Shift` triggers a horizontal **Energy Dash** (`0.14s` at `11.5 u/s`, ~`1.6u`) along your movement keys (or straight ahead when standing still): it costs `25` energy on a `0.35s` cooldown, cancels active Blast charges or Rail zoom, grants `100%` Shock Grate immunity, and suspends downward fall when chained mid-air. A plain jump carries you over a one-cell grate, as in the Training Range. A two-cell grate, as in **06: Warden Core**, needs a jump with a dash in mid-air: along its length a jump alone comes down on the grate, and a dash alone ends on it. With less than `25` energy a dash is refused and the HUD shows `LOW ENERGY`.

**Pistol, Combat Scattergun & Rail Lance.** All three weapons share the lower-right 3D viewmodel pass and gloved hand so walls never clip them. Switch at any time with `1`, `2`, `3`, `Q` (quick-swap last weapon) or the mouse wheel (cycle owned weapons). A swap takes `0.18s`, and no weapon fires until it ends. Asking for a third weapon during a swap starts the swap again; asking for the weapon you are leaving reverses it from where it has got to.

- **Pistol (`1`)**: Instant hitscan (`20` damage, `0.3s` interval, up to `60` world units away, infinite ammo). Right mouse button charges the violet **Energy Blast** orb.
- **Combat Scattergun (`2`)**: Unlocked in **01: Outer Breach**, **06: Warden Core**, and the **Training Range** (and equipped directly when deploying to Stages `02`–`06` from Select Stage). Draws from a carried `24`-shell pool fed by Scattergun (`+8`) and Shell Caddy (`+6`) pickups scaled by difficulty:
  - **Primary fire (LMB, `1` shell)**: `8` deterministic pellets across a `4.2°` half-angle cone (`12` base damage each out to `7u`, falling in a straight line to `35%` at the `26u` max range, `0.75s` pump cycle).
  - **Double-Blast alt-fire (RMB, `2` shells)**: `16` deterministic pellets: one in the centre, an inner ring of `5` at `3.4°` and an outer ring of `10` at `6.8°` (`1.15s` pump recovery; fires a `1`-shell blast if only `1` shell remains).
  - Pulling the trigger at `0` shells emits a dry-fire click and smart-swaps automatically: to your previous weapon if it is loaded, otherwise to a loaded Rail Lance, otherwise to the Pistol.
- **Rail Lance (`3`)**: Unlocked in **03: Security Hub**, **06: Warden Core**, and the **Training Range** (and unlocked directly in Slot 3 when deploying to Stages `04`–`06` from Select Stage). Draws from a carried `15`-slug pool fed by Rail Lance (`+5`) and Slug Pack (`+3`) pickups scaled by difficulty:
  - **Primary fire (LMB, `1` slug)**: Instant `60u` piercing hitscan beam (`1.10s` capacitor recharge, `0` distance falloff) that penetrates every live target and enemy in line (`120` damage; `90` vs the Warden's hide), stopping only at solid walls, closed bulkheads, or Security Consoles and leaving a fading 3D beam wake.
  - **Tactical Zoom Optic (hold RMB, `0` cost)**: Smooth `1.8x` FOV magnification with proportional look-sensitivity scaling, centered low-ADS framing, rangefinder HUD brackets, full arena move speed, and instant zoom cancel on Dash, weapon swap or pause.
  - Pulling the trigger at `0` slugs emits a dry-fire click and smart-swaps automatically: to your previous weapon if it is loaded, otherwise to a loaded Scattergun, otherwise to the Pistol.

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

- It refills at 14 per second with any weapon in hand and during a weapon swap, but not while a Pistol blast charge
  is building.
- A charge stops growing at what the meter can pay for.
- With less than 20 energy a charge cannot start. The HUD shows `LOW ENERGY`, and the meter keeps refilling even
  while the button is held.
- Blasts and dashes are paid for from the same meter.

**Targets, Enemies & Pickups.** Levels contain coolant canisters (50 health, three pistol shots) that detonate in
chain-reacting area explosions when destroyed, ring boards (120 health, six pistol shots), and four hostiles, three robots and the Warden:

- **Rusher Drone** (40 health): closes distance around cover via grid pathfinding and telegraphs a shock lunge that
  can be sidestepped with Energy Dash.
- **Sentry Turret** (80 health): anchored base with a swiveling dual-barrel head that winds up and fires 3-bolt
  bursts.
- **Guard Bot** (100 health): mobile skirmisher that avoids Shock Grates, strafes at medium range and fires single aimed plasma bolts.
- **The Warden** (900 health): two-phase melee brute tracked by a top-center health bar; it has no ranged attack. It stands until it notices you, walks (`1.3 u/s`) along the way to a player it cannot see and runs (`3.7 u/s`, faster than your `3.2`) at one it can. Within `0.9u` and facing you it throws a `2.47s` three-punch combo: a hook at `0.60s`, a backhand at `1.10s` and an uppercut at `1.27s` (`16` / `16` / `28` damage), each warned of by a flare at its chest and each carried by a lunge along its facing that stops at the player (up to `2.3u`, `0.7u`, `0.7u`). A punch lands on a player within `1.1u` and `50` degrees of its facing at that moment, and it turns only slowly (`45` deg/s) while it punches, so a sidestep or an Energy Dash avoids all three; walking straight back does not, because the first lunge catches you. After the combo it stands open for `0.9s`. At `<= 50%` HP it enters **Overdrive** (a blue glow): it runs `25%` faster, turns `50%` faster, punches `35%` harder, throws the combo `15%` faster and recovers in `60%` of the time. The times and damage here are those on Normal: the difficulty scales the speed of the combo, the length of the recovery and the damage of the punches, as it does other enemy attacks, but not how fast the Warden walks or runs.

Drones, Guard Bots and the Warden walk round canisters, ring boards, Security Consoles, turrets and each other
instead of stopping against them, and they turn doorway corners without catching on them.

**Shock Grates, Bulkheads, Keycards & Consoles.** Live Shock Grate floor tiles (`~`) deal periodic arc ticks while walked on, while **jumping (`Space`) clears floor grates while airborne** and **Energy Dash (`Shift`) grants 100% immunity** (`0` damage) for as long as the dash lasts. Guard Bots detour around `~` grates while Drones and the Warden traverse them freely. Sliding steel bulkhead doors (`=`) open permanently with `E`; Amber (`k`) and Cyan (`c`) keycards unlock matching `A` / `C` bulkheads; `S` Security Consoles override `L` lockdown bulkheads; and defeating every **Warden** automatically unseals all `M` boss extraction bulkheads.

## Saved settings and progress

The game keeps two files in the `.prefs` folder of your home directory: `~/.prefs/` on Linux and macOS,
`%USERPROFILE%\.prefs\` on Windows.

| File | Holds |
| --- | --- |
| `io.github.brantunger.gamex.settings` | The six settings |
| `io.github.brantunger.gamex.save` | The saved campaign run, the stages unlocked, and the best time, best rank and medals of each stage and difficulty |

To reset, close the game and delete the file: without `.settings` the settings go back to their defaults, and
without `.save` there is no run to continue, only the first stage is unlocked and every record is gone.

Both are XML property files that can be edited by hand. A value the game cannot use does not stop it:

- a number that is not a number reads as its default, and a setting out of range is pulled back into range;
- an unknown difficulty reads as Normal and an unknown weapon as the Pistol;
- a best time that is not a number above zero counts as no best time, so the next clear replaces it.

## Debug screenshot mode

For checking a launch with nobody at the keyboard, the game can save one frame as a PNG and quit. It is switched
on by environment variables:

| Variable | Meaning |
| --- | --- |
| `GAMEX_SCREENSHOT` | Path of the PNG to write. Switches the mode on; without it the game runs normally. |
| `GAMEX_SCREENSHOT_FRAME` | The frame to save, counted from 1. Default 60; a non-number logs a warning and uses 60. |
| `GAMEX_DEMO` | Optional script: `action`, `targets`, `breach`, `title`, `lockdown`, `stages`, `scattergun`, `raillance`, `warden`, `wardenidle` or `jump`. Without it the player stands still. |
| `GAMEX_VIEWMODEL_ORBIT` | Optional: `yaw` or `yaw,pitch` in degrees. Draws only the weapon in hand and the hand, from outside. |

- `action`: the player turns towards a pillar, charges and releases a blast, then fires the pistol.
- `targets`: the player turns towards the nearest canister and fires the pistol at it from frame 30. The third
  shot, around frame 66, destroys it, so a later frame shows it gone.
- `breach`: on the Outer Breach stage, the player rushes and dashes east through the breach doorway, strafes past
  turret fire, turns toward the room's Drone and Turret and fires the pistol.
- `title`: the saved frame shows the Title screen over its moving view of the Outer Breach stage. Nothing is
  played, and `GAMEX_VIEWMODEL_ORBIT` is ignored.
- `stages`: the saved frame shows the Select Stage screen with all six stages unlocked, over a moving view of the
  Lockdown Wing stage. Nothing is played, and `GAMEX_VIEWMODEL_ORBIT` is ignored.
- `lockdown`: on the Lockdown Wing stage, the player walks up to the first bulkhead, opens it on frame 25 and walks
  on from frame 45.
- `scattergun`: in the Training Range, holding the Scattergun with 16 shells, the player walks forward and fires one
  blast on frame 24.
- `raillance`: on the Security Hub stage, holding the Rail Lance, the player holds the zoom from frame 6 to frame 38
  and fires a beam on frame 24.
- `warden`: on the Warden Core stage, placed in the Warden's arena with the Scattergun, the player pulls the trigger
  on frames 1 and 3, which fires one blast and wakes the Warden, walks forward and then stands. The Warden runs at
  them from frame 1 (frame 40 shows it in mid-run), starts its combo on frame 65, lands its punches on frames 101,
  132 and 142 and stands open from frame 214 to frame 268; its next combo starts on frame 269.
- `wardenidle`: on the Warden Core stage, with every hostile but the Warden removed, the player stands behind the
  Warden's shoulder, out of its sight and hearing, and does nothing. Any frame shows the Warden standing as it
  does before a fight, with no boss bar; `HOSTILES` reads `1/8`.
- `jump`: in the Training Range, the player walks at the Shock Grate and jumps it on frame 19, without a dash and
  without taking damage.

`action`, `targets` and a run without a script start in play on the original first level, which has no pickups and
no exit. The script name is not case-sensitive, and an unknown name is the same as no script.

With `GAMEX_VIEWMODEL_ORBIT` the saved frame shows the weapon in hand and the hand alone, at rest, on a plain grey
background, without the world or the HUD. That weapon is the Pistol, except in the `scattergun`, `warden`, `wardenidle` and `jump`
scripts (the Scattergun) and the `raillance` script (the Rail Lance). A yaw of 0 shows them from behind, 90 from the left side and 180 from the front; a
pitch of 90 shows them from above, and the pitch is 0 if left out. A value that is not a number logs a warning
and the normal picture is saved.

`GAMEX_SCREENSHOT_FRAME`, `GAMEX_DEMO` and `GAMEX_VIEWMODEL_ORBIT` do nothing unless `GAMEX_SCREENSHOT` is set.
During such a run the keyboard and mouse are ignored, the cursor stays free, sound output is muted, and every frame
advances the game by 1/60 of a second, so the saved frame is the same on every run. Saved settings and campaign
progress are neither read nor written: the run uses the default settings and Normal difficulty.

Use an absolute path. The `desktop:run` task runs in the `assets` folder, so a relative path would write the PNG
there.

One more variable works on any run, with or without `GAMEX_SCREENSHOT`: `GAMEX_WINDOW` sets the window size as
`WIDTHxHEIGHT`, for example `800x600`. A value that is malformed, smaller than 160x120 or larger than 7680x4320 logs a
warning and the window opens at 1280x720. Whenever the variable is set, even to such a value, the game stays in a
window although fullscreen is saved on. The saved setting is not changed, so the Settings screen still reads
`FULLSCREEN: ON`.

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

## Logging

The game logs to the console through SLF4J and Logback, on standard error, one line per event. A few
warnings and errors (the version file or the saved settings could not be read, the macOS restart failed) are
followed by the exception's stack trace:

```
14:02:11.482 INFO  DesktopLauncher - Starting gamex 1.0.0: window 1280x720, log level INFO
```

`GAMEX_LOG_LEVEL` sets how much is logged. Like `GAMEX_WINDOW`, it works on any run:

| Value | What is logged |
| --- | --- |
| unset, blank, or `info` | Startup and shutdown, the screens shown, the assets loaded (one line for each batch, and one for the Warden model when it is first loaded), settings read, saved progress read and written, levels started and cleared, deaths, boss phases, best times and medals, display mode switches; and every warning. |
| `debug` | All of that, plus each model, sound and stage file loaded, the meshes, textures and animations of the Warden model, hostiles spawned and killed, the Warden's punches and its changes of animation, targets destroyed, pickups, console overrides, objective changes, menu navigation, settings changes, settings saved and window resizes. |
| `warn`, `error` | Only warnings and errors, or only errors. |
| `trace` | The same as `debug`; the game logs nothing at that level. |

The value can be in any case, with spaces around it. Any other value logs a warning and the level is `info`.

A warning is logged for most things the game falls back on: a sound that does not load, an environment variable it
cannot read, a saved number that is not a number (once for each key), a saved run with an unknown difficulty. An
unknown saved weapon, a setting out of range and an unknown `GAMEX_DEMO` name are replaced without one.

Nothing is logged for a frame in which nothing happened, and nothing is written to a file.

PowerShell:

```powershell
$env:GAMEX_LOG_LEVEL = "debug"
.\gradlew.bat desktop:run
Remove-Item Env:GAMEX_LOG_LEVEL
```

bash:

```sh
GAMEX_LOG_LEVEL=debug ./gradlew desktop:run
```

The `tools` programs print their own output and show only the warnings and errors of the game classes they use.

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
  - `render`: the 3D view (`WorldRenderer` with `u_normalMatrix` and `u_accentColour` shader uniforms), the pass that draws the weapon and hand over it (`ViewmodelRenderer`), the Warden's skinned glTF model drawn with gdx-gltf's PBR shader (`WardenModel`) and the choice of its animation (`WardenPose`), and the textures painted in code.
  - `hud`: the 2D overlay (crosshair and hit markers, health, armor and energy bars, charge bar, status texts and
    the menu screens), the screen and menu state machine behind those screens (`MenuState`, which uses no graphics
    or input types) and the built-in pixel font.
  - `settings`: the player's settings (`GameSettings`), the saved campaign run and best records (`SaveData`), the
    medals (`Medal`), what a finished run hands to the results screen (`RunStats`), and the key-value store they are
    kept in: libGDX preferences in the game, a map in tests and debug runs.
  - `input`: reads the keyboard and mouse for play and for the menus, adds up mouse movement for looking around
    (`LookAccumulator`), and handles pause and cursor capture.
  - `debug`: the debug screenshot mode.
- `desktop`: the LWJGL3 launcher (`DesktopLauncher`), the window settings, the window icons, and the log level and
  console log configuration (`LogLevel`, `logback.xml`).
- `tools`: the Gradle model and audio authoring and inspection subproject (see [tools/README.md](tools/README.md)): `MeshBuilder`
  DSL, `PropSculpt`, `EnemySculpt`, `PistolSculpt`, `ScattergunSculpt`, `RailLanceSculpt`, `HandSculpt`, `GenerateAllModels`,
  `SoundSynth`, `GenerateAllSounds`, and the interactive LWJGL3 previewer (`ModelPreviewApp`).
- `assets`: the files the game loads, at start-up except where noted:
  - `levels/*.map`, `levels/campaign.txt`: plain-text map layouts, stage metadata/briefings, and ordered campaign manifest.
  - `sounds/*.wav`: 16-bit mono PCM `.wav` files for every weapon, combat, world, pickup and menu cue plus the seamless `charge-hum.wav` blast-charge loop.
  - `models/viewmodel-hand.obj`: the mesh of the hand, wrist, cuff and sleeve.
  - `models/viewmodel-pistol.obj`, `models/viewmodel-scattergun.obj`, `models/viewmodel-raillance.obj`: the meshes of the first-person pistol (body and slide), shortened double-barrel drum combat scattergun (body and lever forend), and rail lance (body and magnetic bolt).
  - `models/canister.obj`, `models/ring-board.obj`, `models/blast-orb.obj`, `models/rail-beam.obj`, `models/pickup-*.obj`: 3D meshes for
    targets, pickups (medkit, armor, energy cell, keycards, scattergun, shells, rail lance, slugs), blast projectiles and rail beam trails.
  - `models/door-bulkhead.obj`, `models/security-console.obj`, `models/hazard-grate.obj`: 3D meshes for sliding bulkhead doors, security consoles and shock grates.
  - `models/enemy-turret.obj`, `models/enemy-drone.obj`, `models/enemy-guard.obj`,
    `models/enemy-bolt.obj`: 3D meshes for articulated enemies and enemy plasma bolts.
  - `models/enemy-warden.glb`: the Warden, a binary glTF with a 28-joint skin, the `Stand_Talking_Angry`, `Walking`,
    `Running` and `Punch_Combo` animations and three 4096-pixel textures. It is loaded the first time a stage with a
    Warden is shown, and checked to be there when the game starts. It is a Git LFS file: without Git LFS the game
    and the tests find a small pointer file in its place and stop on it, saying so (see [How to run](#how-to-run)).

## Tests

```sh
./gradlew test
```

The tests are JUnit Jupiter tests in `core/src/test`, `desktop/src/test`, and `tools/src/test`. They need no window or
graphics context; the few that touch libGDX's `Gdx` globals put stubs there (`InputControllerTest`,
`MainGameViewportTest`, `SoundManagerPartialLoadTest`, `LevelsTest`). The `core` tests read `.obj` models, the Warden's `.glb` model, `.wav` sounds and `.map`
levels from `assets/models`, `assets/sounds` and `assets/levels`, and `core/build.gradle` tracks those folders, so
editing an asset reruns them. They cover:

- `GameSimulation` (`GameSimulationTest`): pistol hits, a charged blast from release to explosion, muzzle launch and
  crosshair convergence, a pause dropping a charge, targets blocking the player, the clamp on long frames, canister
  explosions chaining and hurting the player, a dash dropping a charge and collecting pickups, enemy
  alerting/damage/kills, headless sound events, weapon swaps, the energy refill, jumps over one-cell and two-cell
  Shock Grates, a stage ending when its last target breaks, a death on the frame the exit is reached, and no enemy
  seeing the spawn point on any campaign stage.
- The HUD objective line (`ObjectiveTextTest`) and the shot and hit counts behind the accuracy figure
  (`ShotAccuracyTest`).
- `MainGame` (`MainGameViewportTest`): the GL viewport on resize and on HiDPI displays, the windowed size to fall
  back to, a forced windowed start, and which screens draw the backdrop.
- `world`: layout and `.map`/manifest parsing (`GameMapTest`, `LevelsTest`, including doors, keycards, consoles, pickups, enemies, hazards and exit), the first level (the spawn is clear of walls and every target can be reached), the Training Range and all six campaign stages (progression-aware reachability of targets, enemies, pickups, consoles and exit), collision, standard & piercing hitscan (`HitscanTest`), grid BFS pathfinding (`PathfinderTest`), enemy AI (`EnemyTest`), enemies rounding doorway corners, canisters and turrets and guards keeping off Shock Grates (`EnemyMovementTest`), map geometry, targets
  and the shipped world/enemy `.obj` models (closed outward manifolds matching collision dimensions; pickups stand on
  the floor and fit their pickup radius).
- `render`: the Warden model (`WardenModelTest` reads the JSON part of `enemy-warden.glb`: its four animations, its
  one 28-joint skinned mesh, its height against the Warden's collision height, and the length of its combo clip
  against the simulation's combo; and checks that only a stage with a live Warden loads the model, and the Warden's
  glow), and which animation each enemy state shows and how fast it plays for the feet not to slide
  (`WardenPoseTest`).
- The Warden's melee (`WardenCombatTest`): its walk without sight and run with it, the three punches at their clip
  times, reach and frontal arc, the lunges and walls, the telegraph, walking back, sidestepping and dashing, and
  what overdrive and difficulty change. `WardenFightTest` fights it through the whole simulation with player input:
  it reaches a player anywhere in its arena on Warden Core, runs down one who only backpedals, and falls to the
  pistol, the scattergun and the rail lance each on their own.
- `player`: view angles and movement, armor absorbing damage, death, the dash (its cost, distance, cooldown
  and refusal on low energy), and the jump (its arc, the mid-air dash, and the jump buffer at low frame rates).
- `hud`: the menu screens, the saved run, quick restart and the `Q` quit prompt (`MenuStateTest`,
  `MenuStateFlowTest`), the Select Stage rows and the lines of the results screen, including that they fit their
  panels (`StageRecordsDisplayTest`), when the combat HUD is shown (`HudRendererTest`), and the pixel font's glyphs
  (`PixelFontTest`).
- `settings`: the settings' ranges (including `masterVolume`) and their round trip through a store, the saved run,
  best times and ranks (`GameSettingsAndSaveTest`), the medals (`MedalTest`), and damaged or hand-edited settings and
  save values (`CorruptPrefsTest`).
- `audio`: shipped `.wav` asset coverage/format verification, loop-seam continuity, muted headless draining and 3D
  spatial attenuation/panning math (`SoundManagerTest`), and sound files that fail to load
  (`SoundManagerPartialLoadTest`).
- `input`: adding up mouse movement for looking around (`LookAccumulatorTest`), and menu keys and clicks acting once
  per press, `R`, `Q`, and held buttons and `Space` being ignored when play resumes (`InputControllerTest`).
- `weapon`: the pistol, combat scattergun (`ScattergunTest`: deterministic 8/16 pellet cones, range falloff, 1-shell fallback, pump curve), and rail lance (`RailLanceTest`: cooldown, bolt cycle, smooth 1.8x zoom optic & cancel latch), the energy meter, the blast's charge rules, the blast projectile (including impact
  contact bisection) and explosion damage, enemy bolts (`EnemyProjectileTest`), the shipped pistol, scattergun & rail lance models (`PistolModelTest`, `ScattergunModelTest`, `RailLanceModelTest`: closed convex outward solids and zero hand-glove penetration at rest and full slide/pump/bolt travel), the shipped hand mesh (every part is closed and faces outwards, no quad folds, unit
  normals, bands of the fingers and palm lie on the grip's surface, the fingers wrap the grip, no glove vertex is inside
  the pistol, and `HandSculpt`'s copied outlines match `PistolModel`), and the animation (recoil, slide, walking bob and turn lag).
- `model`: the OBJ parser (including files it must reject) and model-file error reporting (`ModelVertices.read`).
- `debug`: the parsing of `GAMEX_VIEWMODEL_ORBIT` and the `jump` demo script (`DemoRunTest`).
- logging: a cleared level, menu navigation, a settings change, a best time, medals and a damaged saved run each log
  their line at the right level, and frames in which nothing happens log nothing (`GameLoggingTest`).
- `desktop`: parsing `GAMEX_WINDOW` (including its 160x120 to 7680x4320 bounds), when the game starts fullscreen,
  the packaging icons, when and how `StartupHelper` relaunches the JVM on macOS, and reading `GAMEX_LOG_LEVEL` and
  the level `logback.xml` then runs at (`LogLevelTest`).
- `tools` (`:tools:test`): `GeneratedModelsTest` and `GeneratedSoundsTest` run `GenerateAllModels` and
  `GenerateAllSounds` and check that every shipped `.obj` and `.wav` matches the generators byte for byte;
  `GeneratedModelsTest` also checks the indexed vertex deduplication.

Rendering, the drawing of the HUD and the menus on screen, OpenAL audio hardware playback and the other demo scripts
have no tests.

A GitHub Actions workflow (`.github/workflows/build.yml`) runs `./gradlew build` with Java 21 on Linux, Windows and
macOS on every pull request and push to `main`.

## Gradle

The project uses the [Gradle](https://gradle.org/) wrapper: run tasks with `./gradlew`, or `gradlew.bat` on
Windows.

- `desktop:run`: starts the game.
- `tools:generateModels`: regenerates every sculpted `.obj` model in `assets/models/`.
- `tools:generateSounds`: regenerates every synthesized `.wav` sound effect in `assets/sounds/`. To add a sound, see
  [Adding a Sound](tools/README.md#adding-a-sound).
- `tools:preview`: launches the interactive 3D OBJ model previewer (`R` rebuilds & reloads live).
- `test`: runs the unit tests across `core`, `desktop`, and `tools`.
- `build`: builds and tests every project.
- `clean`: removes the `build` folders.
- `idea`: generates IntelliJ project data; `cleanIdea` removes it.

### Packaging

- `desktop:jar`: builds a runnable jar, `gamex-1.0.0.jar`, in `desktop/build/libs`. It holds the game, its assets and
  every dependency. `desktop:dist` does the same.
- `desktop:jarWin`, `desktop:jarLinux`, `desktop:jarMac`: the same jar with the native libraries of one system only
  (`gamex-1.0.0-win.jar`, `-linux`, `-mac`).
- `desktop:distZip`, `desktop:distTar`: an archive in `desktop/build/distributions` that holds only that jar, in
  `lib/`, and the start scripts `bin/gamex` and `bin/gamex.bat`.
- `desktop:packageWinX64`, `desktop:packageLinuxX64`, `desktop:packageMacM1`, `desktop:packageMacX64`: Construo
  bundles of the game with its own Java 21 runtime, so the player needs no Java. Each downloads a JDK for its target.

### Running tasks

A task can be run for one project with a `name:` prefix: `core:clean` removes the `build` folder of `core` only.

Useful flags:

- `--offline`: uses cached dependencies only.
- `--refresh-dependencies`: checks every dependency again and downloads any that changed.
- `--continue`: keeps running tasks after one fails.
- `--configuration-cache`: reuses the configured build on the next run. The build works with it; it is not switched
  on by default.

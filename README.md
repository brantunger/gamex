# gamex

An original 3D first-person shooter, built with [libGDX](https://libgdx.com/) 1.14.2 on the LWJGL3 desktop backend.

This is an active campaign & range build. You are "gamerx", equipped with an infinite-ammo pistol, a charged energy
blast, a pump-action **Combat Scattergun** with 2-shell double-blast alt-fire, a piercing **Rail Lance** with 1.8x
tactical zoom optic, a free physical jump, and a horizontal energy dash. Battle four hostile archetypes (three robots, **Rusher Drones**, **Sentry Turrets** and
**Guard Bots**, and the two-phase melee boss **the Warden**), jump or dash over live **Shock Grates**, chain explosive coolant canisters, breach sliding bulkhead doors, collect Amber and Cyan
keycards, override Security Consoles, collect 3D health, armor, energy, weapon, shell and slug pickups, and clear the 6-stage campaign
(**01: Outer Breach**, **02: Coolant Core**, **03: Security Hub**, **04: Lockdown Wing**, **05: Command Vault**, **06: Warden Core**) across
Easy, Normal or Hard difficulty — or unlock and replay stages from **Select Stage** and practise in the **Training
Range**. Surface and effect textures are loaded from `.png` files in `assets/textures/` and the world, model and viewmodel shaders from `.glsl` files in
`assets/shaders/`, while every map and campaign stage is loaded from
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

The game opens in a 1280x720 window (the size in `assets/data/window.txt`), on the Title screen, with the mouse
cursor free. If fullscreen was switched on in the settings on an earlier run, it opens fullscreen instead. Two environment variables keep it in a window
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
| `M` | Toggle the top-down minimap on or off (persisted in Settings) |
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

These are the keys and buttons the game ships with. `assets/data/bindings.txt` gives every action its inputs,
and the key names in the on-screen text follow that file (see [Key bindings](#key-bindings-bindingstxt)).

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
- A stage started from this list is entered with full health, no armor and the weapons its own file lists in its
  `loadout` header, not the weapons its place in the campaign would suggest: the pistol alone on Outer Breach, the
  scattergun too from Coolant Core on, and the rail lance as well from Lockdown Wing on. Each weapon but the pistol
  comes with the ammunition of one of its weapon pickups on the chosen difficulty.

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

**Settings.** The settings are mouse sensitivity, inverted look, field of view, view bob, master volume,
fullscreen and minimap. They are saved between runs; what each is before you change it, and the range and step of each
row, come from `assets/data/settings.txt` (see [Tuning files](#tuning-files)). Opened from the Title, the Settings screen keeps the Title's moving
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
- **Combat Scattergun (`2`)**: Unlocked in **01: Outer Breach**, **06: Warden Core**, and the **Training Range** (and equipped directly when deploying to Stages `02`–`06` from Select Stage). Draws from a carried `24`-shell pool fed by Scattergun (`+8`) and Shell Box (`+6`) pickups scaled by difficulty:
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
| `io.github.brantunger.gamex.settings` | The seven settings |
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
| `GAMEX_DEMO` | Optional script, the name of a file in `assets/demos/` without `.txt`: `action`, `targets`, `breach`, `title`, `lockdown`, `stages`, `scattergun`, `raillance`, `warden`, `wardenidle` or `jump`. Without it the player stands still. |
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
no exit. The script name is not case-sensitive. A name with no file in `assets/demos/` stops the game with a message
naming the file it looked for, and so does a name holding anything but letters, digits, `_` and `-`.

#### Demo script files

Each script is a text file, `assets/demos/<name>.txt`, so a demo is added or adjusted without a rebuild: edit or
add a file and run `./gradlew run` with `GAMEX_SCREENSHOT` set and `GAMEX_DEMO` set to its name. A file is a header
block that sets the scene, then a blank line, then the timeline of inputs. A script may end after the header block;
then no input is played. `assets/demos/breach.txt`:

```
// GAMEX_DEMO=breach: Outer Breach. The player runs in, dashes, sidesteps, turns and then fires the pistol.
screen: playing
stage: 01_outer_breach
loadout: fresh

1-155: move_forward
25: dash
115-135: move_right
136-150: turn -1.2
150-: fire
```

The header block, in the `key: value` format of the [tuning files](#tuning-files), with `//` comment lines:

| Line | Meaning |
| --- | --- |
| `screen` | `playing`, `title` or `stage_select`: the screen the run opens on. |
| `stage` | The `id` of a campaign stage, `training_range`, or `first_level` for the original first level. |
| `loadout` | `fresh` (full health, no armor, the pistol alone) or `custom`. |
| `loadout.health`, `loadout.armor`, `loadout.weapon` | Needed with `loadout: custom`, and not allowed without it: health from 1 to 100, armor from 0 to 100, and the weapon in hand (`pistol`, `scattergun` or `raillance`). The weapon in hand must be one the loadout holds with ammunition: `scattergun` needs a `loadout.shells` above 0 and `raillance` a `loadout.slugs` above 0. |
| `loadout.shells`, `loadout.slugs` | Optional with `loadout: custom`: the line gives the player the Scattergun or the Rail Lance with that much ammunition. |
| `position` | Optional: `x, y` on the map, in cells. Without it the player stands on the spawn. |
| `look` | Optional: `yaw, pitch` in degrees, turned from where the spawn faces. |
| `unlock_all_stages` | Optional: `true` opens every stage on the Select Stage screen. |
| `keep_enemies` | Optional: the kinds of enemy that stay (`drone`, `turret`, `guard`, `warden`, separated by commas); every other enemy is removed before the first frame. |

A timeline line is `frames: input` or `frames: input amount`. Frames are counted from 1: one frame (`25`), a first
and a last frame (`115-135`), or a first frame with no end (`150-`). The inputs carry the names of the key-binding
actions:

- `move_forward`, `move_back`, `move_left`, `move_right`: held, at full speed or at the amount given (more than 0,
  at most 1; `5-14: move_forward 0.5`).
- `fire`, `alt_fire` (charge, double blast or zoom), `jump`: held for the frames.
- `dash`, `interact`, `weapon_1`, `weapon_2`, `weapon_3`, `quick_swap`, `weapon_next`, `weapon_prev`: asked for on
  each of the frames.
- `turn` and `look_up`, which the mouse does: the degrees turned right or looked up on each of the frames. The
  amount is needed and may be negative.

The difficulty is always Normal. A header the game does not know, a missing one, an unknown stage or input, an
amount out of range and two lines that set the same thing on the same frame each stop the game with a message
naming the file and the line (a missing header has no line: the message names the file and the header). The frame
saved (`GAMEX_SCREENSHOT_FRAME`, 60 by default) and the 1/60 second a frame lasts are not in the files.

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
warning and the window opens at the size in `assets/data/window.txt`, 1280x720 as shipped. Whenever the variable is set, even to such a value, the game stays in a
window although fullscreen is saved on. The saved setting is not changed, so the Settings screen still reads
`FULLSCREEN: < ON >`.

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
| `debug` | All of that, plus each model, sound, stage, tuning, shader and demo script file loaded and which copy of a tuning or shader file was read, the meshes, textures and animations of the Warden model, hostiles spawned and killed, the Warden's punches and its changes of animation, targets destroyed, pickups, console overrides, objective changes, menu navigation, settings changes, settings saved and window resizes. |
| `warn`, `error` | Only warnings and errors, or only errors. |
| `trace` | The same as `debug`; the game logs nothing at that level. |

The value can be in any case, with spaces around it. Any other value logs a warning and the level is `info`.

A warning is logged for most things the game falls back on: a sound that does not load, an environment variable it
cannot read, a saved number that is not a number (once for each key), a saved run with an unknown difficulty. An
unknown saved weapon and a setting out of range are replaced without one. An unknown `GAMEX_DEMO` name is not
replaced: it stops the game.

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
    playback (`SoundManager`), at the volume `assets/data/sound-volumes.txt` gives each cue (`SoundVolumes`).
  - `world`: the level grid (`GameMap`), `.map` and `campaign.txt` level/stage asset loading (`Levels`, which also finds a campaign stage by its id with `Levels.stage(id)`), collision with walls, targets and enemies,
    hitscan ray casting, grid BFS pathfinding (`Pathfinder`), line-of-sight fog-of-war exploration
    (`MapExploration`), map geometry, targets, hostile enemies (`Enemy`), pickups
    (`Pickup`), world object models (`WorldModels`), effects, the enemy and Warden values read from
    `assets/data/enemies.txt` and `assets/data/warden.txt` (`EnemyTuning`, `WardenTuning`), and the pickup, canister,
    shock grate, feedback-time, minimap reveal-radius and noise values read from `assets/data/world.txt`
    (`WorldTuning`).
  - `player`: the player's position, view angles, movement and dash, health and armor, the per-frame input record,
    the difficulty levels (`Difficulty`), their multipliers read from `assets/data/difficulty.txt`
    (`DifficultyTuning`) and the health, armor, weapons and ammunition a stage is entered with (`PlayerLoadout`, whose `forStage` gives
    a stage the weapons of its `loadout` header).
  - `weapon`: the pistol, the combat scattergun (`Scattergun`), the rail lance (`RailLance`), weapon slot switching (`WeaponSlot`), the energy meter, the blast's charge logic, player and enemy projectiles (`BlastProjectile`,
    `EnemyProjectile`), explosion damage, the pistol, scattergun & rail lance 3D models (`PistolModel`, `ScattergunModel`, `RailLanceModel`), the packing of the
    hand's mesh (`HandModel`), the numbers that animate them, and the weapon values read from
    `assets/data/weapons.txt` (`WeaponTuning`). Each model takes its colours from its palette file in `assets/data/`.
  - `data`: the shared loader for text files under `assets/`: where a file is looked for, with or without libGDX
    (`AssetText`), and the `key: value` format with its typed getters and errors (`TuningFile`). See
    [Tuning files](#tuning-files).
  - `model`: a parser for Wavefront OBJ model files including `# @part` / `# @socket` / `# @accent` directives (`ObjModel`),
    a headless forward-kinematics rig evaluator (`ModelRig`), vertex packing (`ModelVertices`, including 16-bit
    indexed deduplication), and the material and accent colours of a model read from a palette file
    (`ModelPalette`). None use graphics types.
  - `render`: the 3D view (`WorldRenderer` with `u_normalMatrix` and `u_accentColour` shader uniforms), the pass that draws the weapon and hand over it (`ViewmodelRenderer`), the Warden's skinned glTF model drawn with gdx-gltf's PBR shader (`WardenModel`) and the choice of its animation (`WardenPose`), concurrent off-thread startup texture decoding and OBJ mesh/rig packing (`AssetPreparer`) plus stage-triggered parallel glTF image preloading (`WardenPreloader`), the reading of the shader files in `assets/shaders/` (`Shaders`), the textures read from `assets/textures/` (`ProceduralTextures`, with the wall types' textures named by `assets/data/walls.txt` through `WallTextures`), the five palettes of the world models, the weapons and the hand loaded together (`Palettes`), and the clip range, fog, halos and per-kind accents read from `assets/data/render.txt` (`RenderSettings`).
  - `hud`: the 2D overlay (`HudRenderer`: crosshair and hit markers, health, armor and energy bars, charge bar, status
    texts, north-up top-right minimap via `MinimapRenderer`, and the menu screens), the screen and menu state
    machine behind those screens (`MenuState`, which uses no graphics types and reads no input device), the pixel
    font, whose glyphs are loaded from `assets/data/pixel-font.txt`
    (`PixelFont`), the check that text read from a file can be drawn with it (`GlyphCheck`), and the text of the
    menus and the HUD, loaded from `assets/data/strings.txt` (`UiStrings`): the list of its lines and their
    placeholders (`UiText`), the parsing and filling of a line (`TextTemplate`), the key names and tuning values a
    line may quote (`KeyLabels`, `TextValues`), how numbers and times are written (`TextFormat`), and the check at
    start-up that every line fits where it is drawn (`TextFit`).
  - `settings`: the player's settings (`GameSettings`), the saved campaign run and best records (`SaveData`), the
    medals (`Medal`), what a finished run hands to the results screen (`RunStats`), and the key-value store they are
    kept in: libGDX preferences wrapped in `AsyncKeyValueStore` (an in-memory write-through cache with a sequential
    `gamex-io` background writer) in the game, a map in tests and debug runs. Also the defaults, ranges and steps of
    the settings, loaded from `assets/data/settings.txt` (`SettingsDefaults`), and the default window size, loaded
    from `assets/data/window.txt` (`WindowDefaults`).
  - `input`: reads the keyboard and mouse for play and for the menus (`InputController`), adds up mouse movement
    for looking around (`LookAccumulator`), and handles pause and cursor capture. Also the actions an input can be
    bound to (`Action`), the inputs of each action, loaded from `assets/data/bindings.txt` (`KeyBindings`,
    `BoundInput`), and the names of the inputs in that file and in on-screen hints (`KeyNames`).
  - `debug`: the debug screenshot mode (`DemoRun`) and the demo scripts it plays, loaded from `assets/demos/`
    (`DemoScript` for a script and its timeline, `DemoScene` for the scene its header block sets).
- `desktop`: the LWJGL3 launcher (`DesktopLauncher`), the window settings, the window icons, and the log level and
  console log configuration (`LogLevel`, `logback.xml`).
- `tools`: the Gradle model, audio and texture authoring and inspection subproject (see [tools/README.md](tools/README.md)): `MeshBuilder`
  DSL, `PropSculpt`, `EnemySculpt`, `PistolSculpt`, `ScattergunSculpt`, `RailLanceSculpt`, `HandSculpt`, `GenerateAllModels`,
  `SoundSynth`, `GenerateAllSounds`, `TexturePainter`, `GenerateAllTextures`, and the interactive LWJGL3 previewer
  (`ModelPreviewApp`).
- `assets`: the files the game loads, at start-up except where noted:
  - `levels/*.map`, `levels/campaign.txt`: plain-text map layouts, stage metadata/briefings, and ordered campaign manifest.
    A campaign stage file may hold an optional `loadout:` header listing the weapons a player deployed straight to
    the stage starts with (`pistol`, `scattergun`, `raillance`, in lower case, separated by commas; an unknown name
    or one listed twice stops the load). A stage without the header starts with the pistol alone, and the pistol is
    carried whether it is listed or not. Each other listed weapon comes with the ammunition of one of its weapon
    pickups, scaled by the difficulty's pickup scale. The scattergun is in hand if listed, otherwise the rail lance,
    otherwise the pistol. From the menu, the Training Range is entered with the pistol alone, and its file may
    not carry the header.
  - `data/*.txt`: `key: value` tuning files (see [Tuning files](#tuning-files)): `enemies.txt`, `warden.txt`,
    `difficulty.txt`, `sound-volumes.txt`, `walls.txt`, `weapons.txt`, `world.txt`, `render.txt`,
    `palette-world.txt`, `palette-pistol.txt`, `palette-scattergun.txt`, `palette-rail-lance.txt`,
    `palette-hand.txt`, `strings.txt`, `bindings.txt`, `settings.txt` and `window.txt`; and `pixel-font.txt`, the
    glyphs of the HUD font, drawn as blocks of `#` and `.` rather than written as `key: value` lines.
  - `demos/*.txt`: the scripts of the debug screenshot mode, one for each `GAMEX_DEMO` name (see
    [Demo script files](#demo-script-files)). They are read only on a run that names one.
  - `shaders/*.glsl`: the vertex and fragment shaders of the map (`world`), the world models (`model`) and the
    first-person weapon and hand (`viewmodel`).
  - `textures/*.png`: the wall, floor, ceiling and effect textures, generated by `./gradlew tools:generateTextures`.
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

## Tuning files

Numbers and texts that are meant to be tuned without a rebuild live in `key: value` text files under `assets/data/`,
in the format the stage headers in `assets/levels/*.map` already use. The loader is
`io.github.brantunger.gamex.data.TuningFile`. The files that ship:

- `enemies.txt`: each enemy kind's health, speed and attack, and what the kinds share.
- `warden.txt`: the Warden's chase, three-punch combo and overdrive.
- `difficulty.txt`: what Easy, Normal and Hard multiply.
- `sound-volumes.txt`: the playback volume of each sound cue.
- `walls.txt`: the `.png` texture of each wall type.
- `weapons.txt`: the pistol, the scattergun and its pellet rings, the Rail Lance, the charged blast, the energy
  meter, the recoil kicks, and the ammunition pickups and caps. The briefing of
  `assets/levels/03-security-hub.map` quotes its `rail.zoom_factor` with a `{rail.zoom_factor}` placeholder
  ("1.8X ZOOM OPTIC"), so the text follows the value.
- `world.txt`: what the health, armor and energy pickups give, the canister's explosion, the shock grates, how
  long the hit marker and a pickup toast show, how far line-of-sight reveals the minimap, and the table of how far
  each noise is heard.
- `palette-world.txt`, `palette-pistol.txt`, `palette-scattergun.txt`, `palette-rail-lance.txt` and
  `palette-hand.txt`: the colours of the world models, of each weapon and of the hand, one `material.<name>` line
  for each material and one `accent.<name>` line for each accent colour, as `red, green, blue, gloss` from 0 to 1.
  The material names are the ones the model files use in their `usemtl` lines.
- `render.txt`: the camera's clip range, the fog, the halos under pickups and security consoles, and which accent
  of `palette-world.txt` each kind of pickup and each kind of door lock is drawn with.
- `strings.txt`: every line of text the menus and the HUD show (see [Menu and HUD text](#menu-and-hud-text)).
- `bindings.txt`: the keys, mouse buttons and wheel directions of every action, which are also the key names the
  text shows (see [Key bindings](#key-bindings-bindingstxt)).
- `settings.txt`: what each setting is until the game first saves the settings, and the least, the most and the step
  of the mouse sensitivity, the field of view and the master volume on the Settings screen. The player's own choices
  stay in the saved preferences (see [Saved settings and progress](#saved-settings-and-progress)): all seven settings
  are saved, changed or not, when the player leaves the Settings screen, toggles the minimap during play, switches
  fullscreen or closes the game (a screenshot run saves nothing), so a default changed in the file reaches only a
  player with nothing saved. A saved
  value outside a range that was narrowed is brought back into it when it is read. A least value above the most, a
  default outside its range, a step below the precision a setting is kept to (0.01 for sensitivity and volume, 1 for
  the field of view) and a least value, a most value or a default finer than that precision (a field of view of 72.5,
  a sensitivity of 0.405) each stop the game. The names the preferences are saved under are not in the file: they are
  the save format.
- `window.txt`: the width and height of the window when it is not fullscreen and `GAMEX_WINDOW` does not set one,
  from 160x120 to 7680x4320. The launcher reads it before libGDX is up, from the places the game reads its own
  files from (see [Which copy of a file is read](#which-copy-of-a-file-is-read)). There is no size behind the file:
  without it, or with a size it cannot use, the game does not open, and the message names the file and, where there
  is one, the key. The 3D view and the HUD are laid out for this size too, until the window reports its own.

A recolour takes an edited palette file and `./gradlew run`, or `./gradlew tools:preview`, where `R` reads
the palette files and the font file again (see [tools/README.md](tools/README.md)).

`pixel-font.txt`, also in `assets/data/`, is not a `key: value` file: it holds the HUD font, one block per
character of a `glyph <character>` line and five rows of three cells, `#` for a lit pixel and `.` for an empty
one. `PixelFont` reads it and stops the game on a broken block, naming the file, the line and the glyph. The
shaders in `assets/shaders/` and the textures in `assets/textures/` (generated by
`./gradlew tools:generateTextures`) are not `key: value` files either; the game finds all of them as
[Which copy of a file is read](#which-copy-of-a-file-is-read) says. The sample below shows the format; no
`pistol.txt` ships.

```
// A line whose trimmed text begins with two slashes is a comment.
pistol.fire_interval: 0.3
pistol.pellets: 8
pistol.auto: false
pistol.name: SERVICE PISTOL
pistol.falloff: 0.60, 1.10, 1.27
hint: A KEY DECLARED AS REPEATED
hint: MAY APPEAR ON SEVERAL LINES.
```

The key is the text before the first colon; the value is the rest of the line. A key is matched as a whole string
and its case matters: a dot is part of the name and nothing more. A comment takes a whole line; there are no comments
after a value, and `//` there is part of the value. A byte order mark and Windows line endings are accepted.

### Menu and HUD text

`assets/data/strings.txt` holds every line of text the menus and the HUD show: menu options, HUD captions, screen
titles, the lines of the results screen, objectives, interaction prompts, toasts, and the labels of the
pickups, difficulties and medals. It is a `key: value` file like the others, every key must be present, and
`UiStrings` reads it once at start-up. Stage titles and briefings stay in the `.map` files.

A line may hold placeholders, which are names in braces:

| Placeholder | Stands for |
|---|---|
| `{key:action}` | The name of the key `bindings.txt` gives an action, as in `[{key:interact}] OPEN BULKHEAD`: rebind the action and every line that names it shows the new key (see [Key bindings](#key-bindings-bindingstxt)). The actions are those of `bindings.txt`, listed under [Key bindings](#key-bindings-bindingstxt). |
| `{rail.zoom_factor}` | That value of `weapons.txt`, written without needless decimals (`1.8`, `2`). |
| any other name | A value the game fills in when it shows the line, as in `HOSTILES {remaining}/{total}`. Each line has a fixed set of these, the ones the shipped line uses (`UiText` lists them): a line must keep every one of them, in any order, and can take no others. |

The first two kinds also work in the `briefing` lines of a stage file, which take no others.

The game stops at start-up, naming the file, the key and the line, when a line is empty, holds a character the pixel
font cannot draw, has a brace without its partner, names an unknown placeholder or action, or leaves out a placeholder
it must keep. A bad placeholder in a briefing is reported when the stage file is read, a briefing that cannot be drawn
when the text is measured (see below), and a stage title that cannot be drawn when the menus are built. A value loses
the spaces at its ends, and braces cannot be written as plain text.

Rewording a line changes what is shown and nothing else. The menus tell their options apart by ids fixed in code
(`MenuState.OptionId`), the HUD colours an interaction prompt by whether the bulkhead can be used and not by its
words, and the simulation names objectives, prompts and toasts by enum (`GameSimulation.Objective`,
`InteractionPrompt`, `ToastId`). Which options a screen has, and their order, stay in code. The Select Difficulty
rows are made from `difficulty.txt` with the wording of `menu.difficulty.row`: `damage_taken` 0.7 reads `70%
DAMAGE` and `pickup` 1.25 reads `+25% PICKUPS`; `enemy_speed` is not shown. A difficulty whose three multipliers
are all 1 gets `menu.difficulty.row_standard`.

Layout stays in code, so a longer line can outgrow its place, and a line too long for its place stops the game
at start-up. Once the font, the bindings and the text are loaded, `TextFit` measures every line that is drawn in a
box, at the size it is drawn in, and the message names the key, the file and the line, shows the text and says by
how many characters it is too long:

| Line | Room |
|---|---|
| The Title footer | A 4:3 window: 126 characters. |
| A toast, the pickup toasts among them | The room kept around the centre of the window: 38 characters. |
| The objective line, `hud.objective` around the objective's words | From the status column to that room: 40 characters. |
| An interaction prompt | The narrowest window: 73 characters. A blocked bulkhead's line is also a toast. |
| A menu option, with the Select Stage, Select Difficulty and Settings rows | Its row while highlighted: 42 characters. |
| The title and captions of each menu screen, the quit prompt, the Settings help line, the medal line of Select Stage, the lines of the results screen, and a stage's title on the Briefing and results screens | Their panel, less a margin on each side. |
| The label over the boss health bar | The bar's panel. |
| The minimap header label (`hud.minimap_north`) | The minimap header strip: 25 characters. |
| A briefing line of a stage file | The briefing's box: 58 characters. The box holds three lines, and a stage with more stops the game too. |

A line that takes values is measured with the longest it is given room for: every stage title and every label,
counts of `999`, `9999` damage, `100%`, a pickup of `+999` (a keycard and a weapon come without an amount), times
of `99:59.9` (`99:59` on Select Stage, and a stage's par time where it is longer to write), a rank of
one letter, all four medals marked new, and the end of each Settings range that is longer to write. The Select
Difficulty rows are measured with the multipliers of `difficulty.txt`. The status row, the timer, the health, armor
and energy captions, the weapon list, the keycard badges and the low-energy and charge captions are in no box and
are not measured.

A hint that sums up several keys is a line that names their actions. The Title footer's `WASD: MOVE` is
`{key:move_forward}{key:move_left}{key:move_back}{key:move_right}: MOVE` and its `1-3` is
`{key:weapon_1}-{key:weapon_3}`, so they follow the bindings as the single key names do.

### Key bindings (`bindings.txt`)

`assets/data/bindings.txt` gives every action its keys, mouse buttons or wheel direction, one `key: value` line per
action with one input or several separated by commas. Edit a line and relaunch with `./gradlew run`; there is no
rebuild. The [Controls](#controls) tables list the bindings the game ships with.

```
dash: L-Shift, R-Shift
weapon_next: Wheel-Down
fire: Mouse-Left
```

- The actions are `pause`, `retry`, `move_forward`, `move_back`, `move_left`, `move_right`, `dash`, `jump`,
  `interact`, `weapon_1`, `weapon_2`, `weapon_3`, `quick_swap`, `weapon_next`, `weapon_prev`, `fire`, `alt_fire`,
  `minimap` (during play) and `menu_up`, `menu_down`, `menu_left`, `menu_right`, `menu_confirm`, `quit`,
  `menu_click` (on the menu screens); `pause` and `retry` act in both. What an action does, and whether it acts while held or once per
  press, stays in code (`InputController`, the `Action` enum).
- A key has the name libGDX gives it, and the case matters: `A` to `Z`, `0` to `9`, `Space`, `Enter`, `Escape`,
  `Tab`, `Up`, `Down`, `Left`, `Right`, `L-Shift`, `R-Shift`, `L-Ctrl`, `R-Ctrl`, `L-Alt`, `R-Alt`, `F1` to `F12`,
  `Numpad 0` to `Numpad 9` and so on. The comma key is written `Comma` and the numpad's `Num Comma`. The mouse buttons are `Mouse-Left`,
  `Mouse-Right`, `Mouse-Middle`, `Mouse-Back` and `Mouse-Forward`; the wheel is `Wheel-Up` or `Wheel-Down`.
- An input can serve one action during play and another on the menu screens, as `Q` does for `quick_swap` and
  `quit`. It cannot serve two actions that act at the same time.
- The wheel works during play only, and `menu_click` takes mouse buttons only.
- The first input of a line is the one an on-screen hint names, under a short display name (`KeyNames`:
  `L-Shift` and `R-Shift` are `SHIFT`, the `Ctrl` and `Alt` keys `CTRL` and `ALT`, `Escape` is `ESC`, `Comma` is
  `,` and `Num Comma` is `NUM ,`, the mouse buttons are `LMB`, `RMB`, `MMB`, `MB4` and `MB5`, the wheel is
  `SCROLL`, and any other key is its name in capitals). The HUD font must be able to draw that name. Every key name
  on screen is such a hint, except the word `CLICK` of the Settings help line, which is plain text and does not
  follow `menu_click`: the Title
  footer, the `ENERGY [SHIFT: DASH]` caption, the weapon list, the `[E]` prompts, `RETRY STAGE (R)`, `PRESS Q AGAIN
  TO QUIT`, the Settings help line and the briefing that names the zoom button are lines of `strings.txt` and of
  the stage files with `{key:action}` placeholders (see [Menu and HUD text](#menu-and-hud-text)), so a rebound
  action changes all of them at the next start.
- Longer key names make longer lines, and a line a key name makes too long for its place stops the game at start-up
  (see [Menu and HUD text](#menu-and-hud-text)): the message names the line and the actions whose key names it shows.
  The Title footer is one centred line with room for 126 characters in a 4:3 window, and it has 93 with the shipped
  keys; the briefing line of the Security Hub that names the alt-fire button has room for a key name of 14 characters
  with the shipped zoom factor, which is the longest name a key has (`Forward Delete`).

The game stops at start-up, naming the file, the action and the line (an action without a line has no line to
name), on an action without a line or with two, a line that names no action, an unknown or repeated input name, a
clash, the wheel on an action of the menu screens, anything but a mouse button on `menu_click`, and a hint the font
cannot draw. Rebinding in the game and saving bindings with the player's settings are not supported.

### Which copy of a file is read

- **The file under the working directory comes first.** The game reads assets with libGDX's `Gdx.files.internal`,
  which opens the file at that path relative to the working directory if there is one, and reads the copy on the
  classpath only otherwise. This holds for every internal asset (models, sounds and levels too), not for tuning files
  alone.
- **`./gradlew run`** starts the game with `assets/` as its working directory, so it reads the files you edit. Change
  a value, start the game again, and the change is there; no Java is compiled.
- **Trying a value needs no rebuild, and committing one needs the data file alone.** No test holds the values the
  files ship with, so `./gradlew build` stays green on a tuned number, a reworded line, a rebound key, a retimed
  demo script or a stage's changed `loadout` header. The tests that play the game run on a copy of the data files
  of their own, `core/src/test/resources/frozen/data/`, which `TestTuning` loads: the numbers and the words they
  expect are the ones in that copy, and it does not follow `assets/data/`. The shipped files are tested on their
  own, whatever they say:
  - each loads and passes its loader's checks (`theShippedFileLoads` and its like in `EnemyTuningTest`,
    `WeaponTuningTest`, `WorldTuningTest`, `DifficultyTuningTest`, `SoundVolumesTest`, `WallTexturesTest`,
    `PalettesTest`, `PixelFontTest`, `KeyBindingsTest`, `SettingsDefaultsTest`, `WindowDefaultsTest` and, in
    `desktop`, `DesktopLauncherTest`);
  - `ShippedTextTest` loads `strings.txt` with the shipped font, bindings, tuning, settings and stage files and
    puts them through `TextFit.require`, the measuring the game does at start-up, so a line too long for its place
    fails the build and not only the launch;
  - every script in `assets/demos/` loads and plays (`DemoScriptTest`), and every stage starts with the weapons its
    own file lists (`StageLoadoutTest`, `StageLoadoutFileTest`).

  - the tests of a design promise run twice, on the tests' copy and on the shipped balance
    (`TestTuning.Balance`): an idle player is left alone at the start of every campaign stage and a plain jump
    clears the Training Range's shock grate (`GameSimulationTest`), the two Warden Core walkthroughs of
    `ObjectiveTextTest`, every fight of `WardenFightTest`, walking back from the Warden being caught and its run
    outpacing a walk (`WardenCombatTest`), and the `jump` demo vaulting the grate (`DemoRunTest`, which plays the
    shipped script on the shipped balance). A tuned value that breaks one of these fails the test that names the
    promise, with "the shipped files" in its name.

  That each key of a file reaches its own value, and no other, is checked on files in
  `core/src/test/resources/data-fixtures/` (and on tables the tests build), in which no two values are alike and
  none was the shipped one when the fixture was written. What a changed file can still fail is a real mistake: a
  value its loader rejects, a line
  too long for its place, a balance that breaks one of the design promises above, `combo_time` of `warden.txt` out
  of step with the Warden's clip (`WardenModelTest`), a
  `walls.txt` that stops using a generated texture (`theGameLoadsExactlyTheGeneratedTextures` in
  `GeneratedTexturesTest`), or a texture, model or sound that is not what its generator writes (the test says
  which `./gradlew tools:generate...` task to run again). The stage files are level content, and the tests do read
  them. A stage's `par` and its `loadout` header can change on their own. So can its briefing, as far as `TextFit`
  has room for it, with two exceptions `LevelsTest` holds on purpose: the Command Vault's briefing names the
  `COMMAND VAULT` in its first line and the `WARDEN CORE` in its last, and no stage before the last calls its goal
  the `FINAL OBJECTIVE` or `FINAL EXTRACTION`. A stage's id, its title and its place in `campaign.txt` are named by
  `LevelsTest`, the menu tests and the tests that play a stage, so a retitled or reordered stage, or one given
  another id, still needs its tests changed. The tests that measure or quote a stage's text (`TextFitTest`) read
  copies of the stage files of their own, in `core/src/test/resources/frozen/levels/`.
- **A new key needs its line in three places.** A key added to a file of `assets/data/` also goes into the tests'
  copy in `core/src/test/resources/frozen/data/` and into the file the test of its keys reads, with the value that
  test then expects, in the change that adds the code reading it. For `difficulty.txt`, `enemies.txt`,
  `render.txt`, `warden.txt`, `weapons.txt` and `world.txt` that file is the fixture in
  `core/src/test/resources/data-fixtures/`; for `settings.txt`, `window.txt` and `bindings.txt` the lines are
  written out in the tests (`SettingsDefaultsTest`, `WindowDefaultsTest`, `KeyBindingsTest`,
  `MainGameViewportTest`, `HudRendererTest`, `RendererSupportTest` and, in `desktop`, `DesktopLauncherTest`), and
  `PalettesTest` counts the rows of each palette. A removed key comes out of the same places. A missing line stops
  every test that uses `TestTuning`: the first of them shows the
  reason under `Caused by` (`The tests' own copy of data/enemies.txt, core/src/test/resources/frozen/data/enemies.txt,
  does not load: Missing 'guard.health' key ...`), and the rest fail with `NoClassDefFoundError: Could not initialize
  class ...TestTuning`.
- **A packaged jar** holds a copy of `assets/` and reads that copy. A loose file at the same relative path under the
  working directory of the process (the directory `java -jar` is run from, which need not be the folder the jar is
  in) is read in its place: for example `data/pistol.txt` under that directory. Without one, a changed value needs
  a new jar.
- **Without libGDX** (the tests and the `tools` generators; `tools:preview` runs with libGDX and follows the first
  rule), a file is looked for in the folder named by the `gamex.assets` system property, then in `../assets`,
  `assets` and the working directory, then on the classpath. Only the `core:test` and `tools:test` tasks set
  `gamex.assets` (to `assets/`); `desktop:test` and the generator tasks do not.
- **The desktop launcher** needs `data/window.txt` and `data/settings.txt` before it starts libGDX, and looks for
  them as the first rule says all the same: the file at that path under the working directory if there is one, the
  copy on the classpath otherwise. Under `./gradlew run` that is the file in `assets/`, and for a packaged jar the
  copy inside it unless a loose `data/window.txt` or `data/settings.txt` lies under the working directory; it does
  not look in `gamex.assets`, `../assets` or `assets`. It reads each of the two once and hands what it read to
  `MainGame`, which reads neither again and passes them on to the settings, the renderers and the HUD, so the
  window and the game in it cannot go by different copies.

### Mistakes in a file stop the game

No value in a tuning file has a default, and a file the game asks for must be there: a missing file stops the game
too. Every mistake in a file is an `IllegalArgumentException`:

- **Loading the file stops** on a line that is not `key: value` (the message names the file and the line), on a key
  the code does not know and on a key given twice (file, key and line), and then on a key that is missing (file and
  key). For example, mistyping the second key of the sample above gives
  `Unknown key 'pistol.pelets' in data/pistol.txt at line 3`.
- **Reading a value stops** on an empty value and on a value of the wrong form: `0.5f`, `fast`, `NaN` or a number
  too large or too small for a float where a number is read, `8.0` for a whole number, `yes` for `true` (file, key
  and line).

A mistyped key therefore cannot leave the old value silently in place. The code may declare a key as optional; such
a key may be left out, and what that means is for the code that reads it to say.

### Adding a tuning file, and how its values reach the code

A value read from a file cannot be a `static final` constant, and no constant is initialised from a file. Values
always reach the code as an immutable tuning object that is passed in:

1. Add `assets/data/<name>.txt`.
2. Add a class with a final field per value (public, except that an array is private with an accessor), a
   constructor taking them all, and a static `load()` that declares every key the file holds. A class may
   instead keep its constructor private and add a public `parse(label, text)` beside `load()`, which builds the
   object from the text of a file (`WardenTuning`, and `EnemyTuning`, whose `parse` also takes the Warden's
   tuning). For the sample above:

   ```java
   public static PistolTuning load(PixelFont font) {
       TuningFile file = TuningFile.load("data/pistol.txt",
           List.of("pistol.fire_interval", "pistol.pellets", "pistol.auto", "pistol.name", "pistol.falloff"),
           List.of("hint"));
       float fireInterval = file.getFloat("pistol.fire_interval");
       if (fireInterval <= 0f) throw file.invalid("pistol.fire_interval", "must be positive");
       return new PistolTuning(fireInterval, file.getInt("pistol.pellets"), file.getBoolean("pistol.auto"),
           file.getDisplayString("pistol.name", font), file.getFloats("pistol.falloff", 3),
           file.getDisplayStrings("hint", font));
   }
   ```

   - **Declaring keys.** `load(path, "a", "b")` declares keys that each appear on exactly one line. The form above
     takes those first and the repeated keys (one line or more) second; a third list declares optional keys (one
     line or none), which the code asks about with `has(key)` before reading them.
   - **Reading values.** `getFloat`, `getInt`, `getBoolean` and `getString` read one value. `getFloats(key)` and
     `getInts(key)` read a comma-separated list, and `getFloats(key, count)` and `getInts(key, count)` one of
     exactly that length. `getStrings` reads every line of any declared key, so it is the getter for a repeated
     one. `getDisplayString(key, font)` and `getDisplayStrings(key, font)` also check that the loaded
     `PixelFont` they are given can draw every character, so a `load()` that reads display text takes the font,
     as the one above does.
   - **Lines of a repeated key.** `entries(key)` gives each line with its key, raw value and line number, and
     `getFloat`, `getInt`, `getFloats` and `getInts` also take such a line, so a row like `ring: 7, 0.12, 0` is
     read as strictly as a single value. `entry(key)` gives the one line of a single-valued key.
   - **Ranges.** A getter checks the form of a value. Its range is checked by `load()`, which reports a bad one
     with `file.invalid(key, problem)`, or `file.invalid(entry, problem)` for a line of a repeated key; the
     message names the file, key and line and ends with the value.
   - **Arrays.** `getFloats` and `getInts` return a new array each time. The tuning object keeps it in a private
     field and gives out an indexed getter or a copy, so that it stays immutable.
3. In the game, load it once at start-up, in `MainGame.create()` or later and never from a static initialiser,
   and pass the object to the classes that use it, through a constructor or a `GameSimulation` field. (The two
   files the launcher needs before the window exists, `settings.txt` and `window.txt`, are loaded once in
   `DesktopLauncher` instead and passed to `MainGame`'s constructor.) Tests build
   their own values and read no file: with the public constructor (`DifficultyTuning`, `SoundVolumes`,
   `WallTextures`, `ModelPalette`), or with `parse(label, text)` where the constructor is private
   (`EnemyTuning`, `WardenTuning`, `WeaponTuning`, `WorldTuning`, `RenderSettings`, `PixelFont`,
   `SettingsDefaults`, `WindowDefaults`; the `parse` of `KeyBindings` takes the font as well, which checks that
   its hints can be drawn, and that of `UiStrings` the font, the key names and the tuning values its lines quote).
   Tests that want a whole set of values take them from `TestTuning`, which loads the tests' own copy of the data
   files (`core/src/test/resources/frozen/data/`) in static fields; that is fine in test code. Its `simulation`,
   `menu`, `settings` and `input` build the game's objects from that copy; `GameSimulation` and `InputController`
   have no constructor that reads `assets/data/` itself. A new file needs a copy there and a field in `TestTuning`, a test that
   the shipped file loads, and a test, on values unlike the shipped ones, that each key is read into its own
   field. To test the rules of a `load()` without a file, have it hand text to
   `TuningFile.parseText(path, text, ...)`, which takes the same key declarations.

`core/build.gradle` and `tools/build.gradle` already track `assets/data`, `assets/shaders` and `assets/textures`, so
a file added there needs no build change, and editing it reruns the tests. Any other new folder under `assets/` must
be registered as a test input in `core/build.gradle` (in `TestLocations`, or as `assets/demos` is, with
`inputs.files`) and, if a `tools` test reads it, in `ToolsAssetsLocation` in `tools/build.gradle`.

## Tests

```sh
./gradlew test
```

The tests are JUnit Jupiter tests in `core/src/test`, `desktop/src/test`, and `tools/src/test`. They need no window or
graphics context; the few that touch libGDX's `Gdx` globals put stubs there or clear them (`InputControllerTest`,
`InputControllerBindingsTest`,
`MainGameViewportTest`, `SoundManagerPartialLoadTest`, `SoundVolumesTest`, `LevelsTest`, `LevelsErrorMessagesTest`, `AssetTextTest`, `TuningFileTest`, `ShadersTest`, `WallTexturesTest`, `EnemyTuningTest`, `PixelFontTest`, `StageLoadoutFileTest`, `DemoRunTest`, `HudRendererTest`, `ShippedTextTest`, and `ModelPreviewAppTest` in `tools`). The `core` tests read `.obj` models, the Warden's `.glb` model, `.wav` sounds, `.map`
levels, tuning files and shaders from `assets/models`, `assets/sounds`, `assets/levels`, `assets/data` and `assets/shaders`, and the `tools` tests also read the textures in `assets/textures`. `core/build.gradle` and `tools/build.gradle` track all six folders, so editing an asset reruns them; `core/build.gradle` tracks the demo scripts in `assets/demos` as well. The tests that play the game do not read the
tuning, text and bindings of `assets/data` but the tests' own copy in `core/src/test/resources/frozen/data/`, through
`TestTuning`, so that a tuned file changes no expectation; the shipped files have load and fit tests of their own
(see [Which copy of a file is read](#which-copy-of-a-file-is-read)). They cover:

- `GameSimulation` (`GameSimulationTest`): pistol hits, a charged blast from release to explosion, muzzle launch and
  crosshair convergence, a pause dropping a charge, targets blocking the player, the clamp on long frames, canister
  explosions chaining and hurting the player, a dash dropping a charge and collecting pickups, enemy
  alerting/damage/kills, headless sound events, weapon swaps, the energy refill, jumps over one-cell and two-cell
  Shock Grates, a stage ending when its last target breaks, a death on the frame the exit is reached, and no enemy
  seeing the spawn point on any campaign stage.
- The HUD objective line (`ObjectiveTextTest`) and the shot and hit counts behind the accuracy figure
  (`ShotAccuracyTest`).
- `MainGame` (`MainGameViewportTest`): the GL viewport on resize and on HiDPI displays, the windowed size to fall
  back to (the one it is given, or that of the window defaults when it is given none), a forced windowed start, and
  which screens draw the backdrop.
- `world`: layout and `.map`/manifest parsing (`GameMapTest`, `LevelsTest`, including doors, keycards, consoles, pickups, enemies, hazards and exit, the `loadout` header and finding a stage by its id), the first level (the spawn is clear of walls and every target can be reached), the Training Range and all six campaign stages (progression-aware reachability of targets, enemies, pickups, consoles and exit), 360-degree line-of-sight fog-of-war exploration (`MapExplorationTest`), collision, standard & piercing hitscan (`HitscanTest`), grid BFS pathfinding (`PathfinderTest`), enemy AI (`EnemyTest`), enemies rounding doorway corners, canisters and turrets and guards keeping off Shock Grates (`EnemyMovementTest`), map geometry, targets
  and the shipped world/enemy `.obj` models (closed outward manifolds matching collision dimensions; pickups stand on
  the floor and fit their pickup radius).
- `render`: concurrent CPU startup asset preparation (`AssetPreparerTest`), parallel Warden glTF image preloading and cancellation (`WardenPreloaderTest`), and the Warden model (`WardenModelTest` reads the JSON part of `enemy-warden.glb`: its four animations, its
  one 28-joint skinned mesh, its height against the Warden's collision height, and the length of its combo clip
  against `combo_time` in `warden.txt`; and checks that only a stage with a live Warden loads the model, and the Warden's
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
  panels (`StageRecordsDisplayTest`), when the combat HUD and minimap are shown and the size the overlay takes from
  the window defaults until the window reports its own (`HudRendererTest`), minimap layout bounds, north-up
  coordinate mapping, FOV cone aspect/zoom calculation and 2D raycast bounds (`MinimapRendererTest`), and the pixel
  font (`PixelFontTest`): each block of a glyph file is the glyph of its own character, the shipped `pixel-font.txt`
  loads, an altered or added glyph changes what is drawn, an edited loose copy is read with no rebuild, and a
  broken file fails naming the file, the line and the glyph. The text of the menus and the HUD: each line is shown
  where its key belongs, an edited line changes what is shown and nothing else, the Select Difficulty rows are made
  from the loaded multipliers, the option rows fit their box, and a broken line fails naming the file, the key and
  the line (`UiStringsTest`); the shipped `strings.txt` loads with the shipped font, bindings and tuning and every
  line of it passes the start-up's fit check (`ShippedTextTest`); the placeholder rules of a line (`TextTemplateTest`);
  how numbers and times are written
  (`TextFormatTest`); and every menu flow run again with every line reworded (`RenamedLabelsFlowTest`).
  `KeyHintsTest`: every key name on screen is the one the bindings give, and other keys in the bindings file
  change the Title footer, the HUD captions, the prompts, the quit and retry lines, the
  Settings help line and the briefing that names a key; `HudRendererTest` checks that the Title footer fits a 4:3
  window. `TextFitTest`: the tests' own text fits with its key names, a line one character too long for its
  box stops the start naming the file, the key and the line, a line with values is measured with the longest it
  can hold, a remapped key with a long name stops the start naming the line and the action, a stage with a
  fourth briefing line stops it naming the stage and its file, and `MainGame` runs the check when it has loaded the
  text.
- `settings`: the settings' ranges (including `masterVolume`) and their round trip through a store, the saved run,
  best times and ranks (`GameSettingsAndSaveTest`), the medals (`MedalTest`), damaged or hand-edited settings and
  save values (`CorruptPrefsTest`), and non-blocking cached reads, ordered background disk flushes, shutdown draining,
  and fault isolation (`AsyncKeyValueStoreTest`). `SettingsDefaultsTest`: each key of a settings file sets its own
  default, least value, most value or step, the shipped `settings.txt` loads and new settings start at its defaults,
  edited ones reach the settings and the Settings rows, a stored value outside a narrowed
  range is clamped, and a range, default or step that cannot work fails naming the file and the key, as does a
  least value, a most value or a default finer than the setting is kept.
  `WindowDefaultsTest`: the size is the one the file holds, the shipped `window.txt` loads, and a file without a
  usable size fails naming the file and the key instead of falling back.
- `audio`: shipped `.wav` asset coverage/format verification, loop-seam continuity, muted headless draining and 3D
  spatial attenuation/panning math (`SoundManagerTest`), and sound files that fail to load
  (`SoundManagerPartialLoadTest`).
- `input`: adding up mouse movement for looking around (`LookAccumulatorTest`), and menu keys and clicks acting once
  per press, `R`, `Q`, and held buttons and `Space` being ignored when play resumes (`InputControllerTest`).
  `KeyBindingsTest`: every line of a bindings file binds its own action and gives it the key name of its first
  input, the shipped `bindings.txt` loads, a changed line changes the inputs and the hint, and an unbound
  action, an unknown name, a clash and a hint the font cannot draw fail naming the file, the action and, except
  for the unbound action, the line.
  `InputControllerBindingsTest` plays other bindings through the controller: an action follows its line, and the
  input it had before does nothing.
- `weapon`: the pistol, combat scattergun (`ScattergunTest`: deterministic 8/16 pellet cones, range falloff, 1-shell fallback, pump curve), and rail lance (`RailLanceTest`: cooldown, bolt cycle, smooth 1.8x zoom optic & cancel latch), the energy meter, the blast's charge rules, the blast projectile (including impact
  contact bisection) and explosion damage, enemy bolts (`EnemyProjectileTest`), the shipped pistol, scattergun & rail lance models (`PistolModelTest`, `ScattergunModelTest`, `RailLanceModelTest`: closed convex outward solids and zero hand-glove penetration at rest and full slide/pump/bolt travel), the shipped hand mesh (every part is closed and faces outwards, no quad folds, unit
  normals, bands of the fingers and palm lie on the grip's surface, the fingers wrap the grip, no glove vertex is inside
  the pistol, and `HandSculpt`'s copied outlines match `PistolModel`), and the animation (recoil, slide, walking bob and turn lag).
- `data`: where a text file is looked for without libGDX, in order, and through `Gdx.files` with it, where the
  launcher's two files are looked for (the working directory, then the classpath), the log line saying which copy
  was read, and line splitting (`AssetTextTest`); the `key: value` format, every typed getter,
  repeated and optional keys and the exact message of every error, a sample file read from the test classpath, and
  an edited loose copy of it changing the loaded value (`TuningFileTest`). `LevelsErrorMessagesTest` pins the text
  of each stage-header error and of the missing-layout, blank-line, null-text and missing-file errors (not the
  wrapped `Invalid map layout` ones), and `GlyphCheckTest` the characters the pixel font draws.
- `model`: the OBJ parser (including files it must reject) and model-file error reporting (`ModelVertices.read`).
- `debug`: the parsing of `GAMEX_VIEWMODEL_ORBIT` and the `jump` demo script (`DemoRunTest`). `DemoScriptTest`:
  every script in `assets/demos/` loads, names a stage the game has and plays 300 frames through a simulation,
  on the tests' own balance and on the shipped one; an edited
  timeline or header changes the run; a name with no file fails naming the file, and a broken header or timeline
  naming the file and the line (the file and the header, for a missing header).
- logging: a cleared level, menu navigation, a settings change, a best time, medals and a damaged saved run each log
  their line at the right level, and frames in which nothing happens log nothing (`GameLoggingTest`).
- `desktop`: parsing `GAMEX_WINDOW` (including its 160x120 to 7680x4320 bounds) and falling back to the size of
  the window file, the shipped `window.txt` read without libGDX, that the launcher reads `window.txt` and
  `settings.txt` under the working directory and then on the classpath and from no `assets` folder, when the game
  starts fullscreen (with nothing saved, when `settings.txt` says so), the packaging icons, when and how
  `StartupHelper` relaunches the JVM on macOS, and reading `GAMEX_LOG_LEVEL` and the level `logback.xml` then runs
  at (`LogLevelTest`).
- Enemy, Warden and difficulty tuning: each key of `enemies.txt`, `warden.txt` and `difficulty.txt` sets its own
  value (read from files in which no two values are alike), the shipped files load, a changed value changes a
  spawned enemy, the combo and the starter ammunition,
  an edited loose copy is read with no rebuild, and a missing, unknown, mistyped or out-of-range key fails naming
  the file and the key, as `avoids_hazards: true` does for any kind but the guard (`EnemyTuningTest`,
  `DifficultyTuningTest`). `TunedSimulationTest` plays changed values through the whole simulation (rail damage,
  shock grates, the boss flag, death bursts and noise, overdrive, punch sounds, difficulty scaling) and checks
  that no main source of `core`, `desktop` or `tools` calls a constructor that reads the tuning files itself
  (those of `MenuState`, `GameSettings` and `MainGame` that are for tests),
  and that `settings.txt` and `window.txt` are read once, by the launcher, and not again once `MainGame` is built.
  `TestTuning` holds the tests' own copy of the data files and one-value-changed copies of it for the other
  tests.
- Weapon and world tuning: each key of `weapons.txt` and `world.txt` sets its own value, the shipped files load,
  the pellet pattern is a centre pellet and the loaded rings, the falloff and the blast's radius, damage and cost
  run in a straight line between the loaded values, a changed value
  changes the fire rate, the rings, the blast, the meter, the zoom and the pickups, and a missing, unknown or
  invalid value fails naming the file and the key, as do a pickup that gives less than 1, a zoom factor below 1
  and an energy meter too small for a dash or for the smallest blast (`WeaponTuningTest`, `WorldTuningTest`).
  `TunedWeaponSimulationTest` plays changed values through the whole simulation: shot damage and range, recoil
  kicks, the blast, how far each kind of shot is heard, pickups, grates and canisters, the toast and hit-marker
  times, the minimap reveal radius, the ammunition caps and the starter ammunition.
- The `loadout` header: every campaign stage starts with the weapons its own file lists, the header sets the
  loadout wherever the stage stands in a campaign, a stage without the header starts with the pistol alone, and
  which weapon is in hand (`StageLoadoutTest`); `StageLoadoutFileTest` reads the shipped and edited stage files through
  `Levels` and the menus. A `loadout` header in the Training Range's file fails the load, naming the file and
  the line (`LevelsErrorMessagesTest`).
- Palettes and render settings (`PalettesTest`): every row of a palette file sets its own colour and every key of
  `render.txt` its own value, the shipped files load, the files name their materials and accents as the code's enums
  do, an edited colour reaches the packed vertices, edited render settings change the fog, the camera and the
  tables, and an unknown, missing or malformed row fails naming the file and the key.
- Wall textures (`WallTexturesTest`): every wall type takes the texture of its own line, the shipped `walls.txt`
  loads, a wall character without a line and a line for anything else fail the load, as does a value that is not a plain
  `.png` file name, an edited loose copy changes a wall's texture, a table built in code is checked the same way,
  and a texture file shared by wall types is loaded once.
- Shaders: every shader file loads headless and through libGDX, and a missing one fails naming the file
  (`ShadersTest`); the files are plain ASCII, each vertex shader hands its fragment shader the varyings that one
  reads, they declare exactly the uniforms and attributes the renderers set, and repeat the face shades baked
  into the map, and the
  renderers keep no shader source in Java strings (`ShaderFilesTest`); the renderers compile the text of those
  files and name them in a compile error (`RendererSupportTest`).
- Sound volumes (`SoundVolumesTest`): every cue takes the gain of its own line, the shipped `sound-volumes.txt`
  loads, an altered line changes that cue and no other, `SoundManager` plays a cue at the gain of its table, and
  a missing,
  unknown, repeated, negative or malformed line fails naming the file and the key.
- `tools` (`:tools:test`): `GeneratedModelsTest` and `GeneratedSoundsTest` run `GenerateAllModels` and
  `GenerateAllSounds` and check that every shipped `.obj` and `.wav` matches the generators byte for byte;
  `GeneratedModelsTest` also checks the indexed vertex deduplication. `GeneratedTexturesTest` checks that every
  shipped `.png` matches `GenerateAllTextures` pixel for pixel, that every painter fills a square with a
  power-of-two side, that libGDX decodes each file to the painted pixels, that the files are 8-bit RGBA with no
  colour chunks, and that
  the game loads exactly the generated textures. `ModelPreviewAppTest` checks that the previewer keeps no shader
  source of its own and sets every uniform the game's model shader declares, and `MeshBuilderTest` that the
  Gradle scripts declare the texture generator's output and order its consumers after it.

Rendering, the drawing of the HUD and the menus on screen, the pictures the demo scripts save and OpenAL audio
hardware playback have no tests.

A GitHub Actions workflow (`.github/workflows/build.yml`) runs `./gradlew build` with Java 21 on Linux, Windows and
macOS on every pull request and push to `main`.

## Gradle

The project uses the [Gradle](https://gradle.org/) wrapper: run tasks with `./gradlew`, or `gradlew.bat` on
Windows.

- `desktop:run`: starts the game.
- `tools:generateModels`: regenerates every sculpted `.obj` model in `assets/models/`.
- `tools:generateSounds`: regenerates every synthesized `.wav` sound effect in `assets/sounds/`. To add a sound, see
  [Adding a Sound](tools/README.md#adding-a-sound).
- `tools:generateTextures`: regenerates every painted `.png` texture in `assets/textures/`.
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

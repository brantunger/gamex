# Viewmodel sculpts

`HandSculpt.java` generates the first-person hand, wrist, cuff and sleeve as `assets/models/viewmodel-hand.obj`, and
`PistolSculpt.java` generates the first-person pistol as `assets/models/viewmodel-pistol.obj`. The game loads both `.obj`
files at start-up (into `HandModel` and `PistolModel`).

## Regenerate the models

From the repository root, with a JDK (11 or later) and nothing else:

```
java tools/viewmodel/HandSculpt.java assets/models/viewmodel-hand.obj
java tools/viewmodel/PistolSculpt.java assets/models/viewmodel-pistol.obj
```

Each file is written whole each time, and the same source always writes the same bytes. Commit the generators and
the `.obj` files together.

If `HandSculpt.java` cannot uncross the rings of a part (see "How the sculpt is put together"), it stops with
`The rings of <part> are still crossed after 2000 sweeps` and leaves the file as it was.

## Check the result

```
./gradlew core:test
```

`PistolModelTest` checks that every solid in `viewmodel-pistol.obj` is closed, convex, and faces outwards.
`HandModelTest` loads both written files and checks the hand against the real pistol: every part is a closed surface that
faces outwards, no quad folds when it is split into two triangles, no glove vertex is inside the pistol, the fingers
and the palm lie on the grip's surface, the fingers wrap the grip and end on its left side, the index finger reaches
the trigger, and the forearm leaves towards the lower right.

To look at it, run the game's debug views (the variables are described in `DemoRun`):

```
GAMEX_SCREENSHOT=hand.png GAMEX_VIEWMODEL_ORBIT=90,10 ./gradlew desktop:run
GAMEX_SCREENSHOT=view.png ./gradlew desktop:run
```

The first draws the pistol and hand alone from the left side; the second saves the normal first-person view.

## How the sculpt is put together

- Everything is in the pistol's gun space: the barrel points along negative z, y is up, x is to the shooter's right,
  and the origin is the middle of the underside of the slide. Lengths are roughly metres.
- The hand is posed on a skeleton: a path of joints for each finger, the thumb, and the palm-to-forearm line. Each
  path is smoothed into a curve and skinned as a tube of elliptical cross-sections, so a finger is one surface with a
  pinch at each crease and a rounded tip.
- Glove vertices that would land inside the pistol are pushed back out to its surface. That is what presses the
  fingers and the palm against the grip. The outlines of the grip, frame, slide, beavertail and magazine plate at the
  top of `HandSculpt.java` are copies of the ones in `PistolSculpt.java`; if the pistol changes, change them too. `HandModelTest` measures the
  written file against the real grip from `viewmodel-pistol.obj`, and fails if the grip has grown into the glove or
  shrunk away from it: on either side, at the front strap or at the back strap. It does not notice a change to a part
  of the pistol that the hand does not touch, or a change smaller than about 2 mm.
- Each cross-section is set square to its path, and corner i of one ring is joined to corner i of the next. On the
  inside of a tight bend, and where the push moves one ring further than the next, a corner can end up behind the same
  corner of the ring before it, and the quad between the two folds or faces inwards. So after the push, the rings of
  the palm, the fingers and the thumb are uncrossed (`uncrossRings`): such corners are slid apart, along the line
  between the two ring centres, until each is ahead by at least a tenth of the distance between those centres
  (`RING_GAP`). On the palm only the later corner moves; elsewhere the two share the move. The push runs again after
  every sweep that moved a corner, until a sweep moves none.
- Normals are smoothed within a part. Parts do not share vertices, so the edge between two parts, such as the cuff
  against the glove, stays hard.
- Each part is an OBJ group (`g palm`, `g thumb`, `g sleeve`, ...) with one material (`usemtl glove`, `pads`, `cuff`,
  `strap` or `sleeve`). The colours of the materials are in `HandModel.Material`, not in the file.

## Where to make changes

- Finger thickness, creases and the paths round the grip: `finger`, `wrappingPath`, `indexPath`.
- The palm, heel and wrist: the stations in `palm`.
- The thumb: the stations in `thumb`.
- The cuff, strap and the folds of the sleeve: `cuff`, `sleeve`.
- The number of rings of a part, or of corners in a ring: `noQuadFolds` in `HandModelTest` pins the number of quads
  it checks, 3,675, so change that number too.
- The direction of the forearm: `ARM`. The first-person pose depends on it, so check the in-game view afterwards.

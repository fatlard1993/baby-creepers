# Development

## Building

```
./gradlew build
```

or, from anywhere in the suite, `mc-build baby-creepers`. Compiles against Pandorical's working tree as a subproject.

## Installing

Server-side. Drop the jar in the server's `mods/` alongside Pandorical. Players need nothing.

## How it works

`CreeperMixin` wraps the one `ServerLevel.explode` call in `Creeper.explodeCreeper`. The wrapper calls it with `ExplosionInteraction.NONE` (entity damage and knockback, no blocks) and halves the radius for a baby. Then it calls `Spores.burst` with the radius it actually used, which is after the game has doubled it for a charged creeper.

`Spores.burst` sends a burst of large green dust and adds an `AreaEffectCloud` with no effects and that dust as its particle, shrinking to nothing over 120 ticks. It then rolls `INFECT_CHANCE` for every block in `#baby-creepers-justfatlard:sporeable` whose centre is within the radius. The upper half of tall grass is skipped, so a tall plant is rolled once.

`Infection` is per-level `SavedData`: block position to an entry of a hatch time and a `Clock`. `infect` rolls `DORMANT_CHANCE` (1/2) between `QUICK` (hatches at game time + 1 to 3 minutes) and dormant, which starts at `WAIT_NIGHT` by day or `WAIT_DAY` at night, so the night it was infected in does not count. Every ten ticks it walks every entry:

- the sky first, for every entry loaded or not, from `isDarkOutside` (26.3 keeps time in clock managers, so the sky is read rather than the time worked out): `WAIT_DAY` → `WAIT_NIGHT` at light, `WAIT_NIGHT` → `NIGHT` at dark with a hatch time up to `NIGHT_SPREAD` (2 minutes) on, and `NIGHT` back to `WAIT_NIGHT` if light returns first (a slept-through night)
- then, in entity-ticking chunks only: no longer sporeable (broken, mowed, tilled) is dropped; `QUICK` or `NIGHT` past its time is dropped and hatched; anything else puffs one small dust particle

Hatching is skipped on Peaceful. A block with a collision shape is turned to dirt and the baby stands above it; a plant is destroyed and the baby stands in it. If the baby would not fit, nothing happens and the grass survives.

`BabyCreepers` follows baby-skeletons: a `baby_creeper` scoreboard tag is the truth, and fixed-id permanent attribute modifiers (scale, health, speed) are said again on every `ENTITY_LOAD`.

### The baby model

`baby_creeper.json` in `src/main/resources/assets/baby-creepers-justfatlard/pandorical/entity_models/` is Pandorical part transforms, not new geometry, so the creeper keeps its own texture, animation and charged layer. Pandorical applies each as `pose * scale + offset` to the top-level part. Against 26.3's `CreeperModel` (head pivot y 6 with the 8-cube above it, body pivot y 6 with 12 below, legs pivot y 18 at x ±2, z ±4 with 6 below, soles at 24):

| part | scale | offset | result |
|---|---|---|---|
| head | 0.75 | (0, 11.5, 0) | 6-cube, neck at 16 |
| body | (0.625, 5/12, 0.75) | (0, 13.5, 0) | 5 × 5 × 3, from 16 to 21 |
| legs | (0.625, 0.5, 0.75) | (0, 12, 0) | 2.5 × 3 × 3, hips at 21, at x ±1.25, z ±3 |

14 pixels crown to sole. `BabyCreepers.applySize` (every birth, egg, `/summon` and load) calls `entityModels().set(creeper, MODEL, null, 1 / SCALE)`: the model is small already, so it is drawn at twice the scale attribute's half, while the server keeps the half for the hitbox and vanilla clients. Pandorical forgets models when an entity unloads, which is why it is set on every load.

### Spawn eggs

`Eggs` is baby-skeletons' egg handler. On `UseEntityCallback`, a creeper spawn egg used on any creeper creates a creeper with `SPAWN_ITEM_USE`, finalizes it, makes it a baby, applies the egg's components and consumes one. The game's own path (`SpawnEggItem.spawnOffspringFromSpawnEgg`) asks the offspring to be a baby, which a creeper cannot be, so it hatches nothing on its own.

### Growing up, and friendly babies

`Growth` counts a baby's loaded ticks up to `GROW_TICKS` (24000, vanilla's `BABY_START_AGE`) in a persistent Fabric attachment, `baby-creepers-justfatlard:growth` (`age`, `locked`), ticked from `Creeper.tick` (`CreeperMixin`), so a creeper without AI grows too. A baby without the attachment (`/summon` with the tag) is a newborn. When its time comes it grows only if `EntityType.getDimensions().makeBoundingBox` at its feet is clear, checked every 20 ticks. `BabyCreepers.growUp` drops the tag, the attachment, the three modifiers and the Pandorical model, and keeps the health fraction.

The golden dandelion is vanilla's lock, through the helpers `AgeableMob` offers any mob, the same ones the tadpole uses: `canUseGoldenDandelion` (the item, a baby, the 40-tick cooldown, `#cannot_be_age_locked`), `setAgeLocked` (consume, persistence, sound) and `makeAgeLockedParticle`. The particle timer is a non-persistent attachment.

A locked baby is passive (`BabyCreepers.isPassive`): `CreeperMixin` cancels `Creeper.setTarget` for any non-null target, which every targeting goal (the nearest-player one and the strike-back one) goes through, and locking clears its target and lets an unlit fuse go out. Ignition is untouched.

### Why 1 in 20

A grown blast (radius 3) reaches about 50 grass blocks and plants (25 of each) in a lush meadow; a baby's (1.5) about 18 (9 of each). At 1 in 20 a baby's blast hatches under one baby on average, so a chain of babies dies out. Above about 1 in 18 it would not.

## The icon

```
python3 generate_icon.py
```

Cuts the creeper's face out of the vanilla texture in the Loom cache and lays a grown one and a half-size baby on a fog of spores. Deterministic.

## Tests

```
xvfb-run -a -s "-screen 0 2048x1152x24" ./gradlew runClientGameTest -Ptest.level=full
```

Without `-Ptest.level=full` only the smoke test does anything. Each test prints `[baby-creepers test] PASSED <name>`.

- **SmokeTest**: the mod loads, its mixin binds, and a world comes up.
- **BlastLeavesSpores**: in a walled meadow of grass and short grass, a pig 4.5 blocks off is untouched by a baby's blast and hurt by a grown one's. No grass block, plant or cobblestone breaks. A green cloud lingers. Everything infected is sporeable and inside the blast. Twenty more bursts infect about the share `1 - (1 - p)^20` predicts (it prints both).
- **InfectedGrassHatches**: a grass block, short grass, tall grass and a fourth plant are given quick infections. The fourth is mowed and its infection goes. The server sprints through the longest wait (`/tick sprint`). The grass block is dirt, the plants are gone, the ground under the short grass is still grass, and three babies stand there at half scale, 10 health and half again as fast.

- **EggsHatchBabies**: in survival, a named creeper egg on a grown creeper hatches one named baby with its model and uses one egg; on a baby it hatches another; a zombie egg hatches nothing.
- **BabiesLookLikeBabies**: on the test's own Pandorical client, the baby's model, posed as the game poses it, is drawn at scale 1, 14/16 of a block tall with a 6/16 head; a grown creeper at half scale (what a vanilla client draws) measures a 4/16 head, so the check can tell them apart. It takes a picture, `baby-creeper-model`, of a grown creeper and a baby side by side, into the run's `screenshots/`.

- **DormantGrassWaitsForNight**: a dormant infection taken by day waits through the day, notices midnight, and hatches that night; one taken at midnight lets that night pass, starts waiting at morning, and goes back to waiting when morning returns before it hatches (`/time set` standing in for sleep), then hatches the next night.
- **BabiesGrowUp**: a baby forty ticks short of grown grows up: full scale, 20 max health with its half health kept as half, a grown pace, no model, no attachment. A dandelion locks one (used up, persistent, cooldown honoured) and it stays a baby; a baby under a one-high ceiling waits. A second dandelion and a removed ceiling let both grow.
- **FriendlyBabies**: a survival player stands between a dandelion baby and an ordinary one and hits the dandelion one. The ordinary one targets them (the control); the friendly one neither targets them nor swells. A second dandelion and it hunts them again.

## What to check by hand

- **How the fog looks.** The tests check the cloud exists and is green, not whether it reads as spores in play.
- **How infected grass reads.** One small particle per block every half second. Too subtle or too loud is a matter of looking.
- **How a baby looks moving and swelling.** The test measures it standing; the walk and the swell are the creeper's own animation on resized parts.
- **Save and reload.** Infections are `SavedData` and babies are tagged, both of which the game saves, but no test restarts the world mid-wait.

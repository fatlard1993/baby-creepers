# Baby Creepers

Creepers have babies now, and they grow out of the lawn. A creeper bursts into a fog of spores instead of a crater: the blast still hurts, no block breaks, and the grass it reaches may sprout baby creepers, a minute or two later or the next night.

## What This Mod Does

A creeper going off is still the worst moment of your evening: the same hiss, the same blast, the same damage and knockback to everything nearby. What changes is what it leaves. There is no hole in your wall or your lawn. There is a green fog where the creeper stood, drifting and thinning for a few seconds, and somewhere in the grass around it the spores have taken.

Server-side. Every client sees it, vanilla included.

## The Blast

| | Before | Now |
|---|---|---|
| Damage and knockback | as vanilla | as vanilla |
| Blocks | broken | none broken |
| Afterwards | a crater | a green spore cloud the size of the blast, gone in six seconds |

A charged creeper's blast is still twice the size, and so is its cloud. A creeper carrying potion effects still leaves its lingering potion cloud as well. The spore cloud itself hurts nothing.

## Infected Grass

**Each grass block, short grass or tall grass the blast reaches has a one-in-twenty chance of being infected.** A grown creeper infects about one block on bare grass, two or three in a meadow of tall grass.

**Infected grass puffs green spores** while it waits. Nothing else about it changes: it is still grass.

**Then it hatches, on one of two clocks,** as often as each:

- **Soon:** one to three minutes later.
- **The next night:** it lies dormant in the grass until dark. Grass infected by day hatches that night; grass infected at night lets that night pass and hatches the next. A dormant meadow goes off over the first couple of minutes of dark, not all at once. Sleeping through the night only puts it off to the night after.

When it hatches, a plant is eaten away, a grass block is eaten down to dirt, and a baby creeper stands where it was.

**Break it, mow it or till it before then and nothing hatches.** The infection goes with the grass. On Peaceful, the spores die where they are.

An infection in a part of the world nobody is near waits, and hatches when somebody comes back; a dormant one still counts the nights while it waits. Infections are saved with the world. In a world without nights (the daylight cycle switched off at noon), dormant grass never hatches.

Which blocks spores take root in is the block tag `baby-creepers-justfatlard:sporeable`. A datapack can add to it.

## Baby Creepers

| | Grown | Baby |
|---|---|---|
| Size and hitbox | 1 | half |
| Health | 20 | 10 |
| Speed | 1 | half again as fast |
| Blast radius | 3 | 1.5 |

A baby is the game's own creeper, so lightning charges one, cats scare it, and its blast spores too. Its smaller blast reaches less grass, which is what keeps babies from breeding faster than they die: on average a baby's spores hatch less than one more baby.

**Babies grow up** after twenty minutes, as baby animals do, into an ordinary creeper at the same share of its health. A baby with a ceiling too low for a grown creeper waits until it has room.

**A golden dandelion keeps a baby a baby, and makes it friendly.** Used on a baby creeper, it stops it growing (vanilla's sound and particles, the dandelion used up) and calms it. A friendly baby never goes after anyone, even someone who hits it, so it never hisses, and it no longer despawns. A second dandelion lets it go: it grows again, and it is a creeper again. Flint and steel still lights a friendly one.

**For a baby for certain:**

- **Use a creeper spawn egg on a creeper**, grown or baby. It hatches a baby, the way an egg used on a zombie hatches a baby zombie. A named egg names the baby, and survival uses the egg up. An egg used on the ground still makes a grown creeper.
- **`/summon creeper ~ ~ ~ {Tags:["baby_creeper"]}`.**

Which creeper is a baby is written on it as a scoreboard tag, `baby_creeper`, saved with the mob and readable with `/tag`.

## What One Looks Like

**With Pandorical**, a baby creeper is shaped like a baby: a big head on a short body and stubby legs, 14 pixels tall. It keeps the creeper's own texture, walk and swell, and its charged glow when lightning strikes it.

**Without it**, on a vanilla client, a baby is a grown creeper at half size: about as tall, with a smaller head and longer legs. Nothing else differs.

## None Of It Is New

No new blocks, no new mobs. Infected grass is vanilla grass with particles over it, and a baby is a vanilla creeper at half size, because size travels as one of the game's own attributes. That is why a client needs nothing installed; Pandorical only changes its proportions.

**Removing this mod** brings back block damage. Babies stay small, because their size is saved on the mob. Grass that was infected stays plain grass.

## Pandorical

Pandorical is required on the server; the mod will not load without it. It introduces the mod to players who join, and tells returning players what changed.

**With Pandorical client-side**, babies are drawn with the baby model (see [What One Looks Like](#what-one-looks-like)). **Without it**, nothing is lost but their proportions.

## Development

Building, the icon and the tests are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).

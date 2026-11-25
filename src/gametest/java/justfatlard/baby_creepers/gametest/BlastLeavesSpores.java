package justfatlard.baby_creepers.gametest;

import justfatlard.baby_creepers.BabyCreepers;
import justfatlard.baby_creepers.Infection;
import justfatlard.baby_creepers.Spores;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static justfatlard.baby_creepers.gametest.Kit.check;

/**
 * A creeper's blast in a meadow: it hurts what it always hurt, a baby's reaches half as far, no block
 * breaks, a green fog lingers, and the grass it infects is grass inside the blast, at about the
 * chance it should be.
 */
public final class BlastLeavesSpores implements FabricClientGameTest {

	private static final int HALF = 6;
	/** A grown creeper's blast hurts out to six blocks and a baby's to three; this is between. */
	private static final double PIG_DISTANCE = 4.5;
	private static final int BURSTS = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, Kit.MOD_ID, session -> {
			Kit.prepare(session);
			BlockPos center = Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				BlockPos c = Kit.meadow(session, level, 16, HALF);
				for (int dx = -HALF; dx <= HALF; dx++) {
					for (int dz = -HALF; dz <= HALF; dz++) level.setBlockAndUpdate(c.offset(dx, 0, dz), Blocks.SHORT_GRASS.defaultBlockState());
				}
				// A block for the blast to break, on the side away from the pig so it shields nothing. Glass,
				// because the grass under an opaque block dies back to dirt on its own within the test.
				level.setBlockAndUpdate(c.north(2), Blocks.GLASS.defaultBlockState());
				return c;
			});

			// A baby first: its pig is out of reach.
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				Creeper baby = Kit.spawn(level, EntityTypes.CREEPER, center, 0, 0);
				BabyCreepers.become(baby);
				Kit.spawn(level, EntityTypes.PIG, center, PIG_DISTANCE, 0);
				baby.ignite();
			});
			session.waitTicks(60);
			float babyPig = Kit.ask(session, server -> pig(server.overworld(), center));
			check(babyPig == 0F, "a baby's blast reached " + PIG_DISTANCE + " blocks: the pig lost " + babyPig);
			Kit.ask(session, server -> {
				server.overworld().getEntitiesOfClass(Pig.class, area(center)).forEach(Pig::discard);
				return null;
			});

			// Then a grown one: the same distance, and its pig is hurt.
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				Kit.spawn(level, EntityTypes.PIG, center, PIG_DISTANCE, 0);
				Kit.spawn(level, EntityTypes.CREEPER, center, 0, 0).ignite();
			});
			session.waitTicks(60);

			String result = Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				float lost = pig(level, center);
				check(lost > 0F, "a grown creeper's blast did not hurt a pig " + PIG_DISTANCE + " blocks off");
				check(level.getEntitiesOfClass(Creeper.class, area(center)).isEmpty(), "a creeper is still standing");

				check(level.getBlockState(center.north(2)).is(Blocks.GLASS), "the blast broke the glass");
				int grass = 0, plants = 0;
				for (int dx = -HALF; dx <= HALF; dx++) {
					for (int dz = -HALF; dz <= HALF; dz++) {
						if (level.getBlockState(center.offset(dx, -1, dz)).is(Blocks.GRASS_BLOCK)) grass++;
						if (level.getBlockState(center.offset(dx, 0, dz)).is(Blocks.SHORT_GRASS)) plants++;
					}
				}
				int side = 2 * HALF + 1;
				check(grass == side * side, "the blast broke grass blocks: " + grass + " of " + side * side + " left");
				check(plants == side * side - 1, "the blast broke plants: " + plants + " of " + (side * side - 1) + " left");

				var clouds = level.getEntitiesOfClass(AreaEffectCloud.class, area(center));
				check(!clouds.isEmpty(), "no spore cloud lingers");
				check(clouds.stream().allMatch(c -> c.getParticle() instanceof DustParticleOptions dust
						&& Math.abs(dust.getColor().x() - ((Spores.GREEN >> 16) & 0xFF) / 255F) < 0.01F),
					"the cloud is not spore green");

				// Everything infected is grass the grown blast (radius 3) or the baby's reached.
				Infection infection = Infection.of(level);
				Vec3 blast = Vec3.atBottomCenterOf(center);
				int infected = 0;
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-HALF, -2, -HALF), center.offset(HALF, 2, HALF))) {
					if (!infection.isInfected(pos)) continue;
					infected++;
					check(pos.distToCenterSqr(blast) <= 9.0001, "infected outside the blast: " + pos);
					check(level.getBlockState(pos).is(Spores.SPOREABLE), "infected something that is not grass: " + level.getBlockState(pos));
				}
				return "pig lost " + lost + ", " + infected + " infected, " + clouds.size() + " clouds";
			});
			System.out.println("[baby-creepers test] blast: " + result);

			// The rate: twenty more bursts on the same spot. Each block escapes every one of them with
			// chance (1 - p)^20, about 36%, so about 64% of the grass in reach ends up infected.
			String rate = Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				Creeper stand = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
				stand.snapTo(Vec3.atBottomCenterOf(center));
				for (int i = 0; i < BURSTS; i++) Spores.burst(level, stand, 3F);
				stand.discard();
				Infection infection = Infection.of(level);
				Vec3 blast = Vec3.atBottomCenterOf(center);
				int reach = 0, infected = 0;
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -4, -4), center.offset(4, 4, 4))) {
					if (pos.distToCenterSqr(blast) > 9) continue;
					BlockState state = level.getBlockState(pos);
					if (!state.is(Spores.SPOREABLE)) continue;
					if (state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) continue;
					reach++;
					if (infection.isInfected(pos)) infected++;
				}
				double expected = 1 - Math.pow(1 - Spores.INFECT_CHANCE, BURSTS);
				double share = (double) infected / reach;
				check(share > expected - 0.2 && share < expected + 0.2,
					infected + " of " + reach + " infected after " + BURSTS + " bursts, expected about " + Math.round(expected * 100) + "%");
				return infected + " of " + reach + " (expected about " + Math.round(expected * 100) + "%)";
			});
			System.out.println("[baby-creepers test] rate: " + rate);
		});
		Kit.passed("BlastLeavesSpores");
	}

	private static AABB area(BlockPos center) {
		return new AABB(center).inflate(HALF + 2, 4, HALF + 2);
	}

	/** How much health the pig in the meadow has lost. */
	private static float pig(ServerLevel level, BlockPos center) {
		var pigs = level.getEntitiesOfClass(Pig.class, area(center));
		check(pigs.size() == 1, "expected one pig, found " + pigs.size());
		return pigs.getFirst().getMaxHealth() - pigs.getFirst().getHealth();
	}
}

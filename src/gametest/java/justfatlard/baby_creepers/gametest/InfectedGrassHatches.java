package justfatlard.baby_creepers.gametest;

import java.util.List;

import justfatlard.baby_creepers.BabyCreepers;
import justfatlard.baby_creepers.Infection;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import static justfatlard.baby_creepers.gametest.Kit.check;

/**
 * Infected grass comes due: a grass block is eaten to dirt, short and tall grass are eaten away,
 * each leaves a baby creeper of the right size, health and speed, and grass mowed first leaves none.
 * The wait is minutes, so the server sprints through it.
 */
public final class InfectedGrassHatches implements FabricClientGameTest {

	private static final int HALF = 4;
	/** The longest an infection can wait, and a little over. */
	private static final int SPRINT = Infection.HATCH_MIN + Infection.HATCH_SPREAD + 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, Kit.MOD_ID, session -> {
			Kit.prepare(session);
			Kit.command(session, "time set day");
			// No random ticks: a grass block eaten to dirt early in the sprint would otherwise be grown
			// back over by the grass beside it before the last infection hatched.
			Kit.command(session, "gamerule random_tick_speed 0");
			BlockPos center = Kit.ask(session, server -> Kit.meadow(session, server.overworld(), -16, HALF));
			BlockPos grassBlock = center.offset(-2, -1, 0);
			BlockPos shortGrass = center.offset(2, 0, 0);
			BlockPos tallGrass = center.offset(0, 0, 2);
			BlockPos mowed = center.offset(0, 0, -2);

			session.onServer(server -> {
				ServerLevel level = server.overworld();
				level.setBlockAndUpdate(shortGrass, Blocks.SHORT_GRASS.defaultBlockState());
				level.setBlockAndUpdate(mowed, Blocks.SHORT_GRASS.defaultBlockState());
				level.setBlockAndUpdate(tallGrass, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
				level.setBlockAndUpdate(tallGrass.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
				Infection infection = Infection.of(level);
				for (BlockPos pos : List.of(grassBlock, shortGrass, tallGrass, mowed)) infection.infect(level, pos, false);
			});
			check(Kit.ask(session, server -> {
				Infection infection = Infection.of(server.overworld());
				return infection.isInfected(grassBlock) && infection.isInfected(shortGrass)
					&& infection.isInfected(tallGrass) && infection.isInfected(mowed);
			}), "the grass did not take the infection");

			// Mown before it hatches: the infection goes with the grass.
			session.onServer(server -> server.overworld().destroyBlock(mowed, false));
			session.waitTicks(25);
			check(!Kit.ask(session, server -> Infection.of(server.overworld()).isInfected(mowed)),
				"mowed grass is still infected");

			Kit.command(session, "tick sprint " + SPRINT);
			boolean hatched = false;
			for (int wait = 0; wait < 300 && !hatched; wait++) {
				session.waitTicks(20);
				hatched = Kit.ask(session, server -> {
					Infection infection = Infection.of(server.overworld());
					return !infection.isInfected(grassBlock) && !infection.isInfected(shortGrass) && !infection.isInfected(tallGrass);
				});
			}
			Kit.command(session, "tick sprint stop");
			check(hatched, "nothing hatched in " + SPRINT + " ticks");

			String result = Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				check(level.getBlockState(grassBlock).is(Blocks.DIRT), "the grass block was not eaten to dirt: " + level.getBlockState(grassBlock));
				check(level.getBlockState(shortGrass).isAir(), "the short grass is still there");
				check(level.getBlockState(shortGrass.below()).is(Blocks.GRASS_BLOCK), "the ground under the short grass was eaten too");
				check(level.getBlockState(tallGrass).isAir() && level.getBlockState(tallGrass.above()).isAir(), "the tall grass is still there");

				List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, new AABB(center).inflate(HALF + 1, 4, HALF + 1));
				List<Creeper> babies = creepers.stream().filter(BabyCreepers::is).toList();
				check(babies.size() == 3, "expected three babies, found " + babies.size() + " of " + creepers.size() + " creepers");
				for (Creeper baby : babies) {
					check(near(baby.getAttributeValue(Attributes.SCALE), BabyCreepers.SCALE), "a baby is not half size");
					check(near(baby.getMaxHealth(), 10), "a baby's health is " + baby.getMaxHealth());
					var speed = baby.getAttribute(Attributes.MOVEMENT_SPEED);
					check(near(speed.getValue(), speed.getBaseValue() * (1 + BabyCreepers.SPEED_BONUS)), "a baby is not half again as fast");
				}
				return babies.size() + " babies";
			});
			System.out.println("[baby-creepers test] hatched: " + result);
		});
		Kit.passed("InfectedGrassHatches");
	}

	private static boolean near(double actual, double expected) {
		return Math.abs(actual - expected) < 1.0E-4;
	}
}

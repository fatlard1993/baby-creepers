package justfatlard.baby_creepers.gametest;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import justfatlard.pandorical.gametest.Smoke;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/** What the tests share: a meadow in the sky, and a way to ask the server things. */
final class Kit {
	private Kit() {}

	static final String MOD_ID = "baby-creepers-justfatlard";

	/**
	 * Monsters allowed, and the player in creative, so no creeper walks over to the player and
	 * nothing the tests set off hurts them.
	 */
	static void prepare(Smoke.Session session) {
		command(session, "difficulty normal");
		command(session, "gamemode creative @a");
		session.waitTicks(2);
	}

	/** From the server's console: the test player has no permission to run commands. */
	static void command(Smoke.Session session, String command) {
		session.onServer(server -> server.getCommands().performPrefixedCommand(
			server.createCommandSourceStack().withPosition(session.player().position()), command));
	}

	static <T> T ask(Smoke.Session session, Function<MinecraftServer, T> question) {
		AtomicReference<T> answer = new AtomicReference<>();
		session.onServer(server -> answer.set(question.apply(server)));
		return answer.get();
	}

	/**
	 * A floating square of grass blocks on stone, walled in two high so nothing wanders off, clear of
	 * whatever the world generated. Returns the centre of the meadow's surface: the block a mob
	 * standing on the grass occupies.
	 */
	static BlockPos meadow(Smoke.Session session, ServerLevel level, int east, int half) {
		BlockPos ground = session.player().blockPosition().offset(east, 12, 0);
		for (int dx = -half - 1; dx <= half + 1; dx++) {
			for (int dz = -half - 1; dz <= half + 1; dz++) {
				BlockPos at = ground.offset(dx, 0, dz);
				for (int dy = 1; dy <= 6; dy++) level.setBlockAndUpdate(at.above(dy), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
				boolean edge = Math.abs(dx) > half || Math.abs(dz) > half;
				level.setBlockAndUpdate(at, (edge ? Blocks.STONE : Blocks.GRASS_BLOCK).defaultBlockState());
				if (edge) {
					level.setBlockAndUpdate(at.above(), Blocks.STONE.defaultBlockState());
					level.setBlockAndUpdate(at.above(2), Blocks.STONE.defaultBlockState());
				}
			}
		}
		return ground.above();
	}

	static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, BlockPos at, double dx, double dz) {
		T mob = type.create(level, EntitySpawnReason.COMMAND);
		if (mob == null) throw new AssertionError("could not create " + type);
		mob.snapTo(at.getX() + 0.5 + dx, at.getY(), at.getZ() + 0.5 + dz, 0F, 0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.COMMAND, null);
		mob.setPersistenceRequired();
		mob.setNoAi(true);
		level.addFreshEntity(mob);
		return mob;
	}

	static void passed(String test) {
		System.out.println("[baby-creepers test] PASSED " + test);
	}

	static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}

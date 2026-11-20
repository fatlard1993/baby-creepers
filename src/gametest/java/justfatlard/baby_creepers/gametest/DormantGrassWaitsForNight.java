package justfatlard.baby_creepers.gametest;

import java.util.function.Predicate;

import justfatlard.baby_creepers.BabyCreepers;
import justfatlard.baby_creepers.Infection;
import justfatlard.baby_creepers.Infection.Clock;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import static justfatlard.baby_creepers.gametest.Kit.check;

/**
 * A dormant infection waits for the next night. Taken by day, it hatches that night; taken at night,
 * it lets that night pass and hatches the one after. Day coming back before it hatches, as when
 * everybody sleeps, sends it back to waiting.
 */
public final class DormantGrassWaitsForNight implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, Kit.MOD_ID, session -> {
			Kit.prepare(session);
			Kit.command(session, "gamerule random_tick_speed 0");
			Kit.command(session, "time set day");
			session.waitTicks(2);
			BlockPos center = Kit.ask(session, server -> Kit.meadow(session, server.overworld(), 16, 4));
			BlockPos byDay = center.offset(-2, 0, 0);
			BlockPos byNight = center.offset(2, 0, 0);
			session.onServer(server -> {
				ServerLevel level = server.overworld();
				level.setBlockAndUpdate(byDay, Blocks.SHORT_GRASS.defaultBlockState());
				level.setBlockAndUpdate(byNight, Blocks.SHORT_GRASS.defaultBlockState());
				check(!level.isDarkOutside(), "it is not day after /time set day");
				Infection.of(level).infect(level, byDay, true);
			});
			check(clock(session, byDay) == Clock.WAIT_NIGHT, "taken by day, it does not wait for night: " + clock(session, byDay));
			session.waitTicks(40);
			check(clock(session, byDay) == Clock.WAIT_NIGHT, "a dormant infection moved on by day: " + clock(session, byDay));

			Kit.command(session, "time set midnight");
			waitFor(session, byDay, c -> c == Clock.NIGHT, 30, "dark came and it did not notice");
			session.onServer(server -> Infection.of(server.overworld()).infect(server.overworld(), byNight, true));
			check(clock(session, byNight) == Clock.WAIT_DAY, "taken at night, it does not let the night pass: " + clock(session, byNight));

			// This night: the day one hatches, the night one does not.
			Kit.command(session, "tick sprint " + (Infection.NIGHT_SPREAD + 200));
			waitFor(session, byDay, c -> c == null, 300, "the day one did not hatch that night");
			Kit.command(session, "tick sprint stop");
			check(clock(session, byNight) == Clock.WAIT_DAY, "the night one hatched the night it was taken: " + clock(session, byNight));
			check(babies(session, center) == 1, "expected one baby, found " + babies(session, center));

			// Morning, then dark again, then morning before it hatches: everybody slept.
			Kit.command(session, "time set day");
			waitFor(session, byNight, c -> c == Clock.WAIT_NIGHT, 30, "morning came and the night one did not start waiting for dark");
			Kit.command(session, "time set midnight");
			waitFor(session, byNight, c -> c != Clock.WAIT_NIGHT, 30, "dark came and the night one did not notice");
			Kit.command(session, "time set day");
			session.waitTicks(25);
			Clock slept = clock(session, byNight);
			// It may, about one time in a hundred, have hatched in the few ticks before morning.
			check(slept == Clock.WAIT_NIGHT || slept == null, "day came back and it did not go back to waiting: " + slept);

			if (slept != null) {
				Kit.command(session, "time set midnight");
				Kit.command(session, "tick sprint " + (Infection.NIGHT_SPREAD + 200));
				waitFor(session, byNight, c -> c == null, 300, "the night one did not hatch the next night");
				Kit.command(session, "tick sprint stop");
			}
			check(babies(session, center) == 2, "expected two babies, found " + babies(session, center));
		});
		Kit.passed("DormantGrassWaitsForNight");
	}

	private static Clock clock(Smoke.Session session, BlockPos pos) {
		return Kit.ask(session, server -> Infection.of(server.overworld()).clock(pos));
	}

	private static void waitFor(Smoke.Session session, BlockPos pos, Predicate<Clock> done, int seconds, String otherwise) {
		for (int i = 0; i < seconds * 4; i++) {
			if (done.test(clock(session, pos))) return;
			session.waitTicks(5);
		}
		throw new AssertionError(otherwise + " (" + clock(session, pos) + ")");
	}

	private static int babies(Smoke.Session session, BlockPos center) {
		return Kit.ask(session, server -> server.overworld()
			.getEntitiesOfClass(Creeper.class, new AABB(center).inflate(6, 4, 6), BabyCreepers::is).size());
	}
}

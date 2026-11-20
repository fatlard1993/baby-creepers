package justfatlard.baby_creepers.gametest;

import justfatlard.baby_creepers.BabyCreepers;
import justfatlard.baby_creepers.Growth;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import static justfatlard.baby_creepers.gametest.Kit.check;

/**
 * A baby grows up when its time comes: full size, full health, a grown one's pace, no baby model.
 * A golden dandelion holds it a baby and a second lets it go, a dandelion used each time; and a baby
 * under a low ceiling waits until there is room. The twenty minutes are skipped by setting a baby
 * forty ticks short of them, rather than sprinting through them.
 */
public final class BabiesGrowUp implements FabricClientGameTest {

	private static final int ALMOST = Growth.GROW_TICKS - 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, Kit.MOD_ID, session -> {
			Kit.prepare(session);
			int[] ids = Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				BlockPos at = Kit.meadow(session, level, 16, 5);
				Creeper free = baby(level, at, -3);
				check(Growth.state(free).age() == 0 && !Growth.state(free).locked(), "a new baby is not a newborn: " + Growth.state(free));
				free.setHealth(5F);
				almost(free);

				Creeper held = baby(level, at, 0);
				almost(held);
				ServerPlayer player = session.player();
				player.setGameMode(GameType.SURVIVAL);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_DANDELION, 2));
				check(use(player, level, held).consumesAction(), "the golden dandelion was not taken");
				check(Growth.state(held).locked(), "the dandelion did not lock the baby");
				check(player.getMainHandItem().getCount() == 1, "the dandelion was not used up");
				check(held.isPersistenceRequired(), "a locked baby can still despawn");
				// The cooldown: a second dandelion straight away does nothing.
				check(!use(player, level, held).consumesAction(), "the dandelion worked again inside its cooldown");

				// A one-block-high gap: a grown creeper would suffocate here.
				Creeper cramped = baby(level, at, 3);
				level.setBlockAndUpdate(at.offset(3, 1, 0), Blocks.STONE.defaultBlockState());
				almost(cramped);
				return new int[] {free.getId(), held.getId(), cramped.getId()};
			});
			session.waitTicks(80);

			Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				Creeper free = (Creeper) level.getEntity(ids[0]);
				check(!BabyCreepers.is(free), "a baby did not grow up when its time came");
				check(near(free.getAttributeValue(Attributes.SCALE), 1), "grown up but scale " + free.getAttributeValue(Attributes.SCALE));
				check(near(free.getMaxHealth(), 20), "grown up but max health " + free.getMaxHealth());
				check(near(free.getHealth(), 10), "half health as a baby should be half as a grown one, not " + free.getHealth());
				var speed = free.getAttribute(Attributes.MOVEMENT_SPEED);
				check(near(speed.getValue(), speed.getBaseValue()), "grown up but still quick");
				check(PandoricalApi.entityModels().get(free) == null, "grown up but still drawn as a baby");
				check(!free.hasAttached(Growth.STATE), "grown up but still carrying its growth");

				check(BabyCreepers.is(level.getEntity(ids[1])), "a dandelion-locked baby grew up");
				check(BabyCreepers.is(level.getEntity(ids[2])), "a baby grew up into a ceiling");

				// Let both go: the second dandelion unlocks, and the ceiling comes off.
				ServerPlayer player = session.player();
				check(use(player, level, (Creeper) level.getEntity(ids[1])).consumesAction(), "the second dandelion was not taken");
				check(!Growth.state((Creeper) level.getEntity(ids[1])).locked(), "the second dandelion did not unlock it");
				level.setBlockAndUpdate(level.getEntity(ids[2]).blockPosition().above(), Blocks.AIR.defaultBlockState());
				player.setGameMode(GameType.CREATIVE);
				return null;
			});
			// The unlocked one still has its last forty ticks to live, and room is asked every twenty.
			session.waitTicks(80);

			Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				check(!BabyCreepers.is(level.getEntity(ids[1])), "an unlocked baby did not grow up");
				check(!BabyCreepers.is(level.getEntity(ids[2])), "a baby given room did not grow up");
				return null;
			});
		});
		Kit.passed("BabiesGrowUp");
	}

	private static Creeper baby(ServerLevel level, BlockPos at, double dx) {
		Creeper baby = Kit.spawn(level, EntityTypes.CREEPER, at, dx, 0);
		BabyCreepers.become(baby);
		return baby;
	}

	private static void almost(Creeper baby) {
		baby.setAttached(Growth.STATE, new Growth.State(ALMOST, false));
	}

	private static InteractionResult use(ServerPlayer player, ServerLevel level, Creeper creeper) {
		return UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, creeper, null);
	}

	private static boolean near(double actual, double expected) {
		return Math.abs(actual - expected) < 1.0E-4;
	}
}

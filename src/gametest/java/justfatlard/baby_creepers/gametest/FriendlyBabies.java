package justfatlard.baby_creepers.gametest;

import justfatlard.baby_creepers.BabyCreepers;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import static justfatlard.baby_creepers.gametest.Kit.check;

/**
 * A baby given a golden dandelion is friendly: a survival player stands right beside it, hits it, and
 * it neither targets them nor swells, where an ordinary baby beside the same player does. A second
 * dandelion makes it a creeper again.
 */
public final class FriendlyBabies implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, Kit.MOD_ID, session -> {
			Kit.prepare(session);
			// The player stands in the meadow, between the two babies, in survival.
			int[] ids = Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				BlockPos at = Kit.meadow(session, level, 0, 4);
				ServerPlayer player = session.player();
				player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				player.setGameMode(GameType.SURVIVAL);

				Creeper friendly = baby(level, at, -2);
				Creeper hostile = baby(level, at, 2);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_DANDELION, 2));
				check(UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, friendly, null).consumesAction(),
					"the dandelion was not taken");
				check(BabyCreepers.isPassive(friendly), "a dandelion did not calm the baby");
				check(!BabyCreepers.isPassive(hostile), "an ordinary baby is passive");
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				// Struck: a creeper strikes back by targeting whoever hit it.
				friendly.hurtServer(level, level.damageSources().playerAttack(player), 1F);
				return new int[] {friendly.getId(), hostile.getId()};
			});
			session.waitTicks(20);

			String seen = Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				Creeper friendly = (Creeper) level.getEntity(ids[0]);
				Creeper hostile = (Creeper) level.getEntity(ids[1]);
				check(hostile.getTarget() == session.player(), "the ordinary baby beside the player did not target them, so the check proves nothing");
				hostile.discard();
				check(friendly.getTarget() == null, "the friendly baby targeted " + friendly.getTarget());
				check(friendly.getSwellDir() <= 0, "the friendly baby is swelling");
				return "ok";
			});
			check("ok".equals(seen), seen);

			// A second dandelion, after the cooldown: a creeper again.
			session.waitTicks(40);
			Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = session.player();
				Creeper baby = (Creeper) level.getEntity(ids[0]);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_DANDELION));
				check(UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, baby, null).consumesAction(),
					"the second dandelion was not taken");
				check(!BabyCreepers.isPassive(baby), "a second dandelion did not make it a creeper again");
				return null;
			});
			session.waitTicks(20);
			check(Kit.ask(session, server -> ((Creeper) server.overworld().getEntity(ids[0])).getTarget() == session.player()),
				"the unlocked baby did not go back to hunting");
			Kit.ask(session, server -> {
				server.overworld().getEntity(ids[0]).discard();
				session.player().setGameMode(GameType.CREATIVE);
				return null;
			});
		});
		Kit.passed("FriendlyBabies");
	}

	/** With its AI on, unlike {@link Kit#spawn}: targeting is what is under test. */
	private static Creeper baby(ServerLevel level, BlockPos at, double dx) {
		Creeper baby = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
		baby.snapTo(at.getX() + 0.5 + dx, at.getY(), at.getZ() + 0.5, 0F, 0F);
		baby.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.COMMAND, null);
		baby.setPersistenceRequired();
		level.addFreshEntity(baby);
		BabyCreepers.become(baby);
		return baby;
	}
}

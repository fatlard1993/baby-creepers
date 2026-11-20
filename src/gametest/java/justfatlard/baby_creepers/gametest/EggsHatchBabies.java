package justfatlard.baby_creepers.gametest;

import java.util.List;

import justfatlard.baby_creepers.BabyCreepers;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import static justfatlard.baby_creepers.gametest.Kit.check;

/**
 * A creeper spawn egg used on a creeper hatches a baby: on a grown one or a baby, named as the egg
 * is named, given its baby model, one egg used up in survival. Another kind's egg hatches nothing.
 */
public final class EggsHatchBabies implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, Kit.MOD_ID, session -> {
			Kit.prepare(session);
			String result = Kit.ask(session, server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = session.player();
				player.setGameMode(GameType.SURVIVAL);
				BlockPos at = Kit.meadow(session, level, 16, 3);
				Creeper grown = Kit.spawn(level, EntityTypes.CREEPER, at, 0, 0);

				ItemStack egg = new ItemStack(Items.CREEPER_SPAWN_EGG, 3);
				egg.set(DataComponents.CUSTOM_NAME, Component.literal("Sprout"));
				player.setItemInHand(InteractionHand.MAIN_HAND, egg);
				InteractionResult used = use(player, level, grown);
				check(used.consumesAction(), "the egg was not taken on a grown creeper: " + used);
				check(player.getMainHandItem().getCount() == 2, "survival did not use up the egg: " + player.getMainHandItem().getCount() + " left");
				check(!BabyCreepers.is(grown), "the grown creeper became a baby");

				List<Creeper> babies = babies(level, at);
				check(babies.size() == 1, "hatched " + babies.size() + " babies from a grown creeper");
				Creeper baby = babies.getFirst();
				check(Math.abs(baby.getScale() - BabyCreepers.SCALE) < 1.0E-4, "the hatched baby is " + baby.getScale() + " scale");
				check(baby.getCustomName() != null && "Sprout".equals(baby.getCustomName().getString()), "the egg's name did not carry: " + baby.getCustomName());
				check(BabyCreepers.MODEL.equals(PandoricalApi.entityModels().get(baby)), "the hatched baby has no baby model");

				// On a baby, as on a baby zombie: another baby.
				check(use(player, level, baby).consumesAction(), "the egg was not taken on a baby");
				check(babies(level, at).size() == 2, "an egg on a baby did not hatch another");

				// Another kind's egg is the game's business, and hatches no creeper.
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ZOMBIE_SPAWN_EGG));
				check(!use(player, level, grown).consumesAction(), "a zombie egg was taken by a creeper");
				check(babies(level, at).size() == 2, "a zombie egg hatched a creeper");

				player.setGameMode(GameType.CREATIVE);
				return "ok";
			});
			check("ok".equals(result), result);
		});
		Kit.passed("EggsHatchBabies");
	}

	private static InteractionResult use(ServerPlayer player, ServerLevel level, Creeper creeper) {
		return UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, creeper, null);
	}

	private static List<Creeper> babies(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(Creeper.class, new AABB(at).inflate(4), BabyCreepers::is);
	}
}

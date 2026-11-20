package justfatlard.baby_creepers;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * A creeper spawn egg used on a creeper, grown or baby, hatches a baby creeper, the way an egg used
 * on a zombie hatches a baby zombie. An egg used on the ground still makes a grown one.
 *
 * <p>Done here because the game's own path asks the offspring to be a baby, which a creeper has no
 * way to say yes to, so it hatches nothing. The egg's components (a custom name, entity data) are
 * applied as the game applies them, and survival uses one up.
 */
public final class Eggs {
	private Eggs() {}

	public static void register() {
		UseEntityCallback.EVENT.register(Eggs::onUse);
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
		if (!(entity instanceof Creeper parent) || player.isSpectator()) return InteractionResult.PASS;

		ItemStack held = player.getItemInHand(hand);
		if (!(held.getItem() instanceof SpawnEggItem)) return InteractionResult.PASS;
		if (!SpawnEggItem.spawnsEntity(held, parent.getType())) return InteractionResult.PASS;

		if (!(parent.getType().create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE) instanceof Creeper baby)) {
			return InteractionResult.PASS;
		}
		baby.snapTo(parent.getX(), parent.getY(), parent.getZ(), parent.getYRot(), 0F);
		baby.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(baby.blockPosition()), EntitySpawnReason.SPAWN_ITEM_USE, null);
		BabyCreepers.become(baby);
		baby.applyComponentsFromItemStack(held);
		serverLevel.addFreshEntity(baby);
		held.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}

package justfatlard.baby_creepers.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.baby_creepers.BabyCreepers;
import justfatlard.baby_creepers.Growth;
import justfatlard.baby_creepers.Spores;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class CreeperMixin {

	/**
	 * The blast still hurts and throws everything it would have, but breaks no blocks
	 * ({@code ExplosionInteraction.NONE}); a baby's is half the size; and spores fill the space.
	 * The radius is read here, after the game has doubled it for a charged creeper.
	 */
	@WrapOperation(method = "explodeCreeper", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"))
	private void babyCreepers$sporeBlast(ServerLevel level, Entity source, double x, double y, double z,
			float radius, Level.ExplosionInteraction interaction, Operation<Void> original) {
		Creeper creeper = (Creeper) (Object) this;
		float blast = BabyCreepers.blast(creeper, radius);
		original.call(level, source, x, y, z, blast, Level.ExplosionInteraction.NONE);
		Spores.burst(level, creeper, blast);
	}

	/** A baby's growing, on the server's tick. Creeper's own tick, so it runs for a creeper without AI too. */
	@Inject(method = "tick", at = @At("HEAD"))
	private void babyCreepers$grow(CallbackInfo ci) {
		Creeper creeper = (Creeper) (Object) this;
		if (creeper.level() instanceof ServerLevel level && BabyCreepers.is(creeper)) Growth.tick(creeper, level);
	}

	/** A friendly baby takes no target: not one it sees, and not one that hits it. */
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void babyCreepers$friendly(LivingEntity target, CallbackInfo ci) {
		if (target != null && BabyCreepers.isPassive((Creeper) (Object) this)) ci.cancel();
	}
}

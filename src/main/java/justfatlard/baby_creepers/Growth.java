package justfatlard.baby_creepers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

/**
 * A baby creeper grows up as a baby animal does: twenty minutes of being loaded, and a golden
 * dandelion holds it a baby until another lets it go. The lock is vanilla's own gesture, through the
 * helpers {@link AgeableMob} offers any mob (the tadpole, which is not ageable either, uses the same
 * ones): the sound, the particles, the cooldown, the dandelion used up, and a locked baby kept from
 * despawning. A locked baby is friendly, too: see {@link BabyCreepers#isPassive}.
 *
 * <p>A baby whose time has come waits while a grown creeper would not fit where it stands, so it
 * never grows into a ceiling and suffocates.
 */
public final class Growth {
	private Growth() {}

	/** {@code AgeableMob.BABY_START_AGE}: twenty minutes. */
	public static final int GROW_TICKS = -AgeableMob.BABY_START_AGE;
	/** How long the lock's particles show, and the cooldown before the dandelion works again: the tadpole's. */
	private static final int LOCK_PARTICLE_TICKS = 40;
	private static final int ROOM_CHECK_EVERY = 20;

	/** Ticks lived as a baby, counting up to {@link #GROW_TICKS}, and whether a dandelion holds it there. */
	public record State(int age, boolean locked) {
		public static final State NEWBORN = new State(0, false);
		static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("age").forGetter(State::age),
			Codec.BOOL.fieldOf("locked").forGetter(State::locked)
		).apply(instance, State::new));
	}

	/** Absent on a baby that has not ticked yet, such as one made by {@code /summon} with the tag: it is a newborn. */
	public static final AttachmentType<State> STATE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "growth"), State.CODEC);
	private static final AttachmentType<Integer> LOCK_PARTICLES = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "lock_particles"));

	public static void register() {
		UseEntityCallback.EVENT.register(Growth::onUse);
	}

	public static State state(Creeper baby) {
		return baby.getAttachedOrElse(STATE, State.NEWBORN);
	}

	static void born(Creeper baby) {
		baby.setAttached(STATE, State.NEWBORN);
	}

	/** Every server tick of a baby, from {@code Creeper.tick}. */
	public static void tick(Creeper baby, ServerLevel level) {
		State state = state(baby);
		int particles = baby.getAttachedOrElse(LOCK_PARTICLES, 0);
		if (particles > 0) {
			int left = AgeableMob.makeAgeLockedParticle(level, baby, particles, state.locked());
			if (left > 0) baby.setAttached(LOCK_PARTICLES, left);
			else baby.removeAttached(LOCK_PARTICLES);
		}
		if (state.locked()) return;
		if (state.age() < GROW_TICKS) {
			baby.setAttached(STATE, new State(state.age() + 1, false));
		} else if (level.getGameTime() % ROOM_CHECK_EVERY == 0 && roomToGrow(baby, level)) {
			BabyCreepers.growUp(baby);
		}
	}

	private static boolean roomToGrow(Creeper baby, ServerLevel level) {
		AABB grown = baby.getType().getDimensions().makeBoundingBox(baby.position());
		return level.noCollision(baby, grown);
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (!(level instanceof ServerLevel) || !(entity instanceof Creeper baby) || !BabyCreepers.is(baby)) return InteractionResult.PASS;
		ItemStack held = player.getItemInHand(hand);
		if (!AgeableMob.canUseGoldenDandelion(held, true, baby.getAttachedOrElse(LOCK_PARTICLES, 0), baby)) return InteractionResult.PASS;
		AgeableMob.setAgeLocked(baby, () -> state(baby).locked(), player, held, mob -> {
			State state = state(baby);
			baby.setAttached(STATE, new State(state.age(), !state.locked()));
			baby.setAttached(LOCK_PARTICLES, LOCK_PARTICLE_TICKS);
			if (!state.locked()) {
				// Calmed mid-hiss: it forgets who it was after and lets the fuse go out.
				baby.setTarget(null);
				if (!baby.isIgnited()) baby.setSwellDir(-1);
			}
		});
		return InteractionResult.SUCCESS;
	}
}

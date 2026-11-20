package justfatlard.baby_creepers;

import java.util.List;

import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import org.jspecify.annotations.Nullable;

/**
 * A baby creeper is the game's own creeper at half size, wearing a scoreboard tag: quicker on its
 * feet, half the health, half the blast. The scale attribute is part of the vanilla protocol, so
 * every client draws it small and its hitbox shrinks to match; nothing here is a new entity type.
 * A client with Pandorical draws it with {@link #MODEL} instead: a big head on a small body.
 * It grows up in time: see {@link Growth}.
 */
public final class BabyCreepers {
	private BabyCreepers() {}

	public static final String TAG = "baby_creeper";

	public static final float SCALE = 0.5F;
	/** Half again as fast: the baby zombie's {@code SPEED_MODIFIER_BABY}. */
	public static final double SPEED_BONUS = 0.5;
	/**
	 * Against a grown creeper's radius. Half the radius covers about a third of the ground, which is
	 * what keeps a baby's own spores from reliably sprouting another baby: see {@link Spores#INFECT_CHANCE}.
	 */
	public static final float BLAST = 0.5F;

	/**
	 * The creeper's own parts, texture and animation, moved and resized: a 6-pixel head on a 5-wide,
	 * 5-tall, 3-deep body, on legs 2.5 wide and 3 tall, 14 pixels from crown to sole. In
	 * {@code assets/baby-creepers-justfatlard/pandorical/entity_models/baby_creeper.json}.
	 */
	public static final Identifier MODEL = Identifier.fromNamespaceAndPath(Main.MOD_ID, "baby_creeper");

	private static final Identifier SIZE = Identifier.fromNamespaceAndPath(Main.MOD_ID, "baby_size");
	private static final Identifier SPEED = Identifier.fromNamespaceAndPath(Main.MOD_ID, "baby_speed");

	public static boolean is(@Nullable Entity entity) {
		return entity instanceof Creeper && entity.entityTags().contains(TAG);
	}

	/**
	 * A baby holding a golden dandelion is friendly: it picks no target, so it never hisses at anyone,
	 * even one who hits it. A second dandelion, or growing up, makes it a creeper again. Flint and
	 * steel still lights it; that is the player's own doing.
	 */
	public static boolean isPassive(@Nullable Entity entity) {
		return is(entity) && Growth.state((Creeper) entity).locked();
	}

	public static float blast(Creeper creeper, float radius) {
		return is(creeper) ? radius * BLAST : radius;
	}

	public static void become(Creeper creeper) {
		creeper.addTag(TAG);
		applySize(creeper);
		creeper.setHealth(creeper.getMaxHealth());
		Growth.born(creeper);
	}

	/**
	 * A grown creeper again: the tag, the size, the speed and the model go, and it keeps the share
	 * of its health it had, so a baby at full health is a grown one at full health.
	 */
	public static void growUp(Creeper creeper) {
		if (!(creeper.level() instanceof ServerLevel level)) return;
		float health = creeper.getHealth() / creeper.getMaxHealth();
		creeper.removeTag(TAG);
		creeper.removeAttached(Growth.STATE);
		for (var attribute : List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED)) {
			AttributeInstance instance = creeper.getAttribute(attribute);
			if (instance == null) continue;
			instance.removeModifier(SIZE);
			instance.removeModifier(SPEED);
		}
		creeper.setHealth(health * creeper.getMaxHealth());
		creeper.refreshDimensions();
		PandoricalApi.entityModels().clear(creeper);
		level.sendParticles(Spores.SPORE, creeper.getX(), creeper.getY() + 0.8, creeper.getZ(), 12, 0.3, 0.5, 0.3, 0.0);
	}

	/**
	 * Safe to repeat: each modifier has a fixed id, so saying it again replaces rather than stacks.
	 *
	 * <p>Pandorical keeps a model only while the entity is loaded, so it is set here, which every
	 * load passes through. The model is a baby's size already, so it is drawn at twice the scale
	 * attribute's half; the server keeps the half, for the hitbox and every other client.
	 */
	public static void applySize(Creeper creeper) {
		if (!is(creeper)) return;
		PandoricalApi.entityModels().set(creeper, MODEL, null, 1F / SCALE);
		permanent(creeper.getAttribute(Attributes.SCALE), SIZE, SCALE - 1F);
		permanent(creeper.getAttribute(Attributes.MAX_HEALTH), SIZE, SCALE - 1F);
		permanent(creeper.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, SPEED_BONUS);
		if (creeper.getHealth() > creeper.getMaxHealth()) creeper.setHealth(creeper.getMaxHealth());
		// Now rather than on its next tick, so a baby being placed is checked for room at its own size.
		creeper.refreshDimensions();
	}

	private static void permanent(@Nullable AttributeInstance attribute, Identifier id, double amount) {
		if (attribute == null) return;
		attribute.addOrReplacePermanentModifier(
			new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}
}

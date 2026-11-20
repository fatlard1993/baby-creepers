package justfatlard.baby_creepers;

import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.monster.Creeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creepers burst into spores instead of craters. See {@link Spores} for the burst, {@link Infection}
 * for the grass it leaves behind, and {@link BabyCreepers} for what that grass grows.
 */
public class Main implements ModInitializer {
	public static final String MOD_ID = "baby-creepers-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Eggs.register();
		Growth.register();
		// The baby model, shipped to Pandorical clients with the mod's other assets.
		PandoricalApi.content().registerModAssets(MOD_ID);

		// The tag is the truth and the attributes follow it, so a baby is sized again on every load.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Creeper creeper) BabyCreepers.applySize(creeper);
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> Infection.of(level).tick(level));

		LOGGER.info("[{}] Creepers spore, one grass in {} infected", MOD_ID, Math.round(1F / Spores.INFECT_CHANCE));
	}
}

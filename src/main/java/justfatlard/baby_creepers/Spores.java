package justfatlard.baby_creepers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** What a creeper leaves where its crater would have been: a fog of spores, and infected grass. */
public final class Spores {
	private Spores() {}

	/**
	 * Per block of grass the blast reaches. A grown creeper's blast reaches up to about fifty grass
	 * blocks and plants in a lush meadow, two or three babies at most, about one on bare grass; a baby's
	 * reaches about eighteen, so on average under one. Above about one in eighteen, babies would
	 * breed babies faster than they die, and a meadow would chain-react.
	 */
	public static final float INFECT_CHANCE = 0.05F;

	/** Which blocks spores take root in. A datapack can add to it. */
	public static final TagKey<Block> SPOREABLE =
		TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Main.MOD_ID, "sporeable"));

	/** A creeper's own green. */
	public static final int GREEN = 0x4FA83D;
	static final DustParticleOptions FOG = new DustParticleOptions(GREEN, 3.0F);
	static final DustParticleOptions SPORE = new DustParticleOptions(GREEN, 0.8F);

	private static final int CLOUD_TICKS = 120;

	public static void burst(ServerLevel level, Creeper creeper, float radius) {
		Vec3 at = creeper.position();
		level.sendParticles(FOG, at.x, at.y + 0.5, at.z, Math.round(30 * radius),
			radius / 2, radius / 3, radius / 2, 0.02);

		// The fog that lingers: an effect cloud carrying no effect, so it hurts nothing and only shows.
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		cloud.setCustomParticle(FOG);
		cloud.setRadius(radius);
		cloud.setWaitTime(0);
		cloud.setDuration(CLOUD_TICKS);
		cloud.setRadiusPerTick(-radius / CLOUD_TICKS);
		level.addFreshEntity(cloud);

		infect(level, at, radius);
	}

	private static void infect(ServerLevel level, Vec3 at, float radius) {
		Infection infection = Infection.of(level);
		int reach = (int) Math.ceil(radius);
		double reachSqr = radius * radius;
		BlockPos center = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
			if (pos.distToCenterSqr(at) > reachSqr) continue;
			BlockState state = level.getBlockState(pos);
			if (!state.is(SPOREABLE)) continue;
			// Tall grass is one plant in two blocks; only its root counts, so it is rolled once.
			if (state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) continue;
			if (level.getRandom().nextFloat() < INFECT_CHANCE) infection.infect(level, pos.immutable());
		}
	}
}

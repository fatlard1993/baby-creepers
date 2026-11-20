package justfatlard.baby_creepers;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Grass the spores have taken: where it is, and when it gives up a baby creeper.
 *
 * <p>Infected grass is still the vanilla block, so every client sees it; it only puffs spores while
 * it waits. Break, mow or till it before then and the infection goes with it. When it comes due a
 * plant is eaten away and a solid block is eaten down to dirt, and a {@link BabyCreepers baby}
 * stands where it was.
 *
 * <p>Each infection is one of two kinds, rolled as it takes: {@link Clock#QUICK quick}, hatching one
 * to three minutes later, or {@link Clock#WAIT_NIGHT dormant}, lying in the grass until the next
 * night. The night is read off the sky ({@code isDarkOutside}) rather than worked out from the time,
 * so sleeping through a night or {@code /time set} carries a dormant infection along with it.
 *
 * <p>Saved per level, because the wait outlasts a session. A dormant infection watches the sky even
 * in a chunk nobody is near; any infection there that comes due waits to hatch until somebody comes back.
 */
public final class Infection extends SavedData {
	private static final String STORAGE_KEY = "baby-creepers:infection";

	/** One to three minutes: long enough to see the spores and do something about them. */
	public static final int HATCH_MIN = 20 * 60;
	public static final int HATCH_SPREAD = 20 * 120;
	/** How many infections lie dormant until the next night, rather than hatching in minutes. */
	public static final float DORMANT_CHANCE = 0.5F;
	/** A dormant infection hatches somewhere in the first two minutes of dark, so a meadow does not all go at dusk. */
	public static final int NIGHT_SPREAD = 20 * 120;
	private static final int TICK_EVERY = 10;

	/** Where an infection is in its wait. */
	public enum Clock {
		/** Hatches at its time, day or night. */
		QUICK,
		/** Infected at night: this night does not count, so it waits for the day first. */
		WAIT_DAY,
		/** Waits for the sky to go dark. */
		WAIT_NIGHT,
		/** Dark has come: hatches at its time, if it is still dark then. */
		NIGHT;

		static final Codec<Clock> CODEC = Codec.STRING.xmap(Clock::valueOf, Clock::name);
	}

	private record Entry(long hatchAt, Clock clock) {}

	private record Saved(long pos, long hatchAt, Clock clock) {
		static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf("pos").forGetter(Saved::pos),
			Codec.LONG.fieldOf("hatch_at").forGetter(Saved::hatchAt),
			Clock.CODEC.optionalFieldOf("clock", Clock.QUICK).forGetter(Saved::clock)
		).apply(instance, Saved::new));
	}

	public static final Codec<Infection> CODEC = Saved.CODEC.listOf()
		.xmap(Infection::fromSaved, Infection::toSaved);

	private static final SavedDataType<Infection> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), Infection::new, CODEC, DataFixTypes.LEVEL);

	private final Long2ObjectOpenHashMap<Entry> infected = new Long2ObjectOpenHashMap<>();

	public static Infection of(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	private static Infection fromSaved(List<Saved> saved) {
		Infection infection = new Infection();
		for (Saved entry : saved) infection.infected.put(entry.pos(), new Entry(entry.hatchAt(), entry.clock()));
		return infection;
	}

	private List<Saved> toSaved() {
		return infected.long2ObjectEntrySet().stream()
			.map(e -> new Saved(e.getLongKey(), e.getValue().hatchAt(), e.getValue().clock())).toList();
	}

	public boolean isInfected(BlockPos pos) {
		return infected.containsKey(pos.asLong());
	}

	/** Where this infection is in its wait, or null if the block is not infected. */
	public @Nullable Clock clock(BlockPos pos) {
		Entry entry = infected.get(pos.asLong());
		return entry == null ? null : entry.clock();
	}

	/** Rolls quick or dormant. Infected already, it keeps the wait it has: a second blast does not hurry it. */
	public void infect(ServerLevel level, BlockPos pos) {
		infect(level, pos, level.getRandom().nextFloat() < DORMANT_CHANCE);
	}

	public void infect(ServerLevel level, BlockPos pos, boolean dormant) {
		Entry entry = dormant
			? new Entry(0, level.isDarkOutside() ? Clock.WAIT_DAY : Clock.WAIT_NIGHT)
			: new Entry(level.getGameTime() + HATCH_MIN + level.getRandom().nextInt(HATCH_SPREAD), Clock.QUICK);
		if (infected.putIfAbsent(pos.asLong(), entry) == null) setDirty();
	}

	public void tick(ServerLevel level) {
		if (infected.isEmpty() || level.getGameTime() % TICK_EVERY != 0) return;
		long now = level.getGameTime();
		boolean dark = level.isDarkOutside();
		ObjectIterator<Long2ObjectMap.Entry<Entry>> entries = infected.long2ObjectEntrySet().fastIterator();
		while (entries.hasNext()) {
			Long2ObjectMap.Entry<Entry> slot = entries.next();
			// The sky is the level's, not the chunk's, so the wait moves on whether or not anybody is near.
			Entry entry = watchSky(slot.getValue(), dark, now, level);
			if (entry != slot.getValue()) {
				slot.setValue(entry);
				setDirty();
			}

			BlockPos pos = BlockPos.of(slot.getLongKey());
			if (!level.isPositionEntityTicking(pos)) continue;
			BlockState state = level.getBlockState(pos);
			if (!state.is(Spores.SPOREABLE)) {
				entries.remove();
				setDirty();
			} else if (due(entry, now)) {
				entries.remove();
				setDirty();
				hatch(level, pos, state);
			} else {
				puff(level, pos, state);
			}
		}
	}

	private static Entry watchSky(Entry entry, boolean dark, long now, ServerLevel level) {
		return switch (entry.clock()) {
			case QUICK -> entry;
			case WAIT_DAY -> dark ? entry : new Entry(0, Clock.WAIT_NIGHT);
			case WAIT_NIGHT -> dark ? new Entry(now + level.getRandom().nextInt(NIGHT_SPREAD), Clock.NIGHT) : entry;
			// Day came back before it hatched, as it does when everybody sleeps: the next night, then.
			case NIGHT -> dark ? entry : new Entry(0, Clock.WAIT_NIGHT);
		};
	}

	private static boolean due(Entry entry, long now) {
		return (entry.clock() == Clock.QUICK || entry.clock() == Clock.NIGHT) && now >= entry.hatchAt();
	}

	private static boolean solid(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.getCollisionShape(level, pos).isEmpty();
	}

	private static void puff(ServerLevel level, BlockPos pos, BlockState state) {
		double top = solid(level, pos, state) ? 1.05 : 0.4;
		level.sendParticles(Spores.SPORE, pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5, 1, 0.3, 0.1, 0.3, 0.0);
	}

	/** On peaceful the spores die where they are, and the grass is left as it was. */
	private static void hatch(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) return;
		boolean solid = solid(level, pos, state);
		BlockPos stand = solid ? pos.above() : pos;

		Creeper baby = EntityTypes.CREEPER.create(level, EntitySpawnReason.EVENT);
		if (baby == null) return;
		baby.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
		baby.finalizeSpawn(level, level.getCurrentDifficultyAt(stand), EntitySpawnReason.EVENT, null);
		BabyCreepers.become(baby);
		// Nowhere to stand: the spores are spent and the grass survives.
		if (!level.noCollision(baby)) {
			baby.discard();
			return;
		}

		if (solid) {
			level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(state));
			level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
		} else {
			level.destroyBlock(pos, false);
		}
		level.addFreshEntity(baby);
		level.sendParticles(Spores.FOG, stand.getX() + 0.5, stand.getY() + 0.3, stand.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.0);
	}
}

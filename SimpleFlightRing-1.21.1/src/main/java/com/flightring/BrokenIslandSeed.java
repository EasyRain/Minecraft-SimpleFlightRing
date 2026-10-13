package com.flightring;

/**
 * The world seed bridge for the island biome source.
 * <p>
 * Modern {@code BiomeSource} instances are built from a codec and have no access to the world seed
 * ({@link net.minecraft.world.level.biome.BiomeSource#getNoiseBiome} only gets quart coordinates and
 * the climate sampler), yet the island layout is a function of the seed. The chunk generator does get
 * it - {@code RandomState#getLevelSeed()} in {@code fillFromNoise} / {@code createBiomes},
 * {@code ChunkGeneratorStructureState#getLevelSeed()} in {@code createStructures} - so it publishes
 * the seed here once the level starts generating, and the biome source reads it.
 * <p>
 * A plain static is enough: one running game owns exactly one world seed (a single-player session has
 * one loaded world at a time, a server has one world). The value is only read while chunks are being
 * generated, which always happens after the generator has seen the seed.
 */
public final class BrokenIslandSeed {

    private static volatile long seed;

    private BrokenIslandSeed() {
    }

    public static long get() {
        return seed;
    }

    public static void set(long value) {
        seed = value;
    }
}

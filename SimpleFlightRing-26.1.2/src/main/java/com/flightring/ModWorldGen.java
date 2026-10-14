package com.flightring;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Worldgen registration for the broken dimension (M2): the island chunk generator, its biome source and
 * the mod's own snow feature. All three are codecs in worldgen registries, so the dimension JSON and
 * the biome modifier JSONs can name them ({@code "type": "simpleflightring:island"},
 * {@code "type": "simpleflightring:broken_snow"}).
 */
public final class ModWorldGen {

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, FlightRingMod.MODID);

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, FlightRingMod.MODID);

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, FlightRingMod.MODID);

    /** {@code simpleflightring:island} biome source: one biome per island, void in between. */
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<? extends BiomeSource>>
            ISLAND_BIOME_SOURCE = BIOME_SOURCES.register("island", () -> BrokenIslandBiomeSource.CODEC);

    /** {@code simpleflightring:island} chunk generator: floating islands, nothing else. */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<? extends ChunkGenerator>>
            ISLAND_GENERATOR = CHUNK_GENERATORS.register("island", () -> BrokenIslandChunkGenerator.CODEC);

    /**
     * {@code simpleflightring:broken_snow} - our replacement for vanilla's {@code freeze_top_layer},
     * which cannot work here any more (see {@link BrokenSnowFeature}).
     */
    public static final DeferredHolder<Feature<?>, BrokenSnowFeature> BROKEN_SNOW =
            FEATURES.register("broken_snow", () -> new BrokenSnowFeature(NoneFeatureConfiguration.CODEC));

    private ModWorldGen() {
    }
}

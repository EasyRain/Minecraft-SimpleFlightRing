package com.flightring;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * The biome layer of the broken dimension: every island is one single biome, picked from
 * {@link BrokenBiomePool} by the island's stable index, and everything between the islands is void.
 * <p>
 * The codec carries no fields: the pool and the seed are per-server values that
 * {@link BrokenBiomePool} / {@link BrokenIslandSeed} publish when the server starts, which always
 * happens before any chunk is generated (this class is only ever asked for biomes while generating).
 * {@link #collectPossibleBiomes()} reports the real pool, which matters because the vanilla pipeline
 * uses that list to budget features and structures - a biome missing from it never runs its features.
 */
public class BrokenIslandBiomeSource extends BiomeSource {

    /**
     * The registry is pulled in through the codec ({@code RegistryOps.retrieveRegistryLookup}), which
     * hands it over fully loaded at datapack load time. That matters: this object is built while the
     * dimension JSON is read, i.e. long before any server start hook could fill a static, and an
     * earlier version that did rely on a static crashed with
     * {@code NullPointerException: Cannot invoke "Holder.value()" because ... is null} as soon as a
     * chunk was generated.
     */
    public static final MapCodec<BrokenIslandBiomeSource> CODEC =
            RegistryOps.retrieveRegistryLookup(Registries.BIOME)
                    .xmap(BrokenIslandBiomeSource::new, source -> source.lookup);

    /**
     * How far past the rim a quad still counts as island biome - just enough to cover the 4x4
     * quantisation, no more. The biome is deliberately <b>not</b> larger than the island: everything
     * outside stays {@code minecraft:the_void}, and that is the general defence against features that
     * build in empty space (a feature can only run where its biome is). The void has no features at all,
     * so nothing can appear next to an island - which is exactly how the flat-void start platform, the
     * frozen oceans' icebergs and any mod feature with the same habit were stopped for good. The price is
     * that vanilla's {@code freeze_top_layer} - whose bare {@code BiomeFilter} is evaluated once per
     * chunk at the chunk <i>origin</i>, which may well be void - can no longer be relied upon, so the mod
     * brings its own {@link BrokenSnowFeature} instead.
     */
    public static final double QUAD_MARGIN = 4.0;

    /**
     * How far past the rim a <b>chunk</b> still counts as containing an island. Used by the generator to
     * decide whether its surface pass has anything to do at all; it only has to be a safe over-estimate,
     * because a chunk without an island is simply skipped.
     */
    public static final double CHUNK_MARGIN = 32.0;

    private final HolderLookup.RegistryLookup<Biome> lookup;
    private final Holder<Biome> voidBiome;

    public BrokenIslandBiomeSource(HolderLookup.RegistryLookup<Biome> lookup) {
        this.lookup = lookup;
        this.voidBiome = lookup.getOrThrow(Biomes.THE_VOID);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        // Read the pool *now* rather than copying it in the constructor: this object is built while the
        // dimension JSON is parsed, which happens before NeoForge has applied its biome modifiers - at
        // that moment a modded biome's feature list is still empty, which is how an inconsistent feature
        // order slipped through and crashed chunk generation later on. The pool is rebuilt at
        // ServerAboutToStartEvent (see BrokenBiomePool), so read whatever is current.
        List<Holder<Biome>> pool = BrokenBiomePool.cachedPool();
        if (pool.isEmpty()) {
            // Never hand out an empty pool: build it from the registry this source already holds. That
            // may be the pre-biome-modifier version, but the rebuild replaces it and possibleBiomes()
            // below is live, so the generator always ends up with the final list.
            pool = BrokenBiomePool.poolFrom(lookup);
        }
        return voidBiome == null ? pool.stream() : Stream.concat(Stream.of(voidBiome), pool.stream());
    }

    /**
     * Live view of {@link #collectPossibleBiomes()}.
     * <p>
     * {@code BiomeSource} memoizes that method on its <b>first</b> call, which is a trap in a large mod
     * pack: something can ask for the biomes before the pool exists (the pool is only rebuilt at
     * ServerAboutToStartEvent, i.e. after biome modifiers). The memo then holds the empty pool, and since
     * the vanilla decoration pass starts with
     * {@code set.retainAll(this.biomeSource.possibleBiomes())} - the biomes present in the chunk filtered
     * by the biomes the generator knows - <b>every</b> biome gets filtered out and not a single feature
     * runs, while terrain, biomes and materials look perfectly fine. That is exactly the "islands are
     * correct but there is no vegetation anywhere" report of 2026-10-11, so this override deliberately
     * bypasses the memo.
     */
    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        return collectPossibleBiomes().collect(Collectors.toSet());
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        List<Holder<Biome>> pool = BrokenBiomePool.cachedPool();
        if (pool.isEmpty() || voidBiome == null) {
            return voidBiome;
        }
        // One answer covers 4x4 blocks, so ask about the quad's centre with only the slack the
        // quantisation needs (QUAD_MARGIN). Everything past the island is the void biome on purpose -
        // see QUAD_MARGIN for why that is the general fix for features that build in empty space.
        int blockX = (quartX << 2) + 2;
        int blockZ = (quartZ << 2) + 2;
        BrokenIslandLayout.Island island = BrokenIslandLayout.islandCovering(
                BrokenIslandSeed.get(), blockX, blockZ, QUAD_MARGIN);
        if (island == null) {
            return voidBiome;
        }
        return pool.get(Math.floorMod(island.biomeIndex(), pool.size()));
    }
}

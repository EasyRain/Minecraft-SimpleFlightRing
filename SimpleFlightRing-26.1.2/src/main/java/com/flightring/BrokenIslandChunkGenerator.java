package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The terrain of the broken dimension: floating islands, nothing else (M2).
 * <p>
 * <b>We only plan the shape; vanilla decides the blocks.</b> Every column asks
 * {@link BrokenIslandLayout} whether it belongs to an island and, if so, fills a deterministic lens.
 * What that lens is made of is not our business any more (user 2026-10-09): the filler is the
 * {@code defaultBlock} of the vanilla noise settings that match the island's dimension family (stone,
 * netherrack, end stone) and the actual surface - grass, sand, snow, gravel, warped and crimson
 * <b>nylium</b>, whatever a mod biome wants - comes from that settings' own {@code surface_rule},
 * applied by vanilla's {@link SurfaceSystem} onto our terrain. That is why a warped forest island now
 * grows warped nylium (and therefore its fungi), and why a mod biome needs no code here at all.
 * <p>
 * A biome that is all water (oceans, rivers - see {@link BrokenBiomePool#isLiquid}) becomes a
 * <b>bowl</b>: the middle of the island is hollowed to a basin whose rim stays solid, so its water is
 * held in instead of pouring into the void. Water itself is ours, because vanilla's aquifers would
 * spill it into the void.
 * <p>
 * The generator never uses runtime randomness: the same seed produces the same islands, and the same
 * column always produces the same blocks, which is what {@link #getBaseColumn} and
 * {@link #getBaseHeight} rely on (structures will query them later).
 */
public class BrokenIslandChunkGenerator extends ChunkGenerator {

    /**
     * Vanilla's usual shape: the biome source is a field of the generator, which is why the dimension
     * JSON names both ({@code "type": "simpleflightring:island", "biome_source": {...}}). The noise
     * settings registry is pulled in through the codec ({@code retrieveRegistryLookup}), exactly like
     * the biome source does, so that the dimension JSON needs no extra fields.
     */
    public static final MapCodec<BrokenIslandChunkGenerator> CODEC =
            RecordCodecBuilder.<BrokenIslandChunkGenerator>mapCodec(
                    instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                            RegistryOps.<NoiseGeneratorSettings>retrieveRegistryLookup(Registries.NOISE_SETTINGS)
                                    .forGetter(BrokenIslandChunkGenerator::noiseSettings),
                            // A RecordCodecBuilder element, not a MapCodec one, so it must not get a
                            // forGetter - that is how vanilla does it too
                            // (MultiNoiseBiomeSourceParameterList:28).
                            RegistryOps.<NormalNoise.NoiseParameters, BrokenIslandChunkGenerator>retrieveGetter(Registries.NOISE)
                    ).apply(instance, BrokenIslandChunkGenerator::new));

    /** The dimension is 384 tall starting at y = -64 (see dimension_type/the_broken.json). */
    public static final int MIN_Y = -64;
    public static final int GEN_DEPTH = 384;
    private static final int SEA_LEVEL = 63;

    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

    /**
     * Which vanilla-side noise settings an island borrows its rock and its surface rule from.
     * <p>
     * The three vanilla presets cover the overworld, the nether and the end. A biome from another mod's
     * dimension gets <b>that mod's own settings</b> when they can be identified (see
     * {@link #familyOf(BrokenIslandLayout.Island)}), because otherwise it would be dressed with the
     * overworld's grass - and a crimson forest or "the other" biome whose features expect crimson nylium,
     * soul sand or ancient grass finds no valid ground and places nothing at all (user 2026-10-11).
     */
    private record Family(net.minecraft.resources.ResourceKey<NoiseGeneratorSettings> preset, String label) {
        private static final Family OVERWORLD = new Family(NoiseGeneratorSettings.OVERWORLD, "overworld");
        private static final Family NETHER = new Family(NoiseGeneratorSettings.NETHER, "nether");
        private static final Family END = new Family(NoiseGeneratorSettings.END, "end");
    }

    /**
     * Everything vanilla needs to dress one island family: the settings, the {@link RandomState} they
     * are wrapped in, and the fluid picker its noise chunk wants. Built once per family and seed (per
     * level), because a {@code RandomState} carries the noise instances.
     */
    /**
     * The block an island's interior is made of.
     * <p>
     * Vanilla's overworld turns stone into deepslate below y = 0. Islands are thick enough to reach down
     * there now (user 2026-10-11: "有些岛生成到Y0以下还能生成深板岩地形"), so the deep rock is placed right
     * away instead of relying on the surface rule, which only rewrites the blocks it happens to walk past
     * (a structure carved into the island would otherwise cut through plain stone).
     */
    private static BlockState fillerAt(Family family, NoiseGeneratorSettings settings, int y) {
        if (y < 0 && NoiseGeneratorSettings.OVERWORLD.equals(family.preset())) {
            return DEEPSLATE;
        }
        return settings.defaultBlock();
    }

    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();

    /**
     * Places a block only where there is air - the sealing pass runs after decoration as well, and it must
     * not overwrite what a feature put into the water (seagrass, kelp, coral).
     */
    private static void fillIfAir(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int y, int z,
                                  BlockState state,
                                  List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes) {
        if (insideAny(boxes, x, y, z)) {
            return;
        }
        if (chunk.getBlockState(pos.set(x, y, z)).isAir()) {
            chunk.setBlockState(pos, state);
        }
    }

    /**
     * The bounding boxes of every structure piece anywhere near this chunk.
     * <p>
     * The air repair exists to close accidental gaps in an island - it must not fill in the <b>inside</b> of
     * a structure, or a meteorite's shaft, a mineshaft corridor, a stronghold room and an ancient city all
     * come out solid stone (user 2026-10-11: "结构的地下部分的空气全被石头填实了，比如这个ae的陨石" and
     * then "...末地要塞大部分依旧被填了，比如这个传送门房和书架房").
     * <p>
     * {@code chunk.getAllStarts()} is <b>not</b> enough on its own: it only holds the starts that originate
     * in this chunk, while a stronghold's rooms are dozens of chunks away from its start. So the whole
     * neighbourhood is asked for its starts, which is exactly what {@code ChunkGenerator#createReferences}
     * does when it links pieces to every chunk they overlap.
     */
    private static List<net.minecraft.world.level.levelgen.structure.BoundingBox> structureBoxes(
            net.minecraft.world.level.LevelAccessor level, ChunkAccess chunk) {
        List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes = new ArrayList<>();
        ChunkPos center = chunk.getPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                // 26.x turned ChunkPos into a record, so its components read through x() / z().
                int cx = center.x() + dx;
                int cz = center.z() + dz;
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (net.minecraft.world.level.levelgen.structure.StructureStart start
                        : level.getChunk(cx, cz).getAllStarts().values()) {
                    if (!start.isValid()) {
                        continue;
                    }
                    for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                        boxes.add(piece.getBoundingBox());
                    }
                }
            }
        }
        return boxes;
    }

    /** True when the position is inside one of the given boxes. */
    private static boolean insideAny(List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes,
                                     int x, int y, int z) {
        for (net.minecraft.world.level.levelgen.structure.BoundingBox box : boxes) {
            if (box.isInside(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only the island's outer band is repaired, everything from this elliptical distance outwards.
     * <p>
     * A hole in the middle of an island is harmless - an amethyst geode's hollow, a dripstone cave and a
     * structure room all live there, and filling them looks like the island terrain was stacked on top
     * (user 2026-10-11: "为什么有些晶洞会被填上，感觉像是被岛的地形又叠上去了"). What does matter is the rim:
     * a gap there lets the void show through the island's edge, and the rim is also the shell that keeps a
     * bowl island's water in. So the repair covers the outer band only, at full depth.
     */
    static double REPAIR_RIM_FROM = 0.8;

    private record Bundle(long seed, RandomState state, NoiseGeneratorSettings settings,
                          Aquifer.FluidPicker fluidPicker) {
    }

    private final HolderLookup.RegistryLookup<NoiseGeneratorSettings> noiseSettings;
    private final HolderGetter<NormalNoise.NoiseParameters> noiseParameters;

    private final Map<Family, Bundle> bundles = new ConcurrentHashMap<>();

    /**
     * One in this many islands is carved with vanilla's water table, so its caves fill with water; every
     * other island is carved dry (see carverSettings and {@link #dryOut}). Picking per island instead of
     * globally is the user's idea (2026-10-11): "其实可以不用动海平面，而是按岛屿来，比如大部分岛屿都是干洞，
     * 只有少部分才有含水洞" - and it makes a flooded island a feature instead of a bug.
     * <p>
     * Lowered from 5 (20%) to 10 (10%) on the user's request the same day: "含水的概率还可以再低点，比如
     * 10%，因为还有液体岛会占一部分" - the bowl islands of ocean and river biomes hold water of their own, so
     * the wet islands get added on top of those.
     */
    static int WET_ISLAND_CHANCE = 10;

    /** One vanilla generator per (family, water table), used only to run that family's carvers. */
    private final Map<CarverKey, net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator> carvers =
            new ConcurrentHashMap<>();

    /** Which delegate to use: the rock family and whether this island is a wet one. */
    private record CarverKey(Family family, boolean wet) {
    }

    /** How deep a bowl's water is, and how wide its solid rim is. */
    private static final int BOWL_DEPTH_MIN = 6;
    private static final int BOWL_DEPTH_MAX = 16;
    /**
     * Least solid rock between a bowl's basin floor and the island's own bottom (see the basin clamp), and
     * the thickness of the shell {@link #sealBowls} puts back under a basin floor after carving - a cave
     * opening under water looks wrong (user 2026-10-11: "还有水中心有矿洞也很奇怪"), so the floor is made
     * thick enough that no cave can reach the water at all.
     */
    /**
     * Least solid rock between a bowl's basin floor and the island's own bottom, used when the basin is cut:
     * it keeps a deep basin from cutting through its own floor. It is <b>not</b> used to refill anything after
     * carving - the bowl rim is what holds the water in, and the floor is deliberately left to the carvers.
     */
    static int BOWL_SHELL_MIN = 16;
    private static final int BOWL_RIM = 3;
    private static final int BOWL_LIP = 2;
    // Note: blocks that decoration places *outside* the islands (the frozen ocean's icebergs, a water
    // spring, and whatever mod features do) are deliberately left alone. Clearing them was tried and
    // reverted: it would also delete mod structures that legitimately extend beyond an island, and
    // floating ice is the smaller problem (user decision 2026-10-09).

    public BrokenIslandChunkGenerator(BiomeSource biomeSource,
                                     HolderLookup.RegistryLookup<NoiseGeneratorSettings> noiseSettings,
                                     HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        // The second argument is how a generator adjusts a biome's features - the flat generator uses
        // the same hook (FlatLevelGeneratorSettings#adjustGenerationSettings). We only use it to drop
        // the flat-void start platform, see islandSettings.
        super(biomeSource, BrokenIslandChunkGenerator::islandSettings);
        this.noiseSettings = noiseSettings;
        this.noiseParameters = noiseParameters;
    }

    private HolderLookup.RegistryLookup<NoiseGeneratorSettings> noiseSettings() {
        return this.noiseSettings;
    }

    private HolderGetter<NormalNoise.NoiseParameters> noiseParameters() {
        return this.noiseParameters;
    }

    /**
     * Placed features that must never run in this dimension, because they assume the world they were
     * written for and do damage on a floating island:
     * <ul>
     *   <li>{@code minecraft:void_start_platform} - the flat-void start platform (a placed feature of
     *       the plains biome, {@code OverworldBiomes:748}). In a normal world its surroundings check
     *       fails and nobody sees it; here everything <i>is</i> void, so it built that lone stone slab
     *       with a cobblestone ring next to every plains island.</li>
     *   <li>{@code minecraft:iceberg_packed} / {@code minecraft:iceberg_blue} - the frozen oceans'
     *       icebergs ({@code BiomeDefaultFeatures:370}, rarity 1/16 and 1/200 per chunk). They are
     *       designed to grow a huge body <b>below a water surface</b>, so on a shallow bowl island they
     *       replaced the island's own rock and hung columns of ice and packed ice down into the void
     *       (user 2026-10-10: "deep frozen ocean 底下生成了大片的冰柱").</li>
     * </ul>
     * {@code minecraft:freeze_top_layer} used to be the one vanilla feature we <i>wanted</i>, but it has
     * to go as well now: its placed feature carries only a {@code BiomeFilter}, evaluated once per chunk
     * at the chunk <i>origin</i>, and since the island biome no longer overhangs the island (see
     * {@link BrokenIslandBiomeSource#QUAD_MARGIN}) that origin is often void - so those chunks lost their
     * snow completely. {@link BrokenSnowFeature} does the same job from our side, at the same generation
     * step, without caring where the origin is.
     * <p>
     * The list is deliberately short, because the general protection is elsewhere: the biome <b>is</b> the
     * island (nothing can act in the void) plus the air repair in {@link #applyBiomeDecoration}. A feature
     * only belongs here when it does damage <i>inside</i> an island and cannot be reasoned away.
     */
    private static final Set<Identifier> DROPPED_FEATURES = Set.of(
            Identifier.withDefaultNamespace("void_start_platform"),
            Identifier.withDefaultNamespace("iceberg_packed"),
            Identifier.withDefaultNamespace("iceberg_blue"),
            Identifier.withDefaultNamespace("freeze_top_layer"));

    /**
     * The biome's own generation settings, minus the features listed in {@link #DROPPED_FEATURES}.
     * Placed features are the reason a dimension must not blindly inherit a biome's list: they were
     * written for the world they belong to, not for a floating island.
     */
    static BiomeGenerationSettings islandSettings(Holder<Biome> biome) {
        // A mod biome whose feature order disagreed with the pool's: same features, canonical order.
        BiomeGenerationSettings reordered = BrokenBiomePool.reordered(biome);
        if (reordered != null) {
            return reordered;
        }
        if (BrokenBiomePool.isFeatureStripped(biome)) {
            // Last resort only (a mod biome that even contradicts itself): its features are dropped so the
            // bulk of the pool survives; the biome, its surface and its identity stay.
            return new BiomeGenerationSettings.PlainBuilder().build();
        }
        BiomeGenerationSettings original = biome.value().getGenerationSettings();
        BiomeGenerationSettings.PlainBuilder builder = new BiomeGenerationSettings.PlainBuilder();
        List<HolderSet<PlacedFeature>> steps = original.features();
        for (int step = 0; step < steps.size(); step++) {
            for (Holder<PlacedFeature> feature : steps.get(step)) {
                if (isDropped(feature)) {
                    continue;
                }
                builder.addFeature(step, feature);
            }
        }
        return builder.build();
    }

    private static boolean isDropped(Holder<PlacedFeature> feature) {
        return feature.unwrapKey().map(key -> DROPPED_FEATURES.contains(key.identifier())).orElse(false);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * The generator is the only one that ever sees the world seed ({@code RandomState} does not expose
     * it in 1.21.1), and this hook is called once per level before any chunk is generated - so this is
     * where the biome source gets it, through {@link BrokenIslandSeed}.
     */
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState randomState,
                                                    long seed) {
        BrokenIslandSeed.set(seed);
        return super.createState(structures, randomState, seed);
    }

    // Structures are placed by createStructures further down, which keeps only the ones an island can hold
    // (the switch-off above that used to live here was the M1 placeholder for exactly this).

    // ------------------------------------------------------------------ terrain

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        long seed = BrokenIslandSeed.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;
                BrokenIslandLayout.Island island = BrokenIslandLayout.islandFor(seed, worldX, worldZ);
                int[] profile = column(seed, island, worldX, worldZ);
                if (profile == null) {
                    continue;
                }
                int solidBottom = Math.max(profile[0], minY);
                int solidTop = profile[1];
                int waterTop = profile[2];
                // The rock is the one vanilla would put in that dimension: stone, netherrack or end
                // stone. The surface on top of it is vanilla's job too, in buildSurface below.
                Family family = familyOf(island);
                NoiseGeneratorSettings islandSettings = bundleFor(family).settings();
                for (int y = solidBottom; y <= solidTop; y++) {
                    set(chunk, pos.set(worldX, y, worldZ), fillerAt(family, islandSettings, y));
                }
                // Water is the one thing we keep for ourselves: vanilla's aquifers would pour it into
                // the void, and a bowl island is exactly the case they cannot handle.
                for (int y = solidTop + 1; y <= waterTop; y++) {
                    set(chunk, pos.set(worldX, y, worldZ), WATER);
                }
                // Heightmaps come first, and they are built from the *terrain's* top - never from a
                // cosmetic snow layer. Vanilla adds snow at TOP_LAYER_MODIFICATION, i.e. after
                // vegetation, so its trees are placed on grass and then buried under the snow; if the
                // layer counted towards MOTION_BLOCKING every sapling would be placed on top of the
                // snow and fail its survival check (that is why the snowy taiga island had no trees).
                int terrainTop = Math.max(solidTop, waterTop);
                BlockState terrainTopState = chunk.getBlockState(pos.set(worldX, terrainTop, worldZ));
                for (Heightmap.Types type : Heightmap.Types.values()) {
                    chunk.getOrCreateHeightmapUnprimed(type).update(worldX & 15, terrainTop, worldZ & 15, terrainTopState);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private static void set(ChunkAccess chunk, BlockPos pos, BlockState state) {
        // 26.x replaced the old `boolean isMoving` with `@Block.UpdateFlags int flags`; the two-argument
        // overload is that same call with UPDATE_ALL, i.e. movedByPiston == false, which is exactly what the
        // 1.21.1 code's `false` meant (it only ever reached BlockState#onPlace/onRemove as isMoving).
        chunk.setBlockState(pos, state);
    }

    /**
     * Vanilla's surface pass, applied to our terrain: {@link SurfaceSystem} reads the blocks of the
     * chunk itself ({@code SurfaceSystem:82}) and Consults the island's biome per column through the
     * biome manager, so the family's surface rule can do its work. This is what turns a warped forest
     * island's top into warped nylium (and thereby lets its fungi grow), a desert island's into sand
     * and a snowy island's into grass plus snow.
     */
    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
                             ChunkAccess chunk) {
        BrokenIslandLayout.Island island = islandCoveringChunk(BrokenIslandSeed.get(), chunk);
        if (island == null) {
            // No island in this chunk, so there is nothing to dress (the biome may still be an island
            // biome, because it deliberately overhangs - see BrokenIslandBiomeSource).
            return;
        }
        Bundle bundle = bundleFor(familyOf(island));
        // The surface pass gets the same dry settings as the carvers: it builds its own noise chunk (and
        // therefore its own aquifer), and with vanilla's settings that aquifer believes the island sits
        // under an ocean, which is where the water at y <= 62 on otherwise dry islands came from
        // (probe sweep, 2026-10-11: snowy_beach/bamboo_jungle/savanna all had water up to sea level).
        Bundle dryBundle = new Bundle(bundle.seed(), bundle.state(), carverSettings(bundle.settings()),
                bundle.fluidPicker());
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(
                c -> createNoiseChunk(c, structureManager, Blender.of(region), dryBundle));
        bundle.state().surfaceSystem().buildSurface(
                bundle.state(),
                region.getBiomeManager(),
                region.registryAccess().lookupOrThrow(Registries.BIOME),
                bundle.settings().useLegacyRandomSource(),
                new WorldGenerationContext(this, region),
                chunk,
                noiseChunk,
                bundle.settings().surfaceRule());
        // Last word on a bowl island. Carving - and the surface pass itself, which can turn a carved-out
        // column into a hole - may leave a gap where water should be (user 2026-10-11: "矿洞生成到了水
        // 中心，把水给雕成矿洞了"). Sealing here means every terrain step has already run; decoration still
        // comes afterwards, so the water is in place for seagrass, kelp and the like.
        sealBowls(region, chunk);
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager structureManager, Blender blender,
                                        Bundle bundle) {
        return NoiseChunk.forChunk(
                chunk,
                bundle.state(),
                Beardifier.forStructuresInChunk(structureManager, chunk.getPos()),
                bundle.settings(),
                bundle.fluidPicker(),
                blender);
    }

    /**
     * General damage control, run after every decoration step: <b>the body of an island must not contain
     * air</b>. Whatever wrote a hole into it (the vanilla nether surface rule's {@code hole()} branch, a
     * modded carver, a structure at the rim) is filled back with the family's own rock.
     * <p>
     * Nothing else is touched on purpose: ores, the surface rule's sand/terracotta/nylium, trees, and
     * blocks a mod feature put down next to - or below - an island all stay as they are.
     * <p>
     * <b>A "nothing below an island" rule was tried and removed again.</b> It cleared the 48 blocks under
     * the island, which left the deeper part of the frozen oceans' ice spikes in place and punched a
     * disc-shaped gap between island and ice (user 2026-10-11: "上面的部分就像有个圆给消掉了一部分").
     * Besides being uglier than the spikes themselves, that rule contradicted the earlier decision to
     * simply keep them as a feature of this dimension.
     */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        long seed = BrokenIslandSeed.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinY();
        // Structures own their own air (see structureBoxes): their interiors are not holes to repair.
        List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes = structureBoxes(level, chunk);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;
                BrokenIslandLayout.Island island = BrokenIslandLayout.islandFor(seed, worldX, worldZ);
                int[] profile = column(seed, island, worldX, worldZ);
                if (profile == null) {
                    continue;
                }
                Family family = familyOf(island);
                NoiseGeneratorSettings islandSettings = bundleFor(family).settings();
                // A cave is legitimately empty. Carving runs before decoration, so the air repair has to
                // leave whatever a carver removed alone - otherwise it would pour the caves full of rock
                // again the moment they were dug (user 2026-10-11: islands are thick enough for caves now).
                // 26.x: GenerationStep.Carving is gone (GenerationStep holds only Decoration now) and a
                // ProtoChunk carries a single CarvingMask instead of one per step - air and liquid carve into
                // the same mask, so one lookup answers both.
                net.minecraft.world.level.chunk.CarvingMask mask =
                        ((net.minecraft.world.level.chunk.ProtoChunk) chunk).getCarvingMask();
                // Rim only (see REPAIR_RIM_FROM): the interior is left alone so geodes, caves and structure
                // rooms keep their air - both are skipped below as well.
                if (island.normalizedDistance(worldX, worldZ) < REPAIR_RIM_FROM) {
                    continue;
                }
                for (int y = Math.max(profile[0], minY); y < profile[1]; y++) {
                    if (mask != null && mask.get(x, y, z)) {
                        continue;
                    }
                    if (insideAny(boxes, worldX, y, worldZ)) {
                        continue;
                    }
                    if (chunk.getBlockState(pos.set(worldX, y, worldZ)).isAir()) {
                        chunk.setBlockState(pos, fillerAt(family, islandSettings, y));
                    }
                }
            }
        }
        // Again after decoration: a feature is allowed to dig, but the water of a bowl island must survive
        // it (see sealBowls - it only fills air, so plants in the water are untouched).
        sealBowls(level, chunk);
    }

    /** The island that reaches into this chunk, if any (at most one - the layout keeps them 256 apart). */
    private static BrokenIslandLayout.Island islandCoveringChunk(long seed, ChunkAccess chunk) {
        return BrokenIslandLayout.islandCovering(
                seed,
                chunk.getPos().getMinBlockX() + 8,
                chunk.getPos().getMinBlockZ() + 8,
                BrokenIslandBiomeSource.CHUNK_MARGIN);
    }

    // ------------------------------------------------------------------ vanilla settings per family

    /**
     * The bundle for one family. The settings are a copy of the vanilla preset whose
     * {@code initialDensityWithoutJaggedness} has been replaced by a gradient that is only positive at
     * the very bottom of the world.
     * <p>
     * That function has exactly one job: {@code NoiseChunk} uses it to guess the terrain height
     * ({@code NoiseChunk:200-212}) which becomes the {@code above_preliminary_surface} gate that the
     * vanilla surface rules are wrapped in. Our islands are not noise terrain, so that guess would
     * often land above them and the whole surface rule would be skipped - islands with no surface at
     * all. Making the guess bottom out at y = MIN_Y opens the gate everywhere without touching
     * anything else, because the real terrain density ({@code finalDensity}) is not used by us at all.
     */
    private Bundle bundleFor(Family family) {
        long seed = BrokenIslandSeed.get();
        Bundle cached = bundles.get(family);
        if (cached != null && cached.seed() == seed) {
            return cached;
        }
        NoiseGeneratorSettings preset = noiseSettings.getOrThrow(family.preset).value();
        NoiseRouter router = preset.noiseRouter();
        NoiseRouter openGate = new NoiseRouter(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                router.temperature(),
                router.vegetation(),
                router.continents(),
                router.erosion(),
                router.depth(),
                router.ridges(),
                DensityFunctions.yClampedGradient(MIN_Y, MIN_Y + 2, 1.0D, -1.0D),
                router.finalDensity(),
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap());
        NoiseGeneratorSettings settings = new NoiseGeneratorSettings(
                preset.noiseSettings(),
                preset.defaultBlock(),
                preset.defaultFluid(),
                openGate,
                preset.surfaceRule(),
                preset.spawnTarget(),
                preset.seaLevel(),
                preset.disableMobGeneration(),
                preset.aquifersEnabled(),
                preset.oreVeinsEnabled(),
                preset.useLegacyRandomSource());
        // The router that matters is the one inside the RandomState: NoiseChunk takes it from there
        // (NoiseChunk:138), not from the settings it is handed. Building our own RandomState from the
        // flattened settings is therefore what actually opens the gate - and it brings a matching
        // surface system along for free.
        RandomState state = RandomState.create(settings, noiseParameters, seed);
        Bundle bundle = new Bundle(seed, state, settings, createFluidPicker(settings));
        bundles.put(family, bundle);
        return bundle;
    }

    /** Copied from {@code NoiseBasedChunkGenerator#createFluidPicker}. */
    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, LAVA);
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus fluid = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, seaLevel) ? lava : fluid;
    }

    /** Which family's rock and surface rule this island borrows. */
    private Family familyOf(BrokenIslandLayout.Island island) {
        Holder<Biome> biome = biomeOf(island);
        if (biome == null) {
            return Family.OVERWORLD;
        }
        if (biome.is(BiomeTags.IS_NETHER)) {
            return Family.NETHER;
        }
        if (biome.is(BiomeTags.IS_END)) {
            return Family.END;
        }
        // A mod biome without any of the three tags: borrow the noise settings of the dimension it belongs
        // to (found through the level stem registry), so the island gets that dimension's rock and surface
        // rule instead of grass - without which the mod's own features find no ground to grow on.
        Holder<Biome> biomeHolder = biome;
        Identifier fromDimension = BrokenBiomePool.noiseSettingsFor(biomeHolder);
        if (fromDimension != null) {
            return new Family(net.minecraft.resources.ResourceKey.create(Registries.NOISE_SETTINGS, fromDimension),
                    fromDimension.toString());
        }
        Identifier id = biomeHolder.unwrapKey().map(key -> key.identifier()).orElse(null);
        if (id != null && !"minecraft".equals(id.getNamespace())) {
            Family own = modDimension(id);
            if (own != null) {
                return own;
            }
        }
        return Family.OVERWORLD;
    }

    /**
     * The noise settings that look like they belong to a mod biome's own dimension: first an entry with
     * exactly the same id ({@code allthemodium:the_other} biome -&gt; {@code allthemodium:the_other}
     * settings, which is how mods usually name them), then - if the namespace has exactly one settings
     * entry - that one, because then there is no ambiguity. Otherwise {@code null} and the island falls
     * back to the overworld.
     */
    private Family modDimension(Identifier biomeId) {
        net.minecraft.resources.ResourceKey<NoiseGeneratorSettings> exact =
                net.minecraft.resources.ResourceKey.create(Registries.NOISE_SETTINGS, biomeId);
        if (noiseSettings.get(exact).isPresent()) {
            return new Family(exact, biomeId.getNamespace() + " (same id)");
        }
        Family only = null;
        for (Holder.Reference<NoiseGeneratorSettings> candidate : noiseSettings.listElements().toList()) {
            Identifier id = candidate.key().identifier();
            if (!id.getNamespace().equals(biomeId.getNamespace())) {
                continue;
            }
            if (only != null) {
                return null;
            }
            only = new Family(candidate.key(), id.getNamespace() + " (only entry)");
        }
        return only;
    }

    // ------------------------------------------------------------------ island shape

    /**
     * The vertical shape of one column: {@code {solidBottom, solidTop, waterTop}}, or {@code null}
     * when the column is open void ({@code waterTop} is {@code -1} for a plain island).
     */
    private static int[] column(long seed, BrokenIslandLayout.Island island, int worldX, int worldZ) {
        if (island == null) {
            return null;
        }
        // Elliptical distance: the island can be long instead of round (see BrokenIslandLayout).
        double distance = island.normalizedDistance(worldX, worldZ);
        if (distance > 1.0) {
            return null;
        }
        double bulge = Math.sqrt(Math.max(0.0, 1.0 - distance * distance));
        int centerY = island.centerY();
        double radius = island.radius();
        // The dome scales with the island, and it is deliberately <b>thick</b>: the user wants large
        // underground structures (dungeon mods in particular) to fit inside an island, and at radius 320
        // this is ~226 blocks from top to bottom, roughly 3.3x what it used to be. The bottom is clamped
        // to MIN_Y + 8 so no island ever grows into the void floor.
        int topLimit = MIN_Y + GEN_DEPTH - TOP_MARGIN;
        int bottomLimit = MIN_Y + BOTTOM_MARGIN;
        int solidTop = Math.min(topLimit, centerY + (int) Math.round((8.0 + radius * 0.02) * bulge));
        int solidBottom = Math.max(bottomLimit,
                centerY - (int) Math.round(8.0 + (70.0 + radius * 0.42) * bulge));
        if (!isLiquid(seed, island)) {
            return new int[]{solidBottom, solidTop, -1};
        }
        // Bowl: hollow the middle down to a basin and keep the outer ring solid, so the water of an
        // ocean or river biome cannot reach the void. Bigger islands get deeper water.
        int bowlDepth = BOWL_DEPTH_MIN + (island.variant() % (BOWL_DEPTH_MAX - BOWL_DEPTH_MIN + 1))
                + (int) Math.round(radius / 8.0);
        double rim = BOWL_RIM / Math.max(1.0, radius);
        if (distance > 1.0 - rim) {
            return new int[]{solidBottom, Math.min(MIN_Y + GEN_DEPTH - TOP_MARGIN, centerY + BOWL_LIP), -1};
        }
        // The basin floor follows the dome instead of being a flat plate (a flat floor made every water
        // island's underside a box sticking out of the curve), and it never comes closer than
        // BOWL_SHELL_MIN to the island's own bottom - without that clamp a deep basin on a big island
        // would cut straight through its floor.
        int basinFloor = Math.max(solidBottom + BOWL_SHELL_MIN,
                centerY - (int) Math.round(bowlDepth * bulge));
        return new int[]{solidBottom, basinFloor, centerY - 1};
    }

    /** True when the island's biome is one that has to hold water. */
    private static boolean isLiquid(long seed, BrokenIslandLayout.Island island) {
        Holder<Biome> biome = biomeOf(island);
        return biome != null && BrokenBiomePool.isLiquid(biome);
    }

    /** The biome an island uses, or {@code null} before the pool exists. */
    private static Holder<Biome> biomeOf(BrokenIslandLayout.Island island) {
        List<Holder<Biome>> pool = BrokenBiomePool.cachedPool();
        if (pool.isEmpty() || island == null) {
            return null;
        }
        return pool.get(Math.floorMod(island.biomeIndex(), pool.size()));
    }

    // ------------------------------------------------------------------ vanilla plumbing

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk) {
        // The islands are 120-226 blocks thick now, so vanilla's carvers fit inside them (user 2026-10-11:
        // "现在厚了这么多完全可以承接矿洞的生成").
        // <p>
        // Carving is delegated to a real vanilla generator built from this island's family settings and
        // from our own biome source, instead of reimplementing NoiseBasedChunkGenerator#applyCarvers:
        // CarvingContext only accepts a NoiseBasedChunkGenerator, and going through the vanilla generator
        // means the carvers used per chunk are exactly the ones the biome itself declares (a reordered or
        // stripped biome keeps its own carvers, which our feature-side settings do not carry).
        BrokenIslandLayout.Island island = BrokenIslandLayout.islandFor(BrokenIslandSeed.get(),
                chunk.getPos().getMinBlockX() + 8, chunk.getPos().getMinBlockZ() + 8);
        // islandFor returns null for a chunk that is not inside any island's radius - that is the ordinary
        // void case, and reading island.variant() there is what crashed chunk generation (2026-10-11).
        Family family = island == null ? Family.OVERWORLD : familyOf(island);
        // Most islands are carved dry; one in WET_ISLAND_CHANCE keeps vanilla's water table, so its caves
        // become water pockets (user 2026-10-11).
        boolean wet = wetIsland(island);
        net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator delegate = carvers.computeIfAbsent(
                new CarverKey(family, wet),
                key -> new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(getBiomeSource(),
                        Holder.direct(key.wet()
                                ? noiseSettings.getOrThrow(key.family().preset()).value()
                                : carverSettings(noiseSettings.getOrThrow(key.family().preset()).value()))));
        delegate.applyCarvers(region, seed, randomState, biomeManager, structureManager, chunk);
        dryOut(chunk);
    }

    /**
     * Removes the water the carvers left behind on a <b>dry</b> island.
     * <p>
     * Vanilla's aquifer derives its local water level from {@code preliminarySurfaceLevel(...)} - from where it
     * believes the terrain surface is. On a floating island the density field still describes the overworld,
     * so that level lands near the island's own top and everything below it fills with water. Turning the
     * aquifer off in the settings did not help (stage probe 2026-10-11: {@code snowy_beach} had 0 water after
     * NOISE and SURFACE, then 2430 water blocks after CARVERS), so the water is taken out here instead: on a
     * dry island, water inside the island's own span becomes air again. That keeps the cave and drops the
     * water, and it is what the user asked for - most islands dry, a few with water.
     * <p>
     * Wet islands (vanilla settings, one in {@link #WET_ISLAND_CHANCE}) and bowl islands (whose water we place
     * ourselves) are left alone.
     */
    private void dryOut(ChunkAccess chunk) {
        long seed = BrokenIslandSeed.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;
                BrokenIslandLayout.Island island = BrokenIslandLayout.islandFor(seed, worldX, worldZ);
                if (island == null || isLiquid(seed, island) || wetIsland(island)) {
                    continue;
                }
                int[] profile = column(seed, island, worldX, worldZ);
                if (profile == null) {
                    continue;
                }
                for (int y = Math.max(profile[0], minY); y <= profile[1]; y++) {
                    if (chunk.getBlockState(pos.set(worldX, y, worldZ)).is(Blocks.WATER)) {
                        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /**
     * A copy of these settings that carves dry caves, used only to run the carvers of a dry island.
     * <p>
     * Two things had to change, and the second one is the one that actually works:
     * <ol>
     *   <li>the water table is pushed down to {@link #CARVER_WATER_TABLE}. That alone is <b>not</b> enough:
     *       {@code Aquifer.computeFluid} derives the local water level from
     *       {@code noiseChunk.preliminarySurfaceLevel(...)} - i.e. from where it thinks the terrain surface
     *       is. On a floating island the overworld density field puts that surface near the island's top, so
     *       the aquifer believed the whole island sat under water (user 2026-10-11: still "全是含水洞" after
     *       the first attempt);</li>
     *   <li>aquifers are <b>disabled</b>. Vanilla then uses {@code Aquifer.createDisabled(...)}, whose whole
     *       implementation is "ask the global fluid picker": with a water table at {@link
     *       #CARVER_WATER_TABLE} that yields air above it, water in the thin band below it, and lava below
     *       y = -54 - all deterministic and independent of any noise.</li>
     * </ol>
     * A bowl island's own water is placed by us, not by the aquifer, so it is unaffected either way.
     */
    private static NoiseGeneratorSettings carverSettings(NoiseGeneratorSettings settings) {
        return new NoiseGeneratorSettings(settings.noiseSettings(), settings.defaultBlock(),
                settings.defaultFluid(), settings.noiseRouter(), settings.surfaceRule(),
                settings.spawnTarget(), CARVER_WATER_TABLE, settings.disableMobGeneration(),
                false, settings.oreVeinsEnabled(), settings.useLegacyRandomSource());
    }

    /**
     * Puts a water-holding island back together after carving.
     * <p>
     * Carvers cannot replace water, but they happily carve the stone <i>under</i> it - so a cave opening in
     * the basin floor leaves the ocean hanging over a hole, which looks like the water was displaced and
     * pours into the cave the moment it is touched (user 2026-10-11: "有些水会被矿洞生成顶掉"). This
     * restores exactly the parts that keep a bowl watertight - the water column, an {@link #BOWL_SHELL_MIN}
     * block shell under the basin floor, and the solid rim ring - and leaves every other cave alone.
     */
    private void sealBowls(net.minecraft.world.level.LevelAccessor level, ChunkAccess chunk) {
        long seed = BrokenIslandSeed.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinY();
        List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes = structureBoxes(level, chunk);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;
                BrokenIslandLayout.Island island = BrokenIslandLayout.islandFor(seed, worldX, worldZ);
                Holder<Biome> biome = biomeOf(island);
                if (biome == null || !BrokenBiomePool.isLiquid(biome)) {
                    continue;
                }
                int[] profile = column(seed, island, worldX, worldZ);
                if (profile == null) {
                    continue;
                }
                Family family = familyOf(island);
                NoiseGeneratorSettings islandSettings = bundleFor(family).settings();
                if (profile[2] < 0) {
                    // The solid rim ring: the only thing stopping the water from pouring out sideways.
                    if (profile[1] == island.centerY() + BOWL_LIP) {
                        for (int y = Math.max(profile[0], minY); y <= profile[1]; y++) {
                            fillIfAir(chunk, pos, worldX, y, worldZ, fillerAt(family, islandSettings, y), boxes);
                        }
                    }
                    continue;
                }
                for (int y = profile[1] + 1; y <= profile[2]; y++) {
                    fillIfAir(chunk, pos, worldX, y, worldZ, WATER, boxes);
                }
                // No shell under the basin floor on purpose (user 2026-10-13): the rim is what keeps the water
                // in, and the floor is the one place where the carvers may cut freely - that randomness is the
                // point. Water sitting over a cave is accepted; it only drains once something touches it.
            }
        }
    }

    // ------------------------------------------------------------------ structures

    /** Least clearance between a structure's lowest block and the island's underside. */
    static int STRUCTURE_UNDERSIDE_MARGIN = 4;

    /**
     * Extra headroom reserved <b>above</b> the default band for the structures that stand on an island.
     * <p>
     * Zero by default, which is the user's model (2026-10-13): the default height layers stay at or below 128,
     * so everything above them is already free for structures, and an island only needs {@link #TOP_MARGIN}
     * kept clear of the build limit. Raise this only if a pack's structures need even more room than the
     * default band gives them - it pushes every island further down.
     */
    static int STRUCTURE_HEADROOM = 0;

    /**
     * Extra clearance a structure's bounding box must keep from the island's rim (0 = it may touch the rim).
     * This is the zero-cost alternative to enlarging an island for a structure (see PLAN-撑岛调研.md): raising
     * it drops structures that would hug the edge, at the price of skipping more of them.
     */
    static int STRUCTURE_RIM_MARGIN = 0;

    /**
     * TEMPORARY (2026-10-11): force every island to be carved dry, so the user can settle whether carved
     * water still shows up anywhere. With this on there must be no water block inside any island's caves -
     * only the lava layer below y = -54 - and any water that is still seen comes from somewhere else.
     * Set back to {@code false} (or delete) once the question is answered.
     */
    static boolean FORCE_ALL_DRY = false;

    /** True when this island keeps vanilla's water in its caves (see {@link #WET_ISLAND_CHANCE}). */
    static boolean wetIsland(BrokenIslandLayout.Island island) {
        return !FORCE_ALL_DRY && island != null
                && Math.floorMod(island.variant(), WET_ISLAND_CHANCE) == 0;
    }

    /**
     * The water table the carvers work with on a dry island: at the lava level, so the global fluid picker
     * offers water nowhere at all and only the lava layer below y = -54 remains. (Vanilla's own aquifer is
     * switched off in {@link #carverSettings(NoiseGeneratorSettings)}, so this picker is the only thing that
     * decides.) A wet island keeps vanilla's settings and therefore vanilla's water.
     */
    static int CARVER_WATER_TABLE = -54;

    /**
     * Headroom kept under the build limit and above the world floor, in blocks.
     * <p>
     * An island's top reaches {@code centreY + 8 + 0.02*radius} and its bottom {@code centreY - (78 + 0.42*radius)},
     * so a layer that is too high (or too low) for the island's radius would be cut flat by the build limit.
     * These margins decide where the cut happens if the configuration asks for one; they also guarantee that
     * nothing is ever written out of bounds. Both default to 8, which is what the code did before they were
     * configurable.
     */
    static int TOP_MARGIN = 8;
    static int BOTTOM_MARGIN = 8;

    /** Structures that were skipped because no island could hold them, so the log says it once each. */
    private static final java.util.Set<String> STRUCTURES_SKIPPED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Structure placement, copied from {@code ChunkGenerator#createStructures} with one rule added: a
     * structure only takes root when the island it lands on can actually hold it.
     * <p>
     * The user's requirement from the start of M2 was "有些结构特别大，比如古代城…至少得接得住". A jigsaw
     * structure that hangs off an island's rim, or whose bottom pokes out of the island's underside into the
     * void, looks broken - and because islands are up to 320 blocks in radius and over 200 blocks thick,
     * most structures do fit. The ones that do not are skipped and logged once each.
     */
    @Override
    public void createStructures(net.minecraft.core.RegistryAccess registries,
                                 net.minecraft.world.level.chunk.ChunkGeneratorStructureState state,
                                 StructureManager manager, ChunkAccess chunk,
                                 net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
                                 net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level) {
        ChunkPos chunkPos = chunk.getPos();
        SectionPos section = SectionPos.bottomOf(chunk);
        RandomState randomState = state.randomState();
        for (Holder<net.minecraft.world.level.levelgen.structure.StructureSet> set : state.possibleStructureSets()) {
            net.minecraft.world.level.levelgen.structure.placement.StructurePlacement placement =
                    set.value().placement();
            List<net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry> entries =
                    set.value().structures();
            boolean alreadyThere = false;
            for (net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry : entries) {
                net.minecraft.world.level.levelgen.structure.StructureStart existing =
                        manager.getStartForStructure(section, entry.structure().value(), chunk);
                if (existing != null && existing.isValid()) {
                    alreadyThere = true;
                    break;
                }
            }
            if (alreadyThere || !placement.isStructureChunk(state, chunkPos.x(), chunkPos.z())) {
                continue;
            }
            if (entries.size() == 1) {
                tryGenerateStructure(entries.get(0), manager, registries, randomState, templates,
                        state.getLevelSeed(), chunk, chunkPos, section, level);
                continue;
            }
            // Several structures share this set: pick by weight, exactly like vanilla, but with our own
            // deterministic seed so a candidate that no island can hold does not poison the choice.
            List<net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry> remaining =
                    new ArrayList<>(entries);
            java.util.Random pick = new java.util.Random(state.getLevelSeed() * 31L + chunkPos.x() * 17L + chunkPos.z());
            int total = 0;
            for (net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry : remaining) {
                total += entry.weight();
            }
            while (!remaining.isEmpty()) {
                int roll = pick.nextInt(total);
                int index = 0;
                for (net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry : remaining) {
                    roll -= entry.weight();
                    if (roll < 0) {
                        break;
                    }
                    index++;
                }
                net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry chosen =
                        remaining.get(index);
                if (tryGenerateStructure(chosen, manager, registries, randomState, templates,
                        state.getLevelSeed(), chunk, chunkPos, section, level)) {
                    return;
                }
                remaining.remove(index);
                total -= chosen.weight();
            }
        }
    }

    /** Copy of {@code ChunkGenerator#tryGenerateStructure} with the island fit test in front of the start. */
    private boolean tryGenerateStructure(
            net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry,
            StructureManager manager, net.minecraft.core.RegistryAccess registries, RandomState randomState,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed, ChunkAccess chunk, ChunkPos chunkPos, SectionPos section,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level) {
        net.minecraft.world.level.levelgen.structure.Structure structure = entry.structure().value();
        net.minecraft.world.level.levelgen.structure.StructureStart previous =
                manager.getStartForStructure(section, structure, chunk);
        int references = previous != null ? previous.getReferences() : 0;
        // 26.x: Structure#generate now takes the structure's own Holder and the dimension key in front of the
        // registry access (it profiles the generation with both).
        net.minecraft.world.level.levelgen.structure.StructureStart start = structure.generate(
                entry.structure(), level, registries, this, getBiomeSource(), randomState, templates, seed,
                chunkPos, references, chunk, structure.biomes()::contains);
        if (!start.isValid()) {
            return false;
        }
        if (!islandCanHold(start, seed)) {
            if (STRUCTURES_SKIPPED.add(registries.lookupOrThrow(Registries.STRUCTURE)
                    .getKey(structure) + " @ " + chunkPos)) {
                FlightRingMod.LOGGER.info(
                        "[STRUCTURES] skipped {} at {} - no island there is big or thick enough to hold it",
                        registries.lookupOrThrow(Registries.STRUCTURE).getKey(structure), chunkPos);
            }
            return false;
        }
        manager.setStartForStructure(section, structure, start, chunk);
        return true;
    }

    /**
     * True when the island under this structure can hold it: the whole bounding box has to sit inside the
     * island's footprint, and the lowest block must stay clear of the island's own underside.
     */
    private boolean islandCanHold(net.minecraft.world.level.levelgen.structure.StructureStart start, long seed) {
        net.minecraft.world.level.levelgen.structure.BoundingBox box = start.getBoundingBox();
        int centerX = (box.minX() + box.maxX()) / 2;
        int centerZ = (box.minZ() + box.maxZ()) / 2;
        BrokenIslandLayout.Island island = BrokenIslandLayout.islandCovering(seed, centerX, centerZ, 0.0);
        if (island == null) {
            return false;
        }
        if (!island.containsBox(box.minX() - STRUCTURE_RIM_MARGIN, box.minZ() - STRUCTURE_RIM_MARGIN,
                box.maxX() + STRUCTURE_RIM_MARGIN, box.maxZ() + STRUCTURE_RIM_MARGIN)) {
            return false;
        }
        int[] center = column(seed, island, island.centerX(), island.centerZ());
        // Top end: a structure that would reach past the margin under the build limit is skipped rather than cut
        // off (the user's rule for a hand-raised height layer: "岛顶到上限减默认 8 格，超过的结构就跳过").
        boolean fitsAbove = box.maxY() <= MIN_Y + GEN_DEPTH - TOP_MARGIN - STRUCTURE_HEADROOM;
        return center != null && fitsAbove && box.minY() >= center[0] + STRUCTURE_UNDERSIDE_MARGIN;
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // Mobs spawn normally once the chunks are live.
    }

    @Override
    public int getGenDepth() {
        return GEN_DEPTH;
    }

    @Override
    public int getSeaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        int[] profile = column(BrokenIslandSeed.get(), BrokenIslandLayout.islandFor(BrokenIslandSeed.get(), x, z), x, z);
        if (profile == null) {
            return level.getMinY();
        }
        return profile[2] < 0 ? profile[1] : profile[2];
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int height = level.getHeight();
        BlockState[] states = new BlockState[height];
        int minY = level.getMinY();
        BrokenIslandLayout.Island island = BrokenIslandLayout.islandFor(BrokenIslandSeed.get(), x, z);
        int[] profile = column(BrokenIslandSeed.get(), island, x, z);
        if (profile == null) {
            for (int i = 0; i < height; i++) {
                states[i] = AIR;
            }
        } else {
            Family family = familyOf(island);
            NoiseGeneratorSettings islandSettings = bundleFor(family).settings();
            for (int i = 0; i < height; i++) {
                int y = minY + i;
                states[i] = y >= profile[0] && y <= profile[1] ? fillerAt(family, islandSettings, y)
                        : profile[2] >= 0 && y > profile[1] && y <= profile[2] ? WATER
                        : AIR;
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        BrokenIslandLayout.Island island = BrokenIslandLayout.islandFor(BrokenIslandSeed.get(), pos.getX(), pos.getZ());
        lines.add(island == null
                ? "Broken: void"
                : "Broken island grid(" + island.gridX() + "," + island.gridZ() + ") center=("
                        + island.centerX() + "," + island.centerY() + "," + island.centerZ() + ") r="
                        + island.radius() + " biomeIndex=" + island.biomeIndex()
                        + " biome=" + biomeName(island)
                        + " family=" + familyOf(island).label()
                        // Shown so a world can be judged on the spot: a dry island's caves must be water-free,
                        // a "wet" one (one in WET_ISLAND_CHANCE) keeps vanilla's water on purpose.
                        + (wetIsland(island) ? " wet-caves" : " dry-caves")
                        + (isLiquid(BrokenIslandSeed.get(), island) ? " bowl" : ""));
    }

    /** The biome the island actually uses - the debug line needs it to spot wrong picks. */
    private static String biomeName(BrokenIslandLayout.Island island) {
        List<Holder<Biome>> pool = BrokenBiomePool.cachedPool();
        if (pool.isEmpty()) {
            return "<pool empty>";
        }
        return pool.get(Math.floorMod(island.biomeIndex(), pool.size()))
                .unwrapKey().map(key -> key.identifier().toString()).orElse("<unnamed>");
    }
}

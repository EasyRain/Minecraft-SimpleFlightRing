package com.flightring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The generation settings a world was created with, frozen so that later config edits cannot reshape an island
 * that already exists.
 * <p>
 * Why this is needed (user 2026-10-13: "已经生成了的岛…还是按原配置生成，其余没生成的再跟着配置变"): island
 * shape is a pure function of {@code (seed, gridCell)}, so changing the radius, the gaps or the height layers
 * changes how the <b>not yet generated</b> chunks of an <b>already partly generated</b> island come out - the
 * island gets a seam where the new parameters take over.
 * <p>
 * The biome pool has the same problem one step worse: an island's biome is {@code pool.get(biomeIndex % size)},
 * so adding or removing a single pool entry reshuffles the biome of <b>every</b> island in the world. Freezing
 * the accepted biome ids as well keeps old worlds stable.
 * <p>
 * Stored in the overworld's data storage, so it travels with the world, and re-applied on every server start.
 * Settings are only captured for a world whose settings do not exist yet; a world created after a config change
 * simply picks the new values up.
 * <p>
 * <b>26.x port note:</b> {@code SavedData} no longer has {@code save}/{@code load} or a nested {@code Factory} -
 * a {@link SavedDataType} carries an {@link Identifier}, a constructor and a {@link Codec} instead, and
 * {@code SavedDataStorage#computeIfAbsent} takes that type. The NBT layout therefore changed from the 1.21.1
 * flat {@code <key>: <value>} tag to the codec's {@code {data: {...}}} wrapper, and the file moved from
 * {@code <world>/data/simpleflightring_broken.dat} to
 * {@code <world>/dimensions/minecraft/overworld/data/simpleflightring/simpleflightring_broken.dat}
 * (26.x keeps each level's saved data in its own dimension folder, and {@code Identifier#resolveAgainst} nests
 * one more level by namespace). An existing 1.21.1 world therefore re-captures its freeze once on first load in
 * 26.x.
 */
public class BrokenWorldSettings extends SavedData {

    private static final String FILE_ID = "simpleflightring_broken";

    /** The three {@code int[]} height layers travel as a list - the codec primitives have no array form. */
    private static final Codec<BrokenWorldSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("min_radius").forGetter(settings -> settings.minRadius),
            Codec.INT.fieldOf("max_radius").forGetter(settings -> settings.maxRadius),
            Codec.INT.fieldOf("gap_min").forGetter(settings -> settings.gapMin),
            Codec.INT.fieldOf("gap_max").forGetter(settings -> settings.gapMax),
            Codec.INT.listOf().fieldOf("height_layers").forGetter(BrokenWorldSettings::heightLayersAsList),
            Codec.DOUBLE.fieldOf("max_stretch").forGetter(settings -> settings.maxStretch),
            Codec.INT.fieldOf("top_margin").forGetter(settings -> settings.topMargin),
            Codec.INT.fieldOf("bottom_margin").forGetter(settings -> settings.bottomMargin),
            Codec.INT.fieldOf("wet_island_chance").forGetter(settings -> settings.wetIslandChance),
            Codec.INT.fieldOf("carver_water_table").forGetter(settings -> settings.carverWaterTable),
            Codec.STRING.listOf().fieldOf("biomes").forGetter(settings -> settings.biomeIds)
    ).apply(instance, BrokenWorldSettings::new));

    private static final SavedDataType<BrokenWorldSettings> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("simpleflightring", FILE_ID), BrokenWorldSettings::new, CODEC);

    private int minRadius;
    private int maxRadius;
    private int gapMin;
    private int gapMax;
    private int[] heightLayers = new int[0];
    private double maxStretch;
    private int topMargin;
    private int bottomMargin;
    private int wetIslandChance;
    private int carverWaterTable;
    private final List<String> biomeIds = new ArrayList<>();

    /** Used as the {@link SavedDataType} constructor for a world that has no file yet. */
    public BrokenWorldSettings() {
    }

    /** The codec's constructor: the parameter order has to match the {@code group(...)} above. */
    private BrokenWorldSettings(int minRadius, int maxRadius, int gapMin, int gapMax, List<Integer> heightLayers,
                                double maxStretch, int topMargin, int bottomMargin, int wetIslandChance,
                                int carverWaterTable, List<String> biomeIds) {
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.gapMin = gapMin;
        this.gapMax = gapMax;
        this.heightLayers = toArray(heightLayers);
        this.maxStretch = maxStretch;
        this.topMargin = topMargin;
        this.bottomMargin = bottomMargin;
        this.wetIslandChance = wetIslandChance;
        this.carverWaterTable = carverWaterTable;
        this.biomeIds.addAll(biomeIds);
    }

    /**
     * Loads a world's frozen settings. A world without a file gets a fresh, empty instance ({@code isFrozen()}
     * is false) which is what the caller captures into - 26.x's {@code computeIfAbsent} never returns null.
     */
    public static BrokenWorldSettings get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** True when this instance came from disk (i.e. the world already has frozen settings). */
    public boolean isFrozen() {
        return maxRadius > 0;
    }

    private static int[] toArray(List<Integer> values) {
        int[] array = new int[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private List<Integer> heightLayersAsList() {
        int[] layers = heightLayers == null ? new int[0] : heightLayers;
        List<Integer> values = new ArrayList<>(layers.length);
        for (int layer : layers) {
            values.add(layer);
        }
        return values;
    }

    /** Copies the values worldgen currently uses into this instance. */
    public void capture() {
        minRadius = BrokenIslandLayout.MIN_RADIUS;
        maxRadius = BrokenIslandLayout.MAX_RADIUS;
        gapMin = BrokenIslandLayout.GAP_MIN;
        gapMax = BrokenIslandLayout.GAP_MAX;
        heightLayers = BrokenIslandLayout.HEIGHT_LAYERS.clone();
        maxStretch = BrokenIslandLayout.MAX_STRETCH;
        topMargin = BrokenIslandChunkGenerator.TOP_MARGIN;
        bottomMargin = BrokenIslandChunkGenerator.BOTTOM_MARGIN;
        // The cave knobs belong here too: they are decided per island, so changing them mid-world would give one
        // island a dry half and a wet half.
        wetIslandChance = BrokenIslandChunkGenerator.WET_ISLAND_CHANCE;
        carverWaterTable = BrokenIslandChunkGenerator.CARVER_WATER_TABLE;
        biomeIds.clear();
        BrokenBiomePool.cachedPool().forEach(biome -> biome.unwrapKey()
                .map(key -> key.identifier().toString())
                .ifPresent(biomeIds::add));
        setDirty();
    }

    /** Puts the frozen values back in place, overriding whatever the config file says. */
    public void apply() {
        BrokenIslandLayout.MIN_RADIUS = minRadius;
        BrokenIslandLayout.MAX_RADIUS = maxRadius;
        BrokenIslandLayout.GAP_MIN = gapMin;
        BrokenIslandLayout.GAP_MAX = gapMax;
        BrokenIslandLayout.MAX_JITTER = (gapMax - gapMin) / 4;
        if (heightLayers != null && heightLayers.length > 0) {
            BrokenIslandLayout.HEIGHT_LAYERS = heightLayers.clone();
        }
        BrokenIslandLayout.MAX_STRETCH = maxStretch;
        BrokenIslandChunkGenerator.TOP_MARGIN = topMargin;
        BrokenIslandChunkGenerator.BOTTOM_MARGIN = bottomMargin;
        if (wetIslandChance > 0) {
            BrokenIslandChunkGenerator.WET_ISLAND_CHANCE = wetIslandChance;
            BrokenIslandChunkGenerator.CARVER_WATER_TABLE = carverWaterTable;
        }
        BrokenBiomePool.frozenBiomeIds = List.copyOf(biomeIds);
        FlightRingMod.LOGGER.info(
                "[Config] world settings are frozen to radius {}..{}, gap {}..{}, {} height layers, {} biomes"
                        + " - config edits apply to new worlds only",
                minRadius, maxRadius, gapMin, gapMax, heightLayers == null ? 0 : heightLayers.length, biomeIds.size());
    }
}

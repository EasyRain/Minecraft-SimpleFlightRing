package com.flightring;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

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
 */
public class BrokenWorldSettings extends net.minecraft.world.level.saveddata.SavedData {

    private static final String FILE_ID = "simpleflightring_broken";

    private int minRadius;
    private int maxRadius;
    private int gapMin;
    private int gapMax;
    private int[] heightLayers;
    private double maxStretch;
    private int topMargin;
    private int bottomMargin;
    private int wetIslandChance;
    private int carverWaterTable;
    private final List<String> biomeIds = new ArrayList<>();

    public BrokenWorldSettings() {
    }

    /** Loads a world's frozen settings, or {@code null} when this world has none yet. */
    public static BrokenWorldSettings get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                        BrokenWorldSettings::new, BrokenWorldSettings::load, null), FILE_ID);
    }

    /** True when this instance came from disk (i.e. the world already has frozen settings). */
    public boolean isFrozen() {
        return maxRadius > 0;
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
                .map(key -> key.location().toString())
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

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("minRadius", minRadius);
        tag.putInt("maxRadius", maxRadius);
        tag.putInt("gapMin", gapMin);
        tag.putInt("gapMax", gapMax);
        tag.putIntArray("heightLayers", heightLayers == null ? new int[0] : heightLayers);
        tag.putDouble("maxStretch", maxStretch);
        tag.putInt("topMargin", topMargin);
        tag.putInt("bottomMargin", bottomMargin);
        tag.putInt("wetIslandChance", wetIslandChance);
        tag.putInt("carverWaterTable", carverWaterTable);
        ListTag ids = new ListTag();
        biomeIds.forEach(id -> ids.add(StringTag.valueOf(id)));
        tag.put("biomes", ids);
        return tag;
    }

    private static BrokenWorldSettings load(CompoundTag tag, HolderLookup.Provider registries) {
        BrokenWorldSettings settings = new BrokenWorldSettings();
        settings.minRadius = tag.getInt("minRadius");
        settings.maxRadius = tag.getInt("maxRadius");
        settings.gapMin = tag.getInt("gapMin");
        settings.gapMax = tag.getInt("gapMax");
        settings.heightLayers = tag.getIntArray("heightLayers");
        settings.maxStretch = tag.getDouble("maxStretch");
        settings.topMargin = tag.getInt("topMargin");
        settings.bottomMargin = tag.getInt("bottomMargin");
        settings.wetIslandChance = tag.getInt("wetIslandChance");
        settings.carverWaterTable = tag.getInt("carverWaterTable");
        ListTag ids = tag.getList("biomes", Tag.TAG_STRING);
        for (int i = 0; i < ids.size(); i++) {
            settings.biomeIds.add(ids.getString(i));
        }
        return settings;
    }
}

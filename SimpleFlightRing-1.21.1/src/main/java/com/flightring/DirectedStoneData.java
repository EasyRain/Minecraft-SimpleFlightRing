package com.flightring;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-level record of the <b>one</b> directed reforge stone the broken dimension is allowed to hand out.
 * <p>
 * User 2026-11-05: "定向重铸石同样只能在维度的所有战利品箱子里找到而且概率只有 1%……定向只出现其中一种定向，
 * 也就是说刷出了其中一种定向后就不会刷出另一种定向，而且固定最多只刷一个". So the first successful 1% roll picks
 * the kind and locks the world: every later roll finds this flag set and yields nothing. The player can then
 * duplicate that one stone forever with the copy recipe.
 * <p>
 * Stored in the overworld's data storage so it survives restarts and travels with the world.
 */
public class DirectedStoneData extends SavedData {

    private static final String FILE_ID = "simpleflightring_directed_stone";

    private boolean spawned;
    private String kind = "";

    /** Used as the factory constructor for a world that has no file yet. */
    public DirectedStoneData() {
    }

    public static DirectedStoneData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DirectedStoneData::new, DirectedStoneData::load, null), FILE_ID);
    }

    public boolean spawned() {
        return this.spawned;
    }

    public String kind() {
        return this.kind;
    }

    /**
     * Claims this world's single stone. Returns the chosen kind ({@code prefix} / {@code rarity}), or
     * {@code null} when the stone was already handed out - the caller then produces nothing.
     */
    public String claim(RandomSource random) {
        if (this.spawned) {
            return null;
        }
        this.spawned = true;
        this.kind = random.nextBoolean() ? ReforgeMode.PREFIX.key() : ReforgeMode.RARITY.key();
        setDirty();
        return this.kind;
    }

    private static DirectedStoneData load(CompoundTag tag, HolderLookup.Provider registries) {
        DirectedStoneData data = new DirectedStoneData();
        data.spawned = tag.getBoolean("spawned");
        data.kind = tag.getString("kind");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("spawned", this.spawned);
        tag.putString("kind", this.kind);
        return tag;
    }
}

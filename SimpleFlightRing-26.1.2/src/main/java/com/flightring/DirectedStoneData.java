package com.flightring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-level record of the <b>one</b> directed reforge stone the broken dimension is allowed to hand out.
 * <p>
 * User 2026-11-05: "定向重铸石同样只能在维度的所有战利品箱子里找到而且概率只有 1%……定向只出现其中一种定向，
 * 也就是说刷出了其中一种定向后就不会刷出另一种定向，而且固定最多只刷一个". So the first successful 1% roll picks
 * the kind and locks the world: every later roll finds this flag set and yields nothing. The player can then
 * duplicate that one stone forever with the copy recipe.
 * <p>
 * Stored in the overworld's data storage so it survives restarts and travels with the world.
 * <p>
 * <b>26.x port note:</b> {@code SavedData} has no {@code save}/{@code load} here - a {@link SavedDataType} carries
 * the id, the constructor and a {@link Codec} (same pattern as {@link BrokenWorldSettings}).
 */
public class DirectedStoneData extends SavedData {

    private static final Codec<DirectedStoneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("spawned", false).forGetter(data -> data.spawned),
            Codec.STRING.optionalFieldOf("kind", "").forGetter(data -> data.kind)
    ).apply(instance, DirectedStoneData::new));

    private static final SavedDataType<DirectedStoneData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("simpleflightring", "directed_stone"),
            DirectedStoneData::new, CODEC);

    private boolean spawned;
    private String kind = "";

    /** Used as the {@link SavedDataType} constructor for a world that has no file yet. */
    public DirectedStoneData() {
    }

    private DirectedStoneData(boolean spawned, String kind) {
        this.spawned = spawned;
        this.kind = kind;
    }

    public static DirectedStoneData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
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
}

package com.flightring;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Which half of the affix a reforge stone rerolls.
 * <p>
 * A plain stone (no component) is {@link #BOTH}, exactly the old behaviour. Storing the mode on the <b>stone</b>
 * lets one item cover all three cases: the anvil copies it onto the preview ring and the roll happens when the
 * result is taken (see {@link RingAnvilHandler}).
 */
public enum ReforgeMode implements StringRepresentable {

    /** Reroll the prefix and the rarity (default). */
    BOTH("both"),
    /** Reroll only the prefix, keep the rarity. */
    PREFIX("prefix"),
    /** Reroll only the rarity, keep the prefix. */
    RARITY("rarity");

    public static final Codec<ReforgeMode> CODEC = StringRepresentable.fromEnum(ReforgeMode::values);

    public static final StreamCodec<ByteBuf, ReforgeMode> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(index -> values()[index], ReforgeMode::ordinal);

    private final String key;

    ReforgeMode(String key) {
        this.key = key;
    }

    @Override
    public String getSerializedName() {
        return this.key;
    }

    /** The command / lang key of this mode. */
    public String key() {
        return this.key;
    }

    /**
     * The mode a reforge stone implies: the directed stone carries its own component, a plain stone falls back to
     * its (usually absent) component, i.e. {@link #BOTH}.
     */
    public static ReforgeMode of(net.minecraft.world.item.ItemStack stone) {
        if (stone.is(ModItems.REFORGE_STONE_DIRECTED.get())) {
            ReforgeMode mode = stone.get(ModDataComponents.REFORGE_MODE.get());
            return mode == null ? BOTH : mode;
        }
        return stone.getOrDefault(ModDataComponents.REFORGE_MODE.get(), BOTH);
    }

    /** Looks a mode up by key, or {@code null} when it is not one of the three. */
    public static ReforgeMode byKey(String key) {
        for (ReforgeMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return null;
    }
}

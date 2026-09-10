package com.flightring;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The special rings' built-in (INTRINSIC) enchantment levels, keyed by enchantment id.
 * <p>
 * Keying by {@link ResourceKey} keeps storage and upgrades registry-free: the levels
 * can be read and raised without resolving a {@code Holder}, and they never touch the
 * vanilla enchantments component, so nothing (grindstone, ...) can strip them.
 * <p>
 * The base level of each built-in enchantment lives on the item itself
 * ({@link FlightRingItem}); this component only carries levels raised above the base
 * by the Powered Flight Ring's upgrade recipe.
 */
public record IntrinsicEnchants(Map<ResourceKey<Enchantment>, Integer> levels) {

    public static final IntrinsicEnchants EMPTY = new IntrinsicEnchants(Map.of());

    public static final Codec<IntrinsicEnchants> CODEC =
            Codec.unboundedMap(ResourceKey.codec(Registries.ENCHANTMENT), Codec.intRange(1, 255))
                    .xmap(IntrinsicEnchants::new, IntrinsicEnchants::levels);

    public static final StreamCodec<ByteBuf, IntrinsicEnchants> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, ResourceKey.streamCodec(Registries.ENCHANTMENT), ByteBufCodecs.VAR_INT)
                    .map(IntrinsicEnchants::new, intrinsic -> new HashMap<>(intrinsic.levels()));

    /** Stored level of the given enchantment, 0 when absent. */
    public int level(ResourceKey<Enchantment> enchantment) {
        return levels.getOrDefault(enchantment, 0);
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }

    /** A copy with the given enchantment set to the given level. */
    public IntrinsicEnchants with(ResourceKey<Enchantment> enchantment, int level) {
        Map<ResourceKey<Enchantment>, Integer> copy = new LinkedHashMap<>(levels);
        copy.put(enchantment, level);
        return new IntrinsicEnchants(Map.copyOf(copy));
    }
}

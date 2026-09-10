package com.flightring;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components added by this mod. {@link #INDESTRUCTIBLE} marks a flight
 * ring forged with the Indestructible Core: it never loses durability, its
 * tooltip shows infinite flight time and the HUD countdown is hidden.
 * {@link #INTRINSIC_ENCHANTMENTS} holds the special rings' built-in enchantments:
 * they are kept outside the vanilla enchantments component, so they cannot be
 * removed (grindstone, ...) - see {@code EnchantmentHelperMixin}.
 * {@link #RING_ENERGY} is the energy pool of the linked rings (Magic Lining) and
 * is absent while that pool is full - see {@link RingEnergy}.
 */
public class ModDataComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FlightRingMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> INDESTRUCTIBLE =
            COMPONENTS.registerComponentType("indestructible", builder -> builder
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<IntrinsicEnchants>> INTRINSIC_ENCHANTMENTS =
            COMPONENTS.registerComponentType("intrinsic_enchantments", builder -> builder
                    .persistent(IntrinsicEnchants.CODEC)
                    .networkSynchronized(IntrinsicEnchants.STREAM_CODEC));

    /** Remaining energy of a linked ring; the component is absent while the pool is full. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> RING_ENERGY =
            COMPONENTS.registerComponentType("ring_energy", builder -> builder
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT));

    private ModDataComponents() {
    }
}

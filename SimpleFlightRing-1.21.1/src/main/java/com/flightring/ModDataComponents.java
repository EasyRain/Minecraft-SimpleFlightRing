package com.flightring;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
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

    private ModDataComponents() {
    }
}

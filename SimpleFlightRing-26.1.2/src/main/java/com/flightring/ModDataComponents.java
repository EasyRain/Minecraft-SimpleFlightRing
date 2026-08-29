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
 */
public class ModDataComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FlightRingMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> INDESTRUCTIBLE =
            COMPONENTS.registerComponentType("indestructible", builder -> builder
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    private ModDataComponents() {
    }
}

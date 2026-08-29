package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Resource keys of this mod's data-driven enchantments.
 */
public class ModEnchantments {

    /**
     * "Flight Stability" - a treasure-style enchantment (not obtainable from the
     * enchanting table, anvil only, since it has no primary items): while flying,
     * mining blocks is no longer slowed down.
     */
    public static final ResourceKey<Enchantment> FLIGHT_STABILITY =
            ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "flight_stability"));

    private ModEnchantments() {
    }
}

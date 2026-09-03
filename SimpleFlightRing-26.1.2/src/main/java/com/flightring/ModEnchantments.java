package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
                    Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "flight_stability"));

    /**
     * "Rocket Boost" - while elytra-gliding (or any other form of gliding, e.g.
     * armour-based flight from other mods), pressing the jump key fires a
     * firework-rocket-style boost. Three levels by default; each level matches the
     * equivalent firework-rocket flight duration and costs {@code level * 10}
     * durability points of the ring per use. Higher levels from mods that raise
     * the enchantment cap are honoured as-is.
     */
    public static final ResourceKey<Enchantment> ROCKET_BOOST =
            ResourceKey.create(Registries.ENCHANTMENT,
                    Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "rocket_boost"));

    private ModEnchantments() {
    }
}

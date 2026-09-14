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

    /**
     * "Arcane Amplification" (Chinese: 魔能增幅) - a treasure-style enchantment with nine
     * levels: every level multiplies the ring's energy pool by the ring's base pool, i.e.
     * the maximum becomes {@code base * (level + 1)} (see {@link RingEnergy#max}).
     */
    public static final ResourceKey<Enchantment> ARCANE_AMPLIFICATION =
            ResourceKey.create(Registries.ENCHANTMENT,
                    Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "arcane_amplification"));

    /**
     * "Energy Burst" (Chinese: 能量迸发) - an enchanting-table enchantment for the rings that
     * have an ACTIVE ability only (see the {@code #simpleflightring:active_ability_rings}
     * tag, currently the sculk and miner rings): every level adds 25% to the damage of that
     * ring's special ability and widens its blast by the same factor
     * (see {@link RingAbilities#abilityDamageMultiplier}).
     */
    public static final ResourceKey<Enchantment> ENERGY_BURST =
            ResourceKey.create(Registries.ENCHANTMENT,
                    Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "energy_burst"));

    private ModEnchantments() {
    }
}

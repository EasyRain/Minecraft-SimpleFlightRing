package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, FlightRingMod.MODID);

    /** Crafting-table tier upgrade (wood -> stone -> iron -> gold -> diamond), preserves enchantments/name/lore. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RingUpgradeRecipe>> RING_UPGRADE =
            SERIALIZERS.register("ring_upgrade", () -> RingUpgradeRecipe.SERIALIZER);

    /** Smithing-table upgrade (diamond -> netherite), preserves enchantments/name/lore and resets durability. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SmithingTransformRecipe>> RING_SMITHING =
            SERIALIZERS.register("ring_smithing", () -> RingSmithingRecipe.SERIALIZER);

    /** Shapeless repair: tier material restores 25% durability per unit, stacked inputs repair multiple times at once. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RingRepairRecipe>> RING_REPAIR =
            SERIALIZERS.register("ring_repair", () -> RingRepairRecipe.SERIALIZER);

    /** Smithing: any flight ring + Indestructible Core -> the ring becomes indestructible (infinite flight). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SmithingTransformRecipe>> RING_INDESTRUCTIBLE =
            SERIALIZERS.register("ring_indestructible", () -> RingIndestructibleRecipe.SERIALIZER);

    /** Shapeless: Powered Flight Ring + gunpowder + redstone dust raises its built-in enchants by one (max 3). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RingEnchantUpgradeRecipe>> RING_ENCHANT_UPGRADE =
            SERIALIZERS.register("ring_enchant_upgrade", () -> RingEnchantUpgradeRecipe.SERIALIZER);

    /**
     * Shaped: 8 emeralds around an awakened damaged emerald ring -> the working emerald ring,
     * keeping the strength ({@code hero_level}) the damaged ring was charged with. A plain
     * vanilla shaped recipe cannot do that - it never copies a component from an ingredient -
     * and since the crafting preview is built by {@code assemble}, this also makes the preview
     * in the result slot show the ring at its real level instead of level I.
     */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<EmeraldForgeRecipe>> EMERALD_FORGE =
            SERIALIZERS.register("emerald_forge", () -> EmeraldForgeRecipe.SERIALIZER);

    /**
     * Shapeless: one raid ring + one totem of undying = the same ring with
     * {@link RaidCharges#PER_TOTEM} more totem charges. Has to copy the input ring, so that the
     * enchantments, the durability and the name all survive being charged.
     */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RaidChargeRecipe>> RAID_CHARGE =
            SERIALIZERS.register("raid_charge", () -> RaidChargeRecipe.SERIALIZER);

    /**
     * Note: the AllTheModium chain (indestructible ring + that mod's upgrade template + the
     * matching metal ingot) needs no serializer of its own - it is a plain vanilla
     * {@code minecraft:smithing_transform} recipe exactly like AllTheModium's own gear
     * upgrades, with the base written as a {@code neoforge:components} ingredient (26.1.2
     * spells its dispatch key {@code neoforge:ingredient_type}) so the ring must already be
     * indestructible and JEI shows the right ring.
     */

    private ModRecipeSerializers() {
    }
}

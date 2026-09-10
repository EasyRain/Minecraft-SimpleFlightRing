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

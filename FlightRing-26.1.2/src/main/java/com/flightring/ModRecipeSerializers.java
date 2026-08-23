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

    private ModRecipeSerializers() {
    }
}

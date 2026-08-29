package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * Shapeless repair recipe: one flight ring + 1 material unit = one craft.
 * Each craft restores 25% durability (netherite restores 100% with a single
 * ingot); repeat the craft to fully repair a ring. Consumes exactly one
 * material per craft - the standard vanilla per-slot consumption - so the
 * recipe works identically in every crafting system (crafting table, recipe
 * book, AE2 crafting terminal, ...). Enchantments, custom name and lore are
 * preserved by copying the input ring. A fully-durable ring cannot be used.
 */
public class RingRepairRecipe extends ShapelessRecipe {

    private final Ingredient material;
    private final ItemStack result;
    private final boolean fullRepair;

    public RingRepairRecipe(String group, CraftingBookCategory category, Ingredient material, ItemStack result) {
        super(group, category, result, NonNullList.of(Ingredient.EMPTY, material, Ingredient.of(result.getItem())));
        this.material = material;
        this.result = result;
        this.fullRepair = result.is(ModItems.NETHERITE_FLIGHT_RING.get());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack ring = ItemStack.EMPTY;
        boolean hasMaterial = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(this.result.getItem())) {
                if (!ring.isEmpty()) {
                    return false;
                }
                ring = stack;
            } else if (this.material.test(stack)) {
                hasMaterial = true;
            } else {
                return false;
            }
        }
        return !ring.isEmpty() && hasMaterial && ring.getDamageValue() > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack ring = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.is(this.result.getItem())) {
                ring = stack;
                break;
            }
        }
        ItemStack repaired = ring.copy();
        int repair = this.fullRepair ? ring.getMaxDamage() : ring.getMaxDamage() / 4;
        repaired.setDamageValue(Math.max(0, ring.getDamageValue() - repair));
        return repaired;
    }

    public Ingredient getMaterial() {
        return this.material;
    }

    public ItemStack getResult() {
        return this.result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.RING_REPAIR.get();
    }

    public static class Serializer implements RecipeSerializer<RingRepairRecipe> {

        public static final MapCodec<RingRepairRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                com.mojang.serialization.Codec.STRING.optionalFieldOf("group", "").forGetter(RingRepairRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(RingRepairRecipe::category),
                Ingredient.CODEC.fieldOf("material").forGetter(RingRepairRecipe::getMaterial),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RingRepairRecipe::getResult)
        ).apply(instance, RingRepairRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, RingRepairRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<RingRepairRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RingRepairRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static RingRepairRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            Ingredient material = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new RingRepairRecipe(group, category, material, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, RingRepairRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.material);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        }
    }
}

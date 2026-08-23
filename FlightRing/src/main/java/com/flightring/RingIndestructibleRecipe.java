package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.Level;

/**
 * Smithing-table recipe: any flight ring (base) + Indestructible Core
 * (addition) produces the same ring marked with the {@code indestructible}
 * data component - it never loses durability and grants infinite flight.
 * The template slot is not required (the custom codec has no template field
 * and {@link #matches} ignores it), so only the core + ring are consumed.
 * Enchantments, custom name, lore and current durability are all preserved.
 * <p>
 * Deliberately extends {@link SmithingTransformRecipe} so JEI's smithing
 * category shows it like any vanilla smithing transform recipe.
 */
public class RingIndestructibleRecipe extends SmithingTransformRecipe {

    private final Ingredient base;
    private final Ingredient addition;
    private final ItemStack result;

    public RingIndestructibleRecipe(Ingredient base, Ingredient addition, ItemStack result) {
        super(Ingredient.EMPTY, base, addition, result);
        this.base = base;
        this.addition = addition;
        this.result = result;
        // Example output shown in JEI / recipe preview: the ring with the marker.
        this.result.set(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE);
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return this.base.test(input.base()) && this.addition.test(input.addition());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        ItemStack assembled = input.base().copy();
        assembled.set(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE);
        return assembled;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.RING_INDESTRUCTIBLE.get();
    }

    public Ingredient getBase() {
        return base;
    }

    public Ingredient getAddition() {
        return addition;
    }

    public ItemStack getResult() {
        return result;
    }

    public static class Serializer implements RecipeSerializer<RingIndestructibleRecipe> {

        public static final MapCodec<RingIndestructibleRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC.fieldOf("base").forGetter(RingIndestructibleRecipe::getBase),
                Ingredient.CODEC.fieldOf("addition").forGetter(RingIndestructibleRecipe::getAddition),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RingIndestructibleRecipe::getResult)
        ).apply(instance, RingIndestructibleRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, RingIndestructibleRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<RingIndestructibleRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RingIndestructibleRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static RingIndestructibleRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            Ingredient base = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient addition = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new RingIndestructibleRecipe(base, addition, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, RingIndestructibleRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.base);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.addition);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        }
    }
}

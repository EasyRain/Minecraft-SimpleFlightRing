package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

/**
 * Smithing-table upgrade for the netherite ring: netherite upgrade smithing template
 * + diamond flight ring + netherite ingot. Enchantments, custom name and lore are
 * preserved (vanilla transmuteCopy behavior) and the ring starts at full durability.
 * <p>
 * This deliberately extends {@link SmithingTransformRecipe} instead of implementing
 * {@code SmithingRecipe} directly: JEI's smithing recipe category resolves its display
 * extension by matching the recipe's runtime class against registered extensions
 * ({@code isAssignableFrom}), so subclasses of {@code SmithingTransformRecipe} are shown
 * like any vanilla smithing transform recipe.
 */
public class RingSmithingRecipe extends SmithingTransformRecipe {

    // Shadow copies of the parent's package-private fields, kept so the codec below can
    // (de)serialize this recipe without needing access transformers. They hold the same
    // instances passed to the super constructor.
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final ItemStack result;

    public RingSmithingRecipe(Ingredient template, Ingredient base, Ingredient addition, ItemStack result) {
        super(template, base, addition, result);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        ItemStack assembled = super.assemble(input, registries);
        // Upgraded rings start with full durability (enchantments/name/lore carried over above).
        assembled.setDamageValue(0);
        return assembled;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.RING_SMITHING.get();
    }

    public Ingredient getTemplate() {
        return template;
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

    public static class Serializer implements RecipeSerializer<RingSmithingRecipe> {

        public static final MapCodec<RingSmithingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC.fieldOf("template").forGetter(RingSmithingRecipe::getTemplate),
                Ingredient.CODEC.fieldOf("base").forGetter(RingSmithingRecipe::getBase),
                Ingredient.CODEC.fieldOf("addition").forGetter(RingSmithingRecipe::getAddition),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RingSmithingRecipe::getResult)
        ).apply(instance, RingSmithingRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, RingSmithingRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<RingSmithingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RingSmithingRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static RingSmithingRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            Ingredient template = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient base = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient addition = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new RingSmithingRecipe(template, base, addition, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, RingSmithingRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.template);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.base);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.addition);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        }
    }
}

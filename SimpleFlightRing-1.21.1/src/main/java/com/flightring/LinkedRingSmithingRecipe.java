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
 * Smithing upgrade for the AllTheModium integration chain
 * (indestructible netherite ring -> Allthemodium -> Vibranium -> Unobtainium).
 * <p>
 * Mirrors AllTheModium's own equipment upgrades: the matching upgrade smithing template,
 * the previous ring as the base, and the matching metal ingot as the addition. The base
 * MUST already be an indestructible ring, so the chain can only be entered by forging a
 * netherite ring with the Indestructible Core first.
 * <p>
 * Enchantments, custom name and lore are carried over, and the result is always
 * indestructible (infinite flight, matching AllTheModium's unbreakable gear).
 * <p>
 * Deliberately extends {@link SmithingTransformRecipe} so JEI shows it like a vanilla
 * smithing transform recipe (same reason as {@link RingSmithingRecipe}).
 */
public class LinkedRingSmithingRecipe extends SmithingTransformRecipe {

    // Shadow copies of the parent's package-private fields for the codec below.
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final ItemStack result;

    public LinkedRingSmithingRecipe(Ingredient template, Ingredient base, Ingredient addition, ItemStack result) {
        super(template, base, addition, result);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        // Only a ring that is already infinite can be taken further up the chain.
        if (!input.base().has(ModDataComponents.INDESTRUCTIBLE.get())) {
            return false;
        }
        return this.template.test(input.template())
                && this.base.test(input.base())
                && this.addition.test(input.addition());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        ItemStack assembled = this.result.copy();
        // Carry the previous ring's enchantments, custom name and lore over.
        RingUpgradeHelper.copyComponents(input.base(), assembled);
        assembled.set(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE);
        assembled.setDamageValue(0);
        return assembled;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.LINKED_RING_SMITHING.get();
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

    public static class Serializer implements RecipeSerializer<LinkedRingSmithingRecipe> {

        public static final MapCodec<LinkedRingSmithingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC.fieldOf("template").forGetter(LinkedRingSmithingRecipe::getTemplate),
                Ingredient.CODEC.fieldOf("base").forGetter(LinkedRingSmithingRecipe::getBase),
                Ingredient.CODEC.fieldOf("addition").forGetter(LinkedRingSmithingRecipe::getAddition),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(LinkedRingSmithingRecipe::getResult)
        ).apply(instance, LinkedRingSmithingRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, LinkedRingSmithingRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<LinkedRingSmithingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, LinkedRingSmithingRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static LinkedRingSmithingRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            Ingredient template = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient base = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient addition = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new LinkedRingSmithingRecipe(template, base, addition, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, LinkedRingSmithingRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.template);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.base);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.addition);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        }
    }
}

package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.Level;

import java.util.Optional;

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

    public static final MapCodec<LinkedRingSmithingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
                    Ingredient.CODEC.fieldOf("template").forGetter(recipe -> recipe.template),
                    Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
                    Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
            ).apply(instance, LinkedRingSmithingRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkedRingSmithingRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC,
            recipe -> recipe.commonInfo,
            Ingredient.CONTENTS_STREAM_CODEC,
            recipe -> recipe.template,
            Ingredient.CONTENTS_STREAM_CODEC,
            recipe -> recipe.base,
            Ingredient.CONTENTS_STREAM_CODEC,
            recipe -> recipe.addition,
            ItemStackTemplate.STREAM_CODEC,
            recipe -> recipe.result,
            LinkedRingSmithingRecipe::new
    );

    // The codec produces LinkedRingSmithingRecipe instances; the serializer is exposed as the
    // parent type so getSerializer() can override SmithingTransformRecipe's signature.
    @SuppressWarnings("unchecked")
    public static final RecipeSerializer<SmithingTransformRecipe> SERIALIZER =
            (RecipeSerializer<SmithingTransformRecipe>) (RecipeSerializer<?>) new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    // Shadow copies of the parent's package-private fields, kept so the codec above can
    // (de)serialize this recipe without needing access transformers.
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final ItemStackTemplate result;

    public LinkedRingSmithingRecipe(Recipe.CommonInfo commonInfo, Ingredient template, Ingredient base,
                                    Ingredient addition, ItemStackTemplate result) {
        super(commonInfo, Optional.of(template), base, Optional.of(addition), result);
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
    public ItemStack assemble(SmithingRecipeInput input) {
        ItemStack assembled = this.result.create();
        // Carry the previous ring's enchantments, custom name and lore over.
        RingUpgradeHelper.copyComponents(input.base(), assembled);
        assembled.set(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE);
        assembled.setDamageValue(0);
        return assembled;
    }

    @Override
    public RecipeSerializer<SmithingTransformRecipe> getSerializer() {
        return SERIALIZER;
    }
}

package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

import java.util.Optional;

/**
 * Smithing-table upgrade for the netherite ring: netherite upgrade smithing template
 * + diamond flight ring + netherite ingot. Enchantments, custom name and lore are
 * preserved (transmute behavior) and the ring starts at full durability.
 * <p>
 * This deliberately extends {@link SmithingTransformRecipe} instead of implementing
 * {@code SmithingRecipe} directly: JEI's smithing recipe category resolves its display
 * extension by matching the recipe's runtime class against registered extensions
 * ({@code isAssignableFrom}), so subclasses of {@code SmithingTransformRecipe} are shown
 * like any vanilla smithing transform recipe.
 */
public class RingSmithingRecipe extends SmithingTransformRecipe {

    public static final MapCodec<RingSmithingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(ring -> ring.commonInfo),
                    Ingredient.CODEC.optionalFieldOf("template").forGetter(ring -> ring.template),
                    Ingredient.CODEC.fieldOf("base").forGetter(ring -> ring.base),
                    Ingredient.CODEC.optionalFieldOf("addition").forGetter(ring -> ring.addition),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(ring -> ring.result)
            ).apply(instance, RingSmithingRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RingSmithingRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC,
            ring -> ring.commonInfo,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
            ring -> ring.template,
            Ingredient.CONTENTS_STREAM_CODEC,
            ring -> ring.base,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
            ring -> ring.addition,
            ItemStackTemplate.STREAM_CODEC,
            ring -> ring.result,
            RingSmithingRecipe::new
    );

    // The codec produces RingSmithingRecipe instances; the serializer is exposed as the
    // parent type so getSerializer() can override SmithingTransformRecipe's signature.
    @SuppressWarnings("unchecked")
    public static final RecipeSerializer<SmithingTransformRecipe> SERIALIZER =
            (RecipeSerializer<SmithingTransformRecipe>) (RecipeSerializer<?>) new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    // Shadow copies of the parent's package-private fields, kept so the codec above can
    // (de)serialize this recipe without access transformers. They hold the same instances
    // passed to the super constructor.
    private final Optional<Ingredient> template;
    private final Ingredient base;
    private final Optional<Ingredient> addition;
    private final ItemStackTemplate result;

    public RingSmithingRecipe(Recipe.CommonInfo commonInfo, Optional<Ingredient> template, Ingredient base,
                              Optional<Ingredient> addition, ItemStackTemplate result) {
        super(commonInfo, template, base, addition, result);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        ItemStack assembled = super.assemble(input);
        // Upgraded rings start with full durability (enchantments/name/lore carried over above).
        assembled.setDamageValue(0);
        return assembled;
    }

    @Override
    public RecipeSerializer<SmithingTransformRecipe> getSerializer() {
        return SERIALIZER;
    }
}

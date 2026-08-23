package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Smithing-table recipe: the Indestructible Core is used as the smithing
 * TEMPLATE, any flight ring as the base; the addition slot stays empty.
 * The result is the same ring marked with the {@code indestructible} data
 * component - it never loses durability and grants infinite flight. Only the
 * core + ring are consumed. Enchantments, custom name, lore and the CURRENT
 * durability are all preserved (the result is assembled from the input stack).
 * <p>
 * Deliberately extends {@link SmithingTransformRecipe} so JEI's smithing
 * category shows it like any vanilla smithing transform recipe.
 */
public class RingIndestructibleRecipe extends SmithingTransformRecipe {

    public static final MapCodec<RingIndestructibleRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(ring -> ring.commonInfo),
                    Ingredient.CODEC.fieldOf("template").forGetter(ring -> ring.template),
                    Ingredient.CODEC.fieldOf("base").forGetter(ring -> ring.base),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(ring -> ring.result)
            ).apply(instance, RingIndestructibleRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RingIndestructibleRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC,
            ring -> ring.commonInfo,
            Ingredient.CONTENTS_STREAM_CODEC,
            ring -> ring.template,
            Ingredient.CONTENTS_STREAM_CODEC,
            ring -> ring.base,
            ItemStackTemplate.STREAM_CODEC,
            ring -> ring.result,
            RingIndestructibleRecipe::new
    );

    // The codec produces RingIndestructibleRecipe instances; the serializer is exposed as the
    // parent type so getSerializer() can override SmithingTransformRecipe's signature.
    @SuppressWarnings("unchecked")
    public static final RecipeSerializer<SmithingTransformRecipe> SERIALIZER =
            (RecipeSerializer<SmithingTransformRecipe>) (RecipeSerializer<?>) new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Ingredient template;
    private final Ingredient base;
    private final ItemStackTemplate result;

    public RingIndestructibleRecipe(Recipe.CommonInfo commonInfo, Ingredient template, Ingredient base,
                                    ItemStackTemplate result) {
        super(commonInfo, Optional.of(template), base, Optional.empty(), result);
        this.template = template;
        this.base = base;
        this.result = result;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return this.template.test(input.template()) && this.base.test(input.base());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        // Copy the input ring: enchantments, custom name, lore and the current
        // durability are all kept.
        ItemStack assembled = input.base().copy();
        assembled.set(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE);
        return assembled;
    }

    @Override
    public RecipeSerializer<SmithingTransformRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public List<RecipeDisplay> display() {
        // Example output shown in JEI / recipe preview: the ring with the marker.
        ItemStack example = this.result.create();
        example.set(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE);
        return List.of(
                new SmithingRecipeDisplay(
                        this.template.display(),
                        this.base.display(),
                        SlotDisplay.Empty.INSTANCE,
                        new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(example)),
                        new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
                )
        );
    }
}

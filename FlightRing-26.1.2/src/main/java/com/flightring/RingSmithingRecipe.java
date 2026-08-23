package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

import java.util.List;
import java.util.Optional;

/**
 * Smithing-table upgrade for the netherite ring: netherite upgrade smithing template
 * + diamond flight ring + netherite ingot. Enchantments, custom name and lore are
 * preserved (transmute behavior) and the ring starts at full durability.
 */
public class RingSmithingRecipe extends SimpleSmithingRecipe {

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

    public static final RecipeSerializer<RingSmithingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Optional<Ingredient> template;
    private final Ingredient base;
    private final Optional<Ingredient> addition;
    private final ItemStackTemplate result;

    public RingSmithingRecipe(Recipe.CommonInfo commonInfo, Optional<Ingredient> template, Ingredient base,
                              Optional<Ingredient> addition, ItemStackTemplate result) {
        super(commonInfo);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        ItemStack assembled = TransmuteRecipe.createWithOriginalComponents(this.result, input.base());
        // Upgraded rings start with full durability (enchantments/name/lore carried over above).
        assembled.setDamageValue(0);
        return assembled;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return this.template;
    }

    @Override
    public Ingredient baseIngredient() {
        return this.base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return this.addition;
    }

    @Override
    public RecipeSerializer<? extends SimpleSmithingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(List.of(this.template, Optional.of(this.base), this.addition));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
                new SmithingRecipeDisplay(
                        Ingredient.optionalIngredientToDisplay(this.template),
                        this.base.display(),
                        Ingredient.optionalIngredientToDisplay(this.addition),
                        new SlotDisplay.ItemStackSlotDisplay(this.result),
                        new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
                )
        );
    }
}
